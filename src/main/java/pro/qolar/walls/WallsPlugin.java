package pro.qolar.walls;

import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import pro.qolar.walls.arena.Arena;
import pro.qolar.walls.arena.SectorLayout;
import pro.qolar.walls.arena.VoidChunkGenerator;
import pro.qolar.walls.command.WallsCommand;
import pro.qolar.walls.game.GameManager;
import pro.qolar.walls.listener.GameListener;

/** Plugin entry point. */
public final class WallsPlugin extends JavaPlugin {

    private WallsConfig config;
    private Arena arena;
    private GameManager game;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            config = new WallsConfig(getConfig(), getLogger());
        } catch (IllegalArgumentException invalid) {
            getLogger().severe("config.yml is invalid: " + invalid.getMessage());
            getLogger().severe("Fix the configuration and restart; Walls will not enable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SectorLayout layout = SectorLayout.forTeams(config.teamCount(), config.baseOffset());
        arena = new Arena(this, config, layout);
        game = new GameManager(this, config, arena);

        try {
            World world = arena.ensureWorld();
            getLogger().info("Arena world '" + world.getName() + "' ready.");
        } catch (RuntimeException failed) {
            getLogger().severe("Could not prepare the arena world: " + failed.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new GameListener(game), this);

        PluginCommand command = getCommand("walls");
        if (command == null) {
            getLogger().severe("The 'walls' command is missing from plugin.yml; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(new WallsCommand(this, game));

        getLogger().info("Walls enabled. Run /walls reset to build the arena.");
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.shutdown();
        }
        if (arena != null) {
            arena.shutdown();
        }
    }

    /**
     * Lets the arena world be recreated from bukkit.yml or a world manager and
     * still come back empty.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidChunkGenerator();
    }

    public Arena arena() {
        return arena;
    }

    public GameManager game() {
        return game;
    }

    public WallsConfig settings() {
        return config;
    }
}
