#!/usr/bin/env bash
#
# Boot a throwaway Paper server with the Walls plugin installed.
#
#   ./run-test-server.sh                 # interactive, latest tested version
#   ./run-test-server.sh 1.20.6          # a specific Minecraft version
#   ./run-test-server.sh 1.21.4 --smoke  # scripted check, no keyboard needed
#   ./run-test-server.sh 1.21.4 --match  # full match driven by real bot players
#
# Everything lands in run/<version>/, which is gitignored. Delete it to start over.
#
# --smoke drives the plugin from the server console and then reports whether any
# exception showed up in the log. It is the check that proves the plugin really
# loads and builds an arena, rather than merely compiling.
#
# --match goes further: it connects real clients with mineflayer and plays a whole
# match, which is the only way to cover PvP, respawns, wool breaking and the win
# condition. Requires `npm install` in tools/.

set -euo pipefail

MC_VERSION="${1:-1.21.4}"
MODE="${2:-interactive}"

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$REPO_DIR/run/$MC_VERSION"
LOG="$RUN_DIR/logs/latest.log"
PIPE="$RUN_DIR/console.pipe"

# PaperMC's v2 API is sunset; v3 lives behind fill.papermc.io.
FILL_API="https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds"

log() { printf '\033[36m==>\033[0m %s\n' "$*"; }
die() { printf '\033[31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# --- build the plugin -------------------------------------------------------

PLUGIN_JAR="$(ls "$REPO_DIR"/target/walls-*.jar 2>/dev/null | head -1 || true)"
if [ -z "$PLUGIN_JAR" ]; then
  log "Building the plugin..."
  (cd "$REPO_DIR" && mvn -B -q package)
  PLUGIN_JAR="$(ls "$REPO_DIR"/target/walls-*.jar | head -1)"
fi
log "Plugin: $(basename "$PLUGIN_JAR")"

# --- fetch the server -------------------------------------------------------

mkdir -p "$RUN_DIR/plugins" "$RUN_DIR/logs"
PAPER_JAR="$RUN_DIR/paper.jar"

if [ ! -f "$PAPER_JAR" ]; then
  log "Looking up the latest Paper build for $MC_VERSION..."
  DOWNLOAD_URL="$(curl -fsSL --max-time 60 "$FILL_API" | python3 -c "
import json, sys
builds = json.load(sys.stdin)
stable = [b for b in builds if b.get('channel') == 'STABLE'] or builds
print(stable[-1]['downloads']['server:default']['url'])
")" || die "no Paper build found for $MC_VERSION"
  log "Downloading $(basename "$DOWNLOAD_URL")"
  curl -fsSL --max-time 600 -o "$PAPER_JAR" "$DOWNLOAD_URL" || die "download failed"
fi

# --- configure --------------------------------------------------------------

# Mojang's EULA. This is a disposable local test server; if you are running this
# yourself you are accepting https://aka.ms/MinecraftEULA by using this script.
echo "eula=true" > "$RUN_DIR/eula.txt"

cat > "$RUN_DIR/server.properties" <<'PROPS'
online-mode=false
level-name=world
generate-structures=false
spawn-protection=0
max-players=100
view-distance=6
simulation-distance=4
motd=Walls test server
sync-chunk-writes=false
enable-rcon=true
rcon.port=25575
rcon.password=wallstest
broadcast-rcon-to-ops=false
PROPS

cp -f "$PLUGIN_JAR" "$RUN_DIR/plugins/"
log "Server directory: $RUN_DIR"

# --- run --------------------------------------------------------------------

JAVA_ARGS=(-Xms1G -Xmx2G -XX:+UseG1GC)

if [ "$MODE" = "--match" ]; then
  command -v node >/dev/null || die "node is required for --match"
  [ -d "$REPO_DIR/tools/node_modules" ] || die "run 'npm install' in tools/ first"

  # A small arena with short phases keeps the run quick. damage-per-break equals
  # max-health so one break destroys a wool - the same code path, far less digging.
  mkdir -p "$RUN_DIR/plugins/Walls"
  cat > "$RUN_DIR/plugins/Walls/config.yml" <<'MATCHCFG'
arena:
  # The main world on purpose: bots joining land straight in the arena, and
  # mineflayer's client-side chunk cache does not survive a dimension change.
  world: world
  center-x: 0
  center-z: 0
  floor-y: 64
  thickness: 50
  blocks-per-tick: 20000
  glass:
    height: 40
    material: GLASS
walls:
  material: SANDSTONE
  thickness: 3
  fall-ticks: 20
bases:
  pedestal-material: STONE_BRICKS
center:
  chests: 8
  ring-radius: 3
caves:
  enabled: true
  seed: 20260830
  tunnels: 8
  tunnel-length: 90
  wall-margin: 3
  chests: 3
objective:
  max-health: 1000
  # One break destroys a wool: the same code path, far less digging.
  damage-per-break: 1000
game:
  countdown-seconds: 3
  respawn-seconds: 1
  end-seconds: 5
stats:
  enabled: true
modes:
  test:
    teams: 4
    shape: SQUARE
    radius: 25
    players-per-team: 4
    wall-height: 14
    vault-radius: 6
    base-offset: 15
    grace-seconds: 20
  duel:
    teams: 2
    shape: CIRCLE
    radius: 25
    players-per-team: 1
    wall-height: 12
    vault-radius: 6
    base-offset: 15
    grace-seconds: 20
default-mode: test
arenas:
  main:
    mode: test
    # The main world on purpose - see the note at the top of this config.
    world: world
  second:
    mode: duel
    world: walls_second
MATCHCFG

  rm -rf "$RUN_DIR/world" "$RUN_DIR/walls_arena"
  cd "$RUN_DIR"
  java "${JAVA_ARGS[@]}" -jar paper.jar nogui > "$RUN_DIR/console.out" 2>&1 &
  SERVER_PID=$!
  trap 'kill "$SERVER_PID" 2>/dev/null || true' EXIT

  log "Waiting for the server to start..."
  waited=0
  until grep -qF 'Done (' "$RUN_DIR/console.out" 2>/dev/null; do
    kill -0 "$SERVER_PID" 2>/dev/null || die "server exited during startup"
    [ "$waited" -lt 420 ] || die "server never finished starting"
    sleep 2; waited=$((waited + 2))
  done
  grep -qF 'Walls enabled' "$RUN_DIR/console.out" || die "the plugin did not enable"

  log "Running the match test"
  set +e
  WALLS_MC_VERSION="$MC_VERSION" WALLS_BASE_OFFSET=15 WALLS_FLOOR_Y=64 WALLS_BOTS=4 \
    node "$REPO_DIR/tools/match-test.js"
  MATCH_STATUS=$?
  set -e

  echo
  log "===== Errors and exceptions ====="
  if sed -e 's/\x1b\[[0-9;]*m//g' "$RUN_DIR/console.out" \
       | grep -nE 'Exception|SEVERE|at pro\.qolar|Caused by:' | head -30; then
    MATCH_STATUS=1
  else
    echo "    none"
  fi

  kill "$SERVER_PID" 2>/dev/null || true
  wait "$SERVER_PID" 2>/dev/null || true

  echo
  [ "$MATCH_STATUS" -eq 0 ] && log "MATCH TEST PASSED for Minecraft $MC_VERSION" \
    || die "match test failed for Minecraft $MC_VERSION"
  exit 0
fi

if [ "$MODE" != "--smoke" ]; then
  log "Starting Paper $MC_VERSION (Ctrl-C or 'stop' to quit)"
  cd "$RUN_DIR"
  exec java "${JAVA_ARGS[@]}" -jar paper.jar nogui
fi

# --- smoke mode -------------------------------------------------------------

rm -f "$PIPE"; mkfifo "$PIPE"
rm -f "$LOG"
# Start from an empty arena world so the build is genuinely exercised.
rm -rf "$RUN_DIR/walls_arena"
cd "$RUN_DIR"

java "${JAVA_ARGS[@]}" -jar paper.jar nogui < "$PIPE" > "$RUN_DIR/console.out" 2>&1 &
SERVER_PID=$!
exec 3> "$PIPE"

cleanup() {
  exec 3>&- 2>/dev/null || true
  kill "$SERVER_PID" 2>/dev/null || true
  wait "$SERVER_PID" 2>/dev/null || true
  rm -f "$PIPE"
}
trap cleanup EXIT

# Console output already written when the last command was sent. wait_for
# searches only past this point, so a matching line from an *earlier* command
# cannot satisfy a later wait - which would silently make the wait a no-op.
LAST_MARK=1

console() {
  LAST_MARK=$(( $(wc -l < "$RUN_DIR/console.out" 2>/dev/null || echo 0) + 1 ))
  printf '%s\n' "$1" >&3
}

# Wait for a pattern in the output produced since the last console command.
wait_for() {
  local pattern="$1" timeout="${2:-240}" waited=0
  while [ "$waited" -lt "$timeout" ]; do
    if tail -n "+$LAST_MARK" "$RUN_DIR/console.out" 2>/dev/null | grep -qF "$pattern"; then
      return 0
    fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      echo "server exited early while waiting for: $pattern" >&2
      return 1
    fi
    sleep 2
    waited=$((waited + 2))
  done
  echo "timed out after ${timeout}s waiting for: $pattern" >&2
  return 1
}

step() { log "$1"; }

step "Waiting for the server to finish starting..."
wait_for 'Done (' 420 || die "server never finished starting"

step "Plugin should be enabled"
wait_for 'Walls enabled' 30 || die "the plugin did not enable"

step "/walls status (before building)"
console "walls status"; sleep 3

step "/walls reset - building the arena"
console "walls reset"
wait_for 'Arena ready' 300 || die "the arena never finished building"

step "/walls status (after building)"
console "walls status"; sleep 3

# --- in-world geometry checks ----------------------------------------------
#
# Running the commands without an error proves little on its own. These ask the
# server what is actually at specific coordinates, so a wrongly shaped arena
# fails loudly instead of looking fine.

CHECKS=()

check_block() { # name x y z block
  CHECKS+=("$1")
  console "execute in minecraft:walls_arena if block $2 $3 $4 $5 run say WALLSCHECK $1"
}

assert_checks() {
  local missing=0 name
  for name in "${CHECKS[@]}"; do
    if grep -qF "WALLSCHECK $name" "$RUN_DIR/console.out"; then
      printf '    ok    %s\n' "$name"
    else
      printf '    FAIL  %s\n' "$name"
      missing=1
    fi
  done
  CHECKS=()
  [ "$missing" -eq 0 ] || die "in-world geometry checks failed"
}

step "Checking the arena is actually shaped correctly (walls up)"
check_block up/bedrock-floor      0  15   0 minecraft:bedrock
check_block up/stone-body        50  40  50 minecraft:stone
check_block up/dirt-under-grass  50  63  50 minecraft:dirt
check_block up/grass-surface     50  64  50 minecraft:grass_block
check_block up/glass-shell       61  70   0 minecraft:glass
check_block up/glass-ceiling      0 104   0 minecraft:glass
check_block up/wall-arm           0  70  20 minecraft:sandstone
check_block up/vault-ring         6  70   0 minecraft:sandstone
check_block up/vault-interior     0  70   0 minecraft:air
check_block up/loot-chest         0  65  -3 minecraft:chest
check_block up/base-pedestal     30  64  30 minecraft:stone_bricks
# The walls run the full depth of the platform; stopping them at the surface
# would let players simply tunnel under them into an enemy sector.
check_block up/wall-underground   0  40  20 minecraft:sandstone
check_block up/wall-deep          0  17  20 minecraft:sandstone
check_block up/bedrock-under-wall 0  15  20 minecraft:bedrock
sleep 5
assert_checks

step "/walls down - dropping the walls"
console "walls down"
wait_for 'walls have fallen' 120 || die "the walls never fell"
sleep 2

step "Checking the walls really came down and the vault opened"
check_block down/arm-cleared       0  70  20 minecraft:air
check_block down/vault-ring-gone   6  70   0 minecraft:air
check_block down/chest-survived    0  65  -3 minecraft:chest
check_block down/grass-untouched  50  64  50 minecraft:grass_block
check_block down/bedrock-untouched 0  15   0 minecraft:bedrock
# Underground the wall reverts to ordinary rock rather than air, or dropping the
# walls would leave a chasm the depth of the platform.
check_block down/wall-became-stone 0  40  20 minecraft:stone
check_block down/wall-became-grass 0  64  20 minecraft:grass_block
sleep 5
assert_checks

step "/walls up - raising them again"
console "walls up"
wait_for 'The walls are up' 120 || die "the walls never went back up"
sleep 2

step "Checking the walls went back"
check_block reraised/arm        0 70 20 minecraft:sandstone
check_block reraised/vault-ring 6 70  0 minecraft:sandstone
check_block reraised/underground 0 40 20 minecraft:sandstone
sleep 5
assert_checks

step "/walls start - with nobody online this must refuse cleanly"
console "walls start"; sleep 3
grep -qF 'Nobody is online to play' "$RUN_DIR/console.out" \
  || die "/walls start did not refuse cleanly with no players"

step "/walls status (final)"
console "walls status"; sleep 3

step "Caves should have been carved into the stone"
if sed -e 's/\x1b\[[0-9;]*m//g' "$RUN_DIR/console.out" \
     | grep -qE 'Caves: [1-9][0-9]{3,} carved'; then
  echo "    ok    caves carved"
  sed -e 's/\x1b\[[0-9;]*m//g' "$RUN_DIR/console.out" | grep 'Caves:' | tail -1 | sed 's/^/          /'
else
  die "no caves were carved into the platform"
fi

step "Switching to the 5-team circular mode and rebuilding"
console "walls mode large"; sleep 3
console "walls reset"
wait_for 'Arena ready' 300 || die "the circular 5-team arena never finished building"
console "walls status"; sleep 3

FRESH=$(tail -n "+$LAST_MARK" "$RUN_DIR/console.out" | sed -e 's/\x1b\[[0-9;]*m//g')
if printf '%s' "$FRESH" | grep -qE 'Aqua: .* wool '; then
  echo "    ok    a fifth team exists with its own colour"
else
  die "the 5-team mode did not produce five distinct teams"
fi
printf '%s' "$FRESH" | grep -E '(Red|Blue|Green|Yellow|Aqua): .* wool' | tail -5 | sed 's/^/          /'

# The square arena's corner (61,61) sits outside a circle of radius 80, so the
# rebuild has to clear it - otherwise the old arena's edges are left floating
# beyond the new glass shell.
check_block switched/old-corner-cleared 61 64 61 minecraft:air
check_block switched/new-platform-built 49 64 35 minecraft:grass_block
sleep 4
assert_checks

step "Stopping the server"
console "stop"
wait_for 'Stopping server' 60 || true
wait "$SERVER_PID" 2>/dev/null || true

# --- report -----------------------------------------------------------------

echo
log "===== Walls output ====="
sed -e 's/\x1b\[[0-9;]*m//g' "$RUN_DIR/console.out" \
  | grep -E 'Walls\]|Arena ready|Arena world|WALLSCHECK' | sed 's/^/    /' || true

echo
log "===== Errors and exceptions ====="
if sed -e 's/\x1b\[[0-9;]*m//g' "$RUN_DIR/console.out" \
     | grep -nE 'Exception|SEVERE|ERROR\]|at pro\.qolar|Caused by:' | head -40; then
  die "the log contains errors - see above"
else
  echo "    none"
fi

echo
log "SMOKE TEST PASSED for Minecraft $MC_VERSION"
