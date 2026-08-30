package pro.qolar.walls;

import org.bukkit.configuration.ConfigurationSection;
import pro.qolar.walls.arena.ArenaShape;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * One named game mode: how many teams, how big the map, how many players it is
 * meant to hold.
 *
 * <p>This is the part of the configuration that can change without restarting the
 * server. Everything else about an arena is fixed at startup; a mode swap
 * rebuilds the geometry and the map from these values.
 */
public final class ModeSettings {

    private final String name;
    private final int teams;
    private final ArenaShape shape;
    private final int radius;
    private final int playersPerTeam;
    private final int baseOffset;
    private final int wallHeight;
    private final int vaultRadius;
    private final int graceSeconds;

    public ModeSettings(String name, int teams, ArenaShape shape, int radius, int playersPerTeam,
                        int baseOffset, int wallHeight, int vaultRadius, int graceSeconds) {
        this.name = name;
        this.teams = teams;
        this.shape = shape;
        this.radius = radius;
        this.playersPerTeam = playersPerTeam;
        this.baseOffset = baseOffset;
        this.wallHeight = wallHeight;
        this.vaultRadius = vaultRadius;
        this.graceSeconds = graceSeconds;
    }

    /**
     * The modes shipped when config.yml has none: a 100-player headline mode, the
     * five-team 60-player mode, a quick small game, and a duel.
     */
    public static Map<String, ModeSettings> defaults() {
        Map<String, ModeSettings> modes = new LinkedHashMap<>();
        modes.put("mega", new ModeSettings("mega", 4, ArenaShape.SQUARE, 100, 25, 0, 16, 8, 300));
        modes.put("large", new ModeSettings("large", 5, ArenaShape.CIRCLE, 80, 12, 0, 15, 8, 240));
        modes.put("small", new ModeSettings("small", 4, ArenaShape.SQUARE, 60, 6, 0, 14, 6, 180));
        modes.put("duel", new ModeSettings("duel", 2, ArenaShape.CIRCLE, 30, 1, 0, 12, 6, 90));
        return modes;
    }

    /** Read one mode from a {@code modes.<name>} section. */
    public static ModeSettings read(String name, ConfigurationSection section, ModeSettings fallback,
                                    Logger log) {
        int teams = section.getInt("teams", fallback.teams());
        ArenaShape shape = readShape(section.getString("shape"), teams, log, name);
        return new ModeSettings(
                name,
                teams,
                shape,
                section.getInt("radius", fallback.radius()),
                Math.max(1, section.getInt("players-per-team", fallback.playersPerTeam())),
                Math.max(0, section.getInt("base-offset", fallback.baseOffset())),
                section.getInt("wall-height", fallback.wallHeight()),
                section.getInt("vault-radius", fallback.vaultRadius()),
                Math.max(1, section.getInt("grace-seconds", fallback.graceSeconds())));
    }

    private static ArenaShape readShape(String raw, int teams, Logger log, String modeName) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("auto")) {
            return ArenaShape.defaultFor(teams);
        }
        try {
            return ArenaShape.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            log.warning("Mode '" + modeName + "' has an unknown shape '" + raw + "'; using "
                    + ArenaShape.defaultFor(teams));
            return ArenaShape.defaultFor(teams);
        }
    }

    public String name() {
        return name;
    }

    public int teams() {
        return teams;
    }

    public ArenaShape shape() {
        return shape;
    }

    public int radius() {
        return radius;
    }

    public int playersPerTeam() {
        return playersPerTeam;
    }

    /** Zero means "work it out from the radius". */
    public int baseOffset() {
        return baseOffset;
    }

    public int wallHeight() {
        return wallHeight;
    }

    public int vaultRadius() {
        return vaultRadius;
    }

    public int graceSeconds() {
        return graceSeconds;
    }

    /** How many players this mode is built for. */
    public int capacity() {
        return teams * playersPerTeam;
    }

    @Override
    public String toString() {
        return name + " (" + teams + " teams, " + shape + " radius " + radius
                + ", up to " + capacity() + " players)";
    }
}
