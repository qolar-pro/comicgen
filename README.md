# Walls

The Walls minigame for Minecraft **1.20.x and 1.21.x** (Spigot, Paper, or any fork of them).

One square arena, split into four quadrants by a cross of four walls. Each team is sealed
in its own quadrant with a 1000-health wool block to defend. A loot vault sits at the centre,
closed until the walls fall. When they drop, everyone pours into the middle and fights.

**Last team standing wins.**

---

## The rules

| | |
|---|---|
| **Teams** | 4, one per quadrant |
| **Objective** | Each team has a wool block with 1000 health |
| **Respawns** | While your wool stands, your dead teammates come back |
| **No respawns** | Once your wool is destroyed, deaths are permanent |
| **Elimination** | A team is out when its wool is gone **and** every player is down |
| **Victory** | The last team still standing |

Breaking a wool block chips it for a fixed amount rather than mining it normally, so the cost
is the same for everyone regardless of the tool they hold. At the defaults that is
`1000 / 50 = 20 breaks`.

---

## The arena

Built from config, not from a shipped map file — change a number, run `/walls reset`, and the
arena is reshaped.

```
        +-------------------------------+   <- clear glass shell, wrapping the whole platform
        |             |   |             |
        |    Blue     |   |     Red     |
        |             |   |             |
        |-------------+-###-+-----------|   <- the four walls
        |               # C #           |      C = loot vault: sealed until the walls fall
        |-------------+-###-+-----------|
        |             |   |             |
        |   Yellow    |   |    Green    |
        |             |   |             |
        +-------------------------------+
```

**The platform** is 50 blocks deep by default, layered like real terrain so there is solid rock
to carve into later:

| Course | Depth |
|---|---|
| Grass | 1 |
| Dirt | 4 |
| Stone | 44 |
| Bedrock | 1 (the floor of the world) |

The whole thing is wrapped in a clear glass shell with a glass ceiling, so nobody leaves the map.

**The loot vault** at the centre is a sealed box. Its interior stays open air, so the chests
inside survive the walls coming down — they are revealed, not destroyed, the moment the walls fall.

---

## Commands

All under `/walls` (alias `/w`), permission `walls.admin` (op by default).

| Command | What it does |
|---|---|
| `/walls reset` | Build or rebuild the arena from config. Wipes player builds and dropped items. |
| `/walls start` | Start a match. Assigns teams, teleports everyone, begins the countdown. |
| `/walls up` | Raise the walls. |
| `/walls down` | Drop the walls. During a match this ends the grace period properly. |
| `/walls stop` | End the current match. |
| `/walls status` | Arena and team state. |
| `/walls join` | Join the waiting lobby. |
| `/walls tp` | Teleport to the arena. |

If nobody has used `/walls join`, `/walls start` simply enrolls everyone online.

### A match, start to finish

```
/walls reset     # build the arena (a few seconds)
/walls start     # countdown -> grace period, walls up
                 # ... teams gather and fortify ...
/walls down      # optional: cut the grace period short
                 # ... walls fall, fight, last team standing wins ...
                 # the arena rebuilds itself automatically
```

---

## Installing

Drop `walls-1.0.0.jar` into your server's `plugins/` folder and restart. The plugin creates its
own empty world (`walls_arena` by default) and generates the map into it — there is no map file
to install.

Then run `/walls reset` once to build the arena.

## Building

Requires JDK 17 or newer and Maven.

```bash
mvn package        # -> target/walls-1.0.0.jar
mvn test           # geometry and game-rule tests
```

The plugin compiles against the Spigot API for 1.20.1 and targets Java 17 bytecode. Compiling
against the *oldest* supported API is what lets one jar run across 1.20.x and 1.21.x on both
Spigot and Paper. For the same reason the code avoids enchantments, potion types, attributes and
Adventure components — each of those changed incompatibly somewhere in that version range.

## Testing on a real server

```bash
./run-test-server.sh                  # interactive Paper server, latest tested version
./run-test-server.sh 1.20.6           # a specific version
./run-test-server.sh 1.21.4 --smoke   # scripted check, no keyboard needed
```

The script downloads Paper, installs the plugin, and boots a throwaway server in `run/<version>/`
(gitignored). `--smoke` drives `reset`, `down`, `up`, `start` and `status` from the console and
reports any exception in the log.

Note that the script writes `eula=true` for the local test server, which means accepting
[Mojang's EULA](https://aka.ms/MinecraftEULA).

### What the smoke test does and does not cover

It proves the plugin loads, the world is created, the arena builds, and the wall commands run
clean on a real server. It cannot cover anything needing connected players — PvP, respawns,
objective chipping, and win detection are covered by unit tests and still want a manual pass:

1. `/walls reset`, then join with 2+ accounts and `/walls join`
2. `/walls start` — check everyone lands in their own quadrant and cannot reach each other
3. `/walls down` — check the vault opens and the chests are stocked
4. Break an enemy wool 20 times — check it dies and that team stops respawning
5. Kill the rest of that team — check they are eliminated and the last team wins

---

## Configuration

`plugins/Walls/config.yml`. Every value is documented in the file itself. The ones worth knowing:

| Key | Default | Notes |
|---|---|---|
| `arena.radius` | 60 | Half-width. 60 = a 121x121 map. Raise for 100-player games, lower for quick ones. |
| `arena.thickness` | 50 | Platform depth. Most of it is stone. |
| `arena.blocks-per-tick` | 20000 | Rebuild speed. Higher is faster but costs more per tick. |
| `walls.height` | 14 | How tall the walls stand. |
| `walls.vault-radius` | 6 | Size of the centre loot vault. |
| `objective.max-health` | 1000 | Wool health. |
| `objective.damage-per-break` | 50 | 1000 / 50 = 20 breaks to destroy. |
| `game.grace-seconds` | 180 | Time behind the walls before they fall. |

A full arena rebuild is roughly 1.3 million block positions. Those are placed across ticks rather
than all at once, which is why `/walls reset` takes a couple of seconds instead of freezing the
server.

---

## Not in this version

Named explicitly so nothing here is mistaken for done:

- **5+ teams.** The 60-player/5-team mode needs a radial "pie slice" layout. The `SectorLayout`
  seam it will plug into exists; the layout itself does not. Asking for `teams.count: 5` fails
  loudly at startup rather than building a broken arena.
- **Caves and ores.** The 50-block depth and the terrain layering exist so this can be added as a
  carving pass over the stone, but nothing is carved yet.
- **Kits, upgrades, spectator UI, stats, multiple arenas.**
