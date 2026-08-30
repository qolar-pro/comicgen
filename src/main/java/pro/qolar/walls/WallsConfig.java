package pro.qolar.walls;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import pro.qolar.walls.arena.ArenaGeometry;
import pro.qolar.walls.arena.ArenaShape;

import java.util.logging.Logger;

/**
 * Everything tunable, read once at startup.
 *
 * <p>Values are validated here rather than at the point of use, so a typo in
 * config.yml surfaces as a clear startup warning instead of a half-built arena.
 */
public final class WallsConfig {

    private final String worldName;
    private final int centerX;
    private final int centerZ;
    private final int radius;
    private final int floorY;
    private final int thickness;
    private final int baseOffset;
    private final int blocksPerTick;

    private final int glassHeight;
    private final Material glassMaterial;

    private final Material wallMaterial;
    private final int wallThickness;
    private final int wallHeight;
    private final int vaultRadius;
    private final int wallFallTicks;

    private final Material baseMaterial;
    private final int chestCount;
    private final int chestRingRadius;

    private final int teamCount;
    private final ArenaShape shape;
    private final int objectiveMaxHealth;
    private final int objectiveDamagePerBreak;

    private final int countdownSeconds;
    private final int graceSeconds;
    private final int respawnSeconds;
    private final int endSeconds;
    private final int minPlayersPerTeam;

    private final ArenaGeometry geometry;

    public WallsConfig(FileConfiguration config, Logger log) {
        this.worldName = config.getString("arena.world", "walls_arena");
        this.centerX = config.getInt("arena.center-x", 0);
        this.centerZ = config.getInt("arena.center-z", 0);
        this.radius = config.getInt("arena.radius", 60);
        this.floorY = config.getInt("arena.floor-y", 64);
        this.thickness = config.getInt("arena.thickness", 50);
        this.blocksPerTick = Math.max(256, config.getInt("arena.blocks-per-tick", 20000));

        this.glassHeight = config.getInt("arena.glass.height", 40);
        this.glassMaterial = material(config, "arena.glass.material", Material.GLASS, log);

        this.wallMaterial = material(config, "walls.material", Material.SANDSTONE, log);
        this.wallThickness = config.getInt("walls.thickness", 3);
        this.wallHeight = config.getInt("walls.height", 14);
        this.vaultRadius = config.getInt("walls.vault-radius", 6);
        this.wallFallTicks = Math.max(1, config.getInt("walls.fall-ticks", 40));

        this.baseMaterial = material(config, "bases.pedestal-material", Material.STONE_BRICKS, log);
        this.chestCount = Math.max(0, config.getInt("center.chests", 8));
        this.chestRingRadius = config.getInt("center.ring-radius", 3);

        this.teamCount = config.getInt("teams.count", 4);
        this.shape = readShape(config, teamCount, log);
        this.minPlayersPerTeam = Math.max(1, config.getInt("teams.min-players-per-team", 1));

        this.objectiveMaxHealth = Math.max(1, config.getInt("objective.max-health", 1000));
        this.objectiveDamagePerBreak = Math.max(1, config.getInt("objective.damage-per-break", 50));

        this.countdownSeconds = Math.max(1, config.getInt("game.countdown-seconds", 15));
        this.graceSeconds = Math.max(1, config.getInt("game.grace-seconds", 180));
        this.respawnSeconds = Math.max(0, config.getInt("game.respawn-seconds", 5));
        this.endSeconds = Math.max(1, config.getInt("game.end-seconds", 10));

        // Constructing the geometry validates the whole arena shape up front.
        this.geometry = new ArenaGeometry(shape, teamCount, radius, floorY, thickness,
                wallThickness, wallHeight, vaultRadius, glassHeight);

        int autoOffset = Math.max(vaultRadius + 4, radius / 2);
        int configured = config.getInt("arena.base-offset", 0);
        int offset = configured > 0 ? configured : autoOffset;
        if (offset <= vaultRadius + 1 || offset >= radius - 2) {
            log.warning("arena.base-offset " + offset + " does not leave room between the vault and the edge; using "
                    + autoOffset);
            offset = autoOffset;
        }
        this.baseOffset = offset;

        if (chestCount > 0 && (chestRingRadius < 1 || chestRingRadius >= vaultRadius)) {
            throw new IllegalArgumentException(
                    "center.ring-radius must be between 1 and walls.vault-radius - 1, got " + chestRingRadius);
        }
    }

    /**
     * Four teams get the classic square split by a cross; any other count gets a
     * circle, because a square cannot be divided into five equal sectors.
     */
    private static ArenaShape readShape(FileConfiguration config, int teamCount, Logger log) {
        String name = config.getString("arena.shape");
        if (name == null || name.isBlank() || name.equalsIgnoreCase("auto")) {
            return ArenaShape.defaultFor(teamCount);
        }
        try {
            return ArenaShape.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            log.warning("Unknown arena.shape '" + name + "'; using "
                    + ArenaShape.defaultFor(teamCount) + " for " + teamCount + " teams");
            return ArenaShape.defaultFor(teamCount);
        }
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

    public ArenaGeometry geometry() {
        return geometry;
    }

    public String worldName() {
        return worldName;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int baseOffset() {
        return baseOffset;
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

    public int teamCount() {
        return teamCount;
    }

    public ArenaShape shape() {
        return shape;
    }

    public int minPlayersPerTeam() {
        return minPlayersPerTeam;
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

    public int graceSeconds() {
        return graceSeconds;
    }

    public int respawnSeconds() {
        return respawnSeconds;
    }

    public int endSeconds() {
        return endSeconds;
    }
}
