#!/usr/bin/env node
'use strict';

/**
 * End-to-end match test for the Walls plugin.
 *
 * Console testing can prove the arena builds, but it cannot prove a match works,
 * because a match needs players. This connects real clients with mineflayer and
 * drives the server over RCON, then asserts on what `/walls status` reports -
 * the plugin's own view of the game, rather than a guess from log text.
 *
 * The run: teams get assigned, the walls come down, a player dies and respawns
 * while their wool stands, the wool is broken, the same player dies again and is
 * out for good, and the last team standing wins.
 */

const mineflayer = require('mineflayer');
const { Rcon } = require('rcon-client');
const { Vec3 } = require('vec3');

const HOST = process.env.WALLS_HOST || '127.0.0.1';
const PORT = Number(process.env.WALLS_PORT || 25565);
const RCON_PORT = Number(process.env.WALLS_RCON_PORT || 25575);
const RCON_PASSWORD = process.env.WALLS_RCON_PASSWORD || 'wallstest';
const VERSION = process.env.WALLS_MC_VERSION || '1.21.4';
const BASE_OFFSET = Number(process.env.WALLS_BASE_OFFSET || 20);
const FLOOR_Y = Number(process.env.WALLS_FLOOR_Y || 64);
const BOT_COUNT = Number(process.env.WALLS_BOTS || 4);

// Sector order must match SectorLayout.forTeams: clockwise from (+x,+z), paired
// with TeamColor.values().
const TEAM_SIGNS = { Red: [1, 1], Blue: [-1, 1], Green: [-1, -1], Yellow: [1, -1] };

let rcon;
const bots = [];
let failures = 0;

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const strip = (s) => s.replace(/§./g, '');

const log = (msg) => console.log(`    ${msg}`);
const step = (msg) => console.log(`\x1b[36m==>\x1b[0m ${msg}`);

function check(condition, description) {
  if (condition) {
    console.log(`    \x1b[32mok\x1b[0m    ${description}`);
  } else {
    console.log(`    \x1b[31mFAIL\x1b[0m  ${description}`);
    failures++;
  }
  return condition;
}

async function send(command) {
  return strip(await rcon.send(command));
}

/** Parse `/walls status` into something assertable. */
async function status() {
  const raw = await send('walls status');
  const out = { raw, state: null, wallsUp: null, built: null, teams: {} };

  const state = /State:\s*(\w+)/.exec(raw);
  if (state) out.state = state[1];

  const arena = /Arena:\s*(built|not built),\s*walls\s*(up|down)/.exec(raw);
  if (arena) {
    out.built = arena[1] === 'built';
    out.wallsUp = arena[2] === 'up';
  }

  const teamLine = /(\w+):\s*(\d+) players, wool (\d+)\/(\d+)(\s*\(out\))?/g;
  let m;
  while ((m = teamLine.exec(raw)) !== null) {
    out.teams[m[1]] = {
      players: Number(m[2]),
      wool: Number(m[3]),
      maxWool: Number(m[4]),
      out: Boolean(m[5]),
    };
  }
  return out;
}

/** Poll until predicate(status) holds, or give up and record a failure. */
async function waitFor(predicate, description, timeoutMs = 60000, intervalMs = 1000) {
  const deadline = Date.now() + timeoutMs;
  let last = null;
  while (Date.now() < deadline) {
    last = await status();
    if (predicate(last)) return last;
    await sleep(intervalMs);
  }
  console.log(`    \x1b[31mFAIL\x1b[0m  timed out waiting for ${description}`);
  if (last) console.log(last.raw.split('\n').map((l) => `        ${l}`).join('\n'));
  failures++;
  return last || { teams: {} };
}

function woolPosition(team) {
  const [sx, sz] = TEAM_SIGNS[team];
  return new Vec3(sx * BASE_OFFSET, FLOOR_Y + 2, sz * BASE_OFFSET);
}

async function spawnBot(index) {
  const username = `Bot${index + 1}`;
  const bot = mineflayer.createBot({
    host: HOST,
    port: PORT,
    username,
    version: VERSION,
    auth: 'offline',
  });
  bot.wallsTeam = null;

  // The plugin tells each player their team in chat; read it rather than assume
  // the assignment order.
  bot.on('message', (json) => {
    const match = /You are on (\w+)/.exec(strip(json.toString()));
    if (match) bot.wallsTeam = match[1];
  });
  bot.on('kicked', (reason) => log(`${username} kicked: ${reason}`));
  bot.on('error', (err) => log(`${username} error: ${err.message}`));

  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${username} never spawned`)), 60000);
    bot.once('spawn', () => { clearTimeout(timer); resolve(); });
    bot.once('error', (err) => { clearTimeout(timer); reject(err); });
  });
  return bot;
}

/**
 * Break a wool block by sending dig packets directly.
 *
 * The plugin cancels BlockBreakEvent and chips the wool itself, so the block
 * never turns to air. mineflayer's bot.dig() waits for that change and would
 * hang, so the raw start/finish packets are sent instead - the server still
 * fires BlockBreakEvent, which is all the plugin needs. The wool's health in
 * /walls status is the actual assertion.
 */
async function rawDig(bot, pos) {
  const location = { x: pos.x, y: pos.y, z: pos.z };
  // 0 = start destroying, 2 = finished destroying.
  bot._client.write('block_dig', { status: 0, location, face: 1, sequence: 0 });
  await sleep(1800);
  bot._client.write('block_dig', { status: 2, location, face: 1, sequence: 0 });
  await sleep(700);
}

async function breakWool(bot, team, attempts = 8) {
  const pos = woolPosition(team);
  await send(`tp ${bot.username} ${pos.x + 0.5} ${FLOOR_Y + 1} ${pos.z - 2.5}`);
  await sleep(2000);

  const block = bot.blockAt(pos);
  log(`${team} wool at (${pos.x},${pos.y},${pos.z}) reads as: ${block ? block.name : 'unknown'}`);

  for (let i = 0; i < attempts; i++) {
    const before = (await status()).teams[team];
    if (before && before.wool === 0) return true;

    try {
      await bot.lookAt(pos.offset(0.5, 0.5, 0.5), true);
    } catch (ignored) {
      // Looking is a courtesy; the dig packets carry the coordinates.
    }
    await rawDig(bot, pos);

    const after = (await status()).teams[team];
    if (after && after.wool === 0) return true;
    if (after && before && after.wool < before.wool) {
      log(`${team} wool chipped to ${after.wool}`);
    }
  }
  return false;
}

async function killTeam(team) {
  for (const bot of bots.filter((b) => b.wallsTeam === team)) {
    await send(`kill ${bot.username}`);
    await sleep(1200);
  }
}

async function main() {
  step(`Connecting to ${HOST}:${PORT} (Minecraft ${VERSION})`);
  rcon = await Rcon.connect({ host: HOST, port: RCON_PORT, password: RCON_PASSWORD });

  step('Two arenas should be configured, each in its own world');
  const arenaList = await send('walls arenas');
  check(/\bmain\b/.test(arenaList) && /\bsecond\b/.test(arenaList),
    'both arenas are registered');
  check(/world/.test(arenaList) && /walls_second/.test(arenaList),
    'each arena has its own world');
  arenaList.trim().split('\n').forEach((line) => log(line.trim()));

  step('Building the arena');
  await send('walls reset');
  await waitFor((s) => s.built, 'the arena to finish building', 180000);

  step(`Connecting ${BOT_COUNT} bots`);
  for (let i = 0; i < BOT_COUNT; i++) {
    bots.push(await spawnBot(i));
    await sleep(600);
  }
  check(bots.length === BOT_COUNT, `${BOT_COUNT} bots connected`);
  await sleep(2000);

  step('Starting the match');
  log((await send('walls start')).trim());
  await sleep(3000);

  check(bots.filter((b) => b.wallsTeam).length === BOT_COUNT, 'every bot was told its team');
  const teamsInPlay = [...new Set(bots.map((b) => b.wallsTeam).filter(Boolean))];
  check(teamsInPlay.length >= 2, `players split across ${teamsInPlay.length} teams`);
  log(`teams: ${bots.map((b) => `${b.username}=${b.wallsTeam}`).join(', ')}`);

  step('Waiting for the grace period');
  const grace = await waitFor((s) => s.state === 'Preparing', 'the grace period to begin', 90000);
  check(grace.wallsUp === true, 'the walls are up during the grace period');

  step('Dropping the walls');
  await send('walls down');
  const open = await waitFor((s) => s.state === 'Fighting', 'the fight to begin', 60000);
  check(open.state === 'Fighting', 'the match reached the fighting phase');

  // --- the rule the whole game is built around -----------------------------

  const attacker = bots.find((b) => b.wallsTeam);
  const myTeam = attacker.wallsTeam;
  const victims = teamsInPlay.filter((t) => t !== myTeam);
  log(`attacker ${attacker.username} (${myTeam}); eliminating ${victims.join(', ')}`);

  const firstVictim = victims[0];
  step(`A death with the wool intact should NOT eliminate ${firstVictim}`);
  await killTeam(firstVictim);
  await sleep(2000);
  const afterDeath = await status();
  check(afterDeath.teams[firstVictim] && !afterDeath.teams[firstVictim].out,
    `${firstVictim} survives a death while their wool stands`);
  check(afterDeath.teams[firstVictim] && afterDeath.teams[firstVictim].wool > 0,
    `${firstVictim}'s wool is still intact`);

  step(`Breaking ${firstVictim}'s wool`);
  const broke = await breakWool(attacker, firstVictim);
  check(broke, `${firstVictim}'s wool was destroyed by a player`);

  if (broke) {
    step(`A death with the wool gone SHOULD eliminate ${firstVictim}`);
    await killTeam(firstVictim);
    await sleep(2500);
    const afterSecond = await status();
    check(afterSecond.teams[firstVictim] && afterSecond.teams[firstVictim].out,
      `${firstVictim} is eliminated once their wool is gone and they die`);
  }

  step('Eliminating the remaining teams');
  for (const victim of victims.slice(1)) {
    await breakWool(attacker, victim);
    await killTeam(victim);
    await sleep(1500);
  }

  step('The last team standing should win');
  const ended = await waitFor((s) => s.state === 'Finished' || s.state === 'Idle',
    'the match to end', 90000);
  check(ended.state === 'Finished' || ended.state === 'Idle',
    `the match ended (state: ${ended.state})`);

  console.log();
  if (failures === 0) {
    console.log('\x1b[32mMATCH TEST PASSED\x1b[0m');
  } else {
    console.log(`\x1b[31mMATCH TEST FAILED: ${failures} check(s) failed\x1b[0m`);
  }
}

main()
  .catch((err) => {
    console.error(`\x1b[31mERROR:\x1b[0m ${err.stack || err}`);
    failures++;
  })
  .finally(async () => {
    for (const bot of bots) {
      try { bot.quit(); } catch (ignored) { /* shutting down */ }
    }
    if (rcon) {
      try { await rcon.end(); } catch (ignored) { /* shutting down */ }
    }
    setTimeout(() => process.exit(failures === 0 ? 0 : 1), 1500);
  });
