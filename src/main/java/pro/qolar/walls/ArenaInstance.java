package pro.qolar.walls;

import pro.qolar.walls.arena.Arena;
import pro.qolar.walls.arena.ArenaGeometry;
import pro.qolar.walls.arena.SectorLayout;
import pro.qolar.walls.game.GameManager;

/**
 * One playable arena: its world, its current mode, and the match running in it.
 *
 * <p>A server can host several. Each owns its world outright, which is what makes
 * routing events unambiguous - the world an event happened in identifies the
 * match it belongs to, with nothing to guess at.
 *
 * <p>Switching mode rebuilds the geometry and the game from scratch, because team
 * count and arena size are baked into both.
 */
public final class ArenaInstance {

    private final WallsPlugin plugin;
    private final WallsConfig config;
    private final String name;
    private final String worldName;

    private ModeSettings mode;
    private Arena arena;
    private GameManager game;

    public ArenaInstance(WallsPlugin plugin, WallsConfig config, String name, ModeSettings mode) {
        this.plugin = plugin;
        this.config = config;
        this.name = name;
        this.worldName = config.worldNameFor(name);
        this.mode = mode;
        assemble();
    }

    private void assemble() {
        ArenaGeometry geometry = config.geometryFor(mode);
        int baseOffset = config.baseOffsetFor(mode, geometry, plugin.getLogger());
        SectorLayout layout = SectorLayout.forGeometry(geometry, baseOffset);
        arena = new Arena(plugin, config, worldName, geometry, layout);
        game = new GameManager(plugin, config, arena, mode);
    }

    public String name() {
        return name;
    }

    public String worldName() {
        return worldName;
    }

    public ModeSettings mode() {
        return mode;
    }

    public Arena arena() {
        return arena;
    }

    public GameManager game() {
        return game;
    }

    /**
     * Switch to a different mode.
     *
     * <p>Refuses while a match is running: the team count and the map itself would
     * change underneath the players.
     *
     * @return an error message, or {@code null} on success
     */
    public String applyMode(ModeSettings newMode) {
        if (game.state().inProgress()) {
            return "A match is running in arena '" + name + "'. Stop it first.";
        }
        if (arena.isBusy()) {
            return "Arena '" + name + "' is still building.";
        }
        game.shutdown();
        arena.shutdown();
        this.mode = newMode;
        assemble();
        return null;
    }

    public void shutdown() {
        game.shutdown();
        arena.shutdown();
    }
}
