package pro.qolar.walls;

import org.bukkit.command.PluginCommand;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import pro.qolar.walls.arena.VoidChunkGenerator;
import pro.qolar.walls.command.WallsCommand;
import pro.qolar.walls.game.GameManager;
import pro.qolar.walls.game.SpectatorMenu;
import pro.qolar.walls.listener.GameListener;
import pro.qolar.walls.stats.StatsStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Plugin entry point. */
public final class WallsPlugin extends JavaPlugin {

    private WallsConfig settings;
    private ArenaRegistry arenas;
    private StatsStore stats;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            settings = new WallsConfig(getConfig(), getLogger());
        } catch (IllegalArgumentException invalid) {
            getLogger().severe("config.yml is invalid: " + invalid.getMessage());
            getLogger().severe("Fix the configuration and restart; Walls will not enable.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        stats = new StatsStore(this, settings.statsEnabled());
        arenas = new ArenaRegistry();

        try {
            for (Map.Entry<String, String> entry : settings.arenas().entrySet()) {
                ModeSettings mode = settings.mode(entry.getValue());
                ArenaInstance instance = new ArenaInstance(this, settings, entry.getKey(), mode);
                instance.arena().ensureWorld();
                arenas.add(instance);
                getLogger().info("Arena '" + instance.name() + "' ready in world '"
                        + instance.worldName() + "' - mode " + mode);
            }
        } catch (RuntimeException failed) {
            getLogger().severe("Could not prepare the arenas: " + failed.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new GameListener(arenas), this);

        List<GameManager> games = new ArrayList<>();
        arenas.all().forEach(instance -> games.add(instance.game()));
        getServer().getPluginManager().registerEvents(new SpectatorMenu(games), this);

        PluginCommand command = getCommand("walls");
        if (command == null) {
            getLogger().severe("The 'walls' command is missing from plugin.yml; disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(new WallsCommand(this, arenas));

        getLogger().info("Walls enabled with " + arenas.size() + " arena(s). Run /walls reset to build.");
    }

    @Override
    public void onDisable() {
        if (arenas != null) {
            for (ArenaInstance instance : arenas.all()) {
                instance.shutdown();
            }
        }
        if (stats != null) {
            stats.save();
        }
    }

    /**
     * Lets an arena world be recreated from bukkit.yml or a world manager and
     * still come back empty.
     */
    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new VoidChunkGenerator();
    }

    public ArenaRegistry arenas() {
        return arenas;
    }

    public WallsConfig settings() {
        return settings;
    }

    public StatsStore stats() {
        return stats;
    }
}
