# Walls

The Walls minigame for Minecraft **1.20.x and 1.21.x** (Spigot, Paper, or any fork of them).

An arena split into sectors by walls. Each team is sealed in its own sector with a 1000-health
wool block to defend. A loot vault sits at the centre, closed until the walls fall. When they
drop, everyone pours into the middle and fights.

**Last team standing wins.**

---

## The rules

| | |
|---|---|
| **Teams** | 2 to 8, one sector each |
| **Objective** | Each team has a wool block with 1000 health |
| **Respawns** | While your wool stands, your dead teammates come back |
| **No respawns** | Once your wool is destroyed, deaths are permanent |
| **Elimination** | A team is out when its wool is gone **and** every player is down |
| **Victory** | The last team still standing |

Breaking a wool block chips it for a fixed amount rather than mining it normally, so the cost is
the same for everyone regardless of the tool they hold. At the defaults that is
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
        |-------------+-###-+-----------|   <- the walls
        |               # C #           |      C = loot vault: sealed until the walls fall
        |-------------+-###-+-----------|
        |             |   |             |
        |   Yellow    |   |    Green    |
        |             |   |             |
        +-------------------------------+
```

**Shape.** Four teams get the classic square split by a cross. Any other count gets a **circular**
arena, because a square cannot be divided into five equal sectors without handing the corner teams
a bigger share of the map. Override with `shape` on a mode if you disagree.

**The platform** is 50 blocks deep by default, layered like real terrain:

| Course | Depth |
|---|---|
| Grass | 1 |
| Dirt | 4 |
| Stone | 44 |
| Bedrock | 1 (the floor of the world) |

**Caves and ore** are carved into that stone — tunnels to explore, ore banded by depth (coal and
copper shallow, diamond and emerald deep), and loot chests hidden down there. Carving never touches
a wall column or anything within `wall-margin` of one, so it cannot open a route between sectors.

**The walls run the full depth of the platform**, not just above ground. Surface-only walls are
pointless: a player simply digs down in their own base, tunnels sideways, and surfaces in an enemy
sector. Building is also capped three blocks below the wall top while they stand, so nobody can
pillar up and walk over instead.

When the walls fall, the buried section reverts to ordinary rock rather than air — clearing it
outright would leave a chasm the depth of the platform where the wall used to be.

**The loot vault** at the centre is a sealed box. Its interior stays open air, so the chests inside
survive the walls coming down — they are revealed, not destroyed, the moment the walls fall.

---

## Game modes

Named presets, switchable at runtime with `/walls mode <name>`.

| Mode | Teams | Shape | Radius | Built for |
|---|---|---|---|---|
| `mega` | 4 | square | 100 | 100 players |
| `large` | 5 | circle | 80 | 60 players |
| `small` | 4 | square | 60 | 24 players |
| `duel` | 2 | circle | 30 | 2 players |

Add your own under `modes:` in config.yml. A mode with many teams needs a larger `vault-radius`, or
the walls would merge into a solid disc at the centre; the plugin says so at startup with the number
to use rather than building something broken.

## Multiple arenas

Several arenas can run at once, each in its own world, listed under `arenas:`. Commands act on the
arena you are standing in, or take a trailing name (`/walls reset second`). `/walls arenas` lists
them.

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
| `/walls status` | Arena, cave and team state. |
| `/walls join` | Join the waiting lobby. |
| `/walls tp` | Teleport to the arena. |
| `/walls mode [name]` | Show or switch the game mode. |
| `/walls arenas` | List every arena. |
| `/walls watch` | Spectate a surviving player (for those already out). |
| `/walls stats [player]` | Match records, or the leaderboard. |

If nobody has used `/walls join`, `/walls start` simply enrols everyone online.

### A match, start to finish

```
/walls mode large   # optional: 5 teams, 60 players
/walls reset        # build the arena (a few seconds)
/walls start        # countdown -> grace period, walls up
                    # ... teams gather, mine and fortify ...
/walls down         # optional: cut the grace period short
                    # ... walls fall, fight, last team standing wins ...
                    # the arena rebuilds itself automatically
```

---

## Installing

Drop `walls-1.0.0.jar` into your server's `plugins/` folder and restart. The plugin creates its own
empty world and generates the map into it — there is no map file to install.

Then run `/walls reset` once to build the arena.

## Building

Requires JDK 17 or newer and Maven.

```bash
mvn package        # -> target/walls-1.0.0.jar
mvn test           # geometry, cave, mode and game-rule tests
```

The plugin compiles against the Spigot API for 1.20.1 and targets Java 17 bytecode. Compiling
against the *oldest* supported API is what lets one jar run across 1.20.x and 1.21.x on both Spigot
and Paper. For the same reason the code avoids enchantments, potion types, attributes and Adventure
components — each of those changed incompatibly somewhere in that version range.

## Testing

```bash
./run-test-server.sh                  # interactive Paper server
./run-test-server.sh 1.20.6           # a specific version
./run-test-server.sh 1.21.4 --smoke   # scripted arena check, no keyboard needed
./run-test-server.sh 1.21.4 --match   # a whole match played by real bot players
```

`--smoke` boots Paper, builds the arena from nothing, and then asks the server what is actually at
26 specific coordinates — before and after the walls drop, above ground and below — so a wrongly
shaped arena fails loudly instead of looking fine. It then switches to the five-team circular mode
live, rebuilds, and checks both that the new arena exists and that the old one's corners were
cleared away.

`--match` goes further. It connects real clients with [mineflayer](https://github.com/PrismarineJS/mineflayer)
and drives the server over RCON, covering what no unit test can: two arenas are registered in
separate worlds, teams are assigned, the walls come down, a player dies and respawns while their
wool stands, a player breaks the wool, that same player dies again and is out for good, and the
last team standing ends the match. Run `npm install` in `tools/` first.

Both are verified passing on **Paper 1.20.6** and **Paper 1.21.4** from the same jar.

The script writes `eula=true` for the local test server, which means accepting
[Mojang's EULA](https://aka.ms/MinecraftEULA).

---

## Configuration

`plugins/Walls/config.yml`. Every value is documented in the file itself. The ones worth knowing:

| Key | Default | Notes |
|---|---|---|
| `modes.<name>.teams` | 2–5 | Any count from 2 up. |
| `modes.<name>.radius` | varies | Half-width. 60 = a 121x121 map. |
| `modes.<name>.shape` | auto | `SQUARE`, `CIRCLE`, or auto by team count. |
| `arena.thickness` | 50 | Platform depth. Most of it is stone for caves. |
| `arena.blocks-per-tick` | 20000 | Rebuild speed. Higher is faster but costs more per tick. |
| `caves.enabled` | true | Caves and ore in the stone. |
| `caves.seed` | 0 | 0 picks a fresh seed each rebuild; set a number to repeat a map. |
| `objective.max-health` | 1000 | Wool health. |
| `objective.damage-per-break` | 50 | 1000 / 50 = 20 breaks to destroy. |
| `stats.enabled` | true | Per-player records in `stats.yml`. |

A full rebuild of the default arena is roughly 1.3 million block positions. Those are placed across
ticks rather than all at once, which is why `/walls reset` takes a couple of seconds instead of
freezing the server.

---

## Not in this version

- **Kits / starting gear.** Players spawn empty-handed and mine for what they need.
- **Reconnect grace.** A player who disconnects mid-match is removed from their team and cannot
  rejoin that match.
- **Placed-block tracking.** A reset clears everything above the floor rather than only what players
  built, which is simpler and just as thorough, but means the arena is rebuilt wholesale each time.
