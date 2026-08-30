package pro.qolar.walls;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pro.qolar.walls.arena.ArenaGeometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Everything tunable, read once at startup.
 *
 * <p>Split in two on purpose. What lives here is fixed for the life of the
 * server: materials, timings, how deep the platform is. What varies per match -
 * team count, arena size, shape - lives in {@link ModeSettings}, so switching
 * mode can rebuild an arena without a restart.
 *
 * <p>Values are validated here rather than at the point of use, so a typo in
 * config.yml surfaces as a clear startup warning instead of a half-built arena.
 */
public final class WallsConfig {

    private final String worldPrefix;
    private final int centerX;
    private final int centerZ;
    private final int floorY;
    private final int thickness;
    private final int blocksPerTick;

    private final int glassHeight;
    private final Material glassMaterial;

    private final Material wallMaterial;
    private final int wallThickness;
    private final int wallFallTicks;

    private final Material baseMaterial;
    private final int chestCount;
    private final int chestRingRadius;

    private final boolean cavesEnabled;
    private final long caveSeed;
    private final int caveWorms;
    private final int caveWormLength;
    private final int caveWallMargin;
    private final int caveChests;

    private final int objectiveMaxHealth;
    private final int objectiveDamagePerBreak;

    private final int countdownSeconds;
    private final int respawnSeconds;
    private final int endSeconds;

    private final boolean statsEnabled;

    private final Map<String, ModeSettings> modes;
    private final String defaultMode;
    private final Map<String, String> arenas;

    public WallsConfig(FileConfiguration config, Logger log) {
        this.worldPrefix = config.getString("arena.world", "walls_arena");
        this.centerX = config.getInt("arena.center-x", 0);
        this.centerZ = config.getInt("arena.center-z", 0);
        this.floorY = config.getInt("arena.floor-y", 64);
        this.thickness = config.getInt("arena.thickness", 50);
        this.blocksPerTick = Math.max(256, config.getInt("arena.blocks-per-tick", 20000));

        this.glassHeight = config.getInt("arena.glass.height", 40);
        this.glassMaterial = material(config, "arena.glass.material", Material.GLASS, log);

        this.wallMaterial = material(config, "walls.material", Material.SANDSTONE, log);
        this.wallThickness = config.getInt("walls.thickness", 3);
        this.wallFallTicks = Math.max(1, config.getInt("walls.fall-ticks", 40));

        this.baseMaterial = material(config, "bases.pedestal-material", Material.STONE_BRICKS, log);
        this.chestCount = Math.max(0, config.getInt("center.chests", 8));
        this.chestRingRadius = config.getInt("center.ring-radius", 3);

        this.cavesEnabled = config.getBoolean("caves.enabled", true);
        this.caveSeed = config.getLong("caves.seed", 0L);
        this.caveWorms = Math.max(0, config.getInt("caves.tunnels", 26));
        this.caveWormLength = Math.max(1, config.getInt("caves.tunnel-length", 160));
        this.caveWallMargin = Math.max(1, config.getInt("caves.wall-margin", 3));
        this.caveChests = Math.max(0, config.getInt("caves.chests", 6));

        this.objectiveMaxHealth = Math.max(1, config.getInt("objective.max-health", 1000));
        this.objectiveDamagePerBreak = Math.max(1, config.getInt("objective.damage-per-break", 50));

        this.countdownSeconds = Math.max(1, config.getInt("game.countdown-seconds", 15));
        this.respawnSeconds = Math.max(0, config.getInt("game.respawn-seconds", 5));
        this.endSeconds = Math.max(1, config.getInt("game.end-seconds", 10));

        this.statsEnabled = config.getBoolean("stats.enabled", true);

        this.modes = readModes(config, log);
        this.defaultMode = readDefaultMode(config, modes, log);
        this.arenas = readArenas(config, modes, defaultMode, log);
    }

    private static Map<String, ModeSettings> readModes(FileConfiguration config, Logger log) {
        Map<String, ModeSettings> defaults = ModeSettings.defaults();
        ConfigurationSection section = config.getConfigurationSection("modes");
        if (section == null) {
            return Collections.unmodifiableMap(defaults);
        }
        Map<String, ModeSettings> found = new LinkedHashMap<>();
        ModeSettings fallback = defaults.get("small");
        for (String key : section.getKeys(false)) {
            ConfigurationSection mode = section.getConfigurationSection(key);
            if (mode == null) {
                continue;
            }
            String name = key.toLowerCase(Locale.ROOT);
            found.put(name, ModeSettings.read(name, mode, defaults.getOrDefault(name, fallback), log));
        }
        if (found.isEmpty()) {
            log.warning("No usable entries under 'modes'; falling back to the built-in modes.");
            return Collections.unmodifiableMap(defaults);
        }
        return Collections.unmodifiableMap(found);
    }

    private static String readDefaultMode(FileConfiguration config, Map<String, ModeSettings> modes,
                                          Logger log) {
        String configured = config.getString("default-mode", "small");
        String name = configured == null ? "" : configured.toLowerCase(Locale.ROOT);
        if (modes.containsKey(name)) {
            return name;
        }
        String first = modes.keySet().iterator().next();
        log.warning("default-mode '" + configured + "' is not a defined mode; using '" + first + "'");
        return first;
    }

    private static Map<String, String> readArenas(FileConfiguration config, Map<String, ModeSettings> modes,
                                                  String defaultMode, Logger log) {
        Map<String, String> found = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String name = key.toLowerCase(Locale.ROOT);
                String mode = section.getString(key + ".mode", defaultMode);
                if (mode == null || !modes.containsKey(mode.toLowerCase(Locale.ROOT))) {
                    log.warning("Arena '" + name + "' asks for unknown mode '" + mode
                            + "'; using '" + defaultMode + "'");
                    mode = defaultMode;
                }
                found.put(name, mode.toLowerCase(Locale.ROOT));
            }
        }
        if (found.isEmpty()) {
            found.put("main", defaultMode);
        }
        return Collections.unmodifiableMap(found);
    }

    private static Material material(FileConfiguration config, String path, Material fallback, Logger log) {
        String name = config.getString(path);
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material matched = Material.matchMaterial(name);
        if (matched == null || !matched.isBlock()) {
            log.warning("Unknown block material '" + name + "' at " + path
                    + " (not valid on this server version); falling back to " + fallback);
            return fallback;
        }
        return matched;
    }

    // --- geometry --------------------------------------------------------

    /**
     * Build the geometry for a mode.
     *
     * <p>The glass shell is raised if a mode's walls would otherwise reach it,
     * rather than refusing to build: a mode asking for tall walls should get a
     * taller box, not an error.
     */
    public ArenaGeometry geometryFor(ModeSettings mode) {
        int glass = Math.max(glassHeight, mode.wallHeight() + 10);
        return new ArenaGeometry(mode.shape(), mode.teams(), mode.radius(), floorY, thickness,
                wallThickness, mode.wallHeight(), mode.vaultRadius(), glass);
    }

    /** Where bases sit for a mode: its own setting, or a sensible spot if unset. */
    public int baseOffsetFor(ModeSettings mode, ArenaGeometry geometry, Logger log) {
        int automatic = Math.max(geometry.vaultRadius() + 4, geometry.radius() / 2);
        int configured = mode.baseOffset();
        if (configured <= 0) {
            return automatic;
        }
        if (configured <= geometry.vaultRadius() + 1 || configured >= geometry.radius() - 1) {
            log.warning("Mode '" + mode.name() + "' base-offset " + configured
                    + " does not fit between the vault and the edge; using " + automatic);
            return automatic;
        }
        return configured;
    }

    /** The world a named arena lives in. */
    public String worldNameFor(String arenaName) {
        // A single arena keeps the plain world name, so existing setups are undisturbed.
        return arenas.size() == 1 ? worldPrefix : worldPrefix + "_" + arenaName;
    }

    // --- accessors -------------------------------------------------------

    public Map<String, ModeSettings> modes() {
        return modes;
    }

    public ModeSettings mode(String name) {
        return modes.get(name == null ? null : name.toLowerCase(Locale.ROOT));
    }

    public String defaultMode() {
        return defaultMode;
    }

    public Map<String, String> arenas() {
        return arenas;
    }

    /** Modes ordered from smallest capacity upward - used to auto-pick one. */
    public List<ModeSettings> modesByCapacity() {
        List<ModeSettings> sorted = new ArrayList<>(modes.values());
        sorted.sort((a, b) -> Integer.compare(a.capacity(), b.capacity()));
        return sorted;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int blocksPerTick() {
        return blocksPerTick;
    }

    public Material glassMaterial() {
        return glassMaterial;
    }

    public Material wallMaterial() {
        return wallMaterial;
    }

    public Material baseMaterial() {
        return baseMaterial;
    }

    public int wallFallTicks() {
        return wallFallTicks;
    }

    public int chestCount() {
        return chestCount;
    }

    public int chestRingRadius() {
        return chestRingRadius;
    }

    public boolean cavesEnabled() {
        return cavesEnabled;
    }

    /** Zero means "pick a fresh one each rebuild". */
    public long caveSeed() {
        return caveSeed;
    }

    public int caveWorms() {
        return caveWorms;
    }

    public int caveWormLength() {
        return caveWormLength;
    }

    public int caveWallMargin() {
        return caveWallMargin;
    }

    public int caveChests() {
        return caveChests;
    }

    public int objectiveMaxHealth() {
        return objectiveMaxHealth;
    }

    public int objectiveDamagePerBreak() {
        return objectiveDamagePerBreak;
    }

    public int countdownSeconds() {
        return countdownSeconds;
    }

    public int respawnSeconds() {
        return respawnSeconds;
    }

    public int endSeconds() {
        return endSeconds;
    }

    public boolean statsEnabled() {
        return statsEnabled;
    }
}
