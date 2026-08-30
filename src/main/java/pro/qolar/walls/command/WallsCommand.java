package pro.qolar.walls.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pro.qolar.walls.ArenaInstance;
import pro.qolar.walls.ArenaRegistry;
import pro.qolar.walls.ModeSettings;
import pro.qolar.walls.WallsPlugin;
import pro.qolar.walls.arena.Arena;
import pro.qolar.walls.game.GameManager;
import pro.qolar.walls.game.SpectatorMenu;
import pro.qolar.walls.stats.StatsStore;
import pro.qolar.walls.util.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /walls} - the admin surface.
 *
 * <p>The four commands the minigame is driven by are {@code up}, {@code down},
 * {@code reset} and {@code start}. The rest exist because without them the plugin
 * cannot be run on a live server.
 *
 * <p>Commands act on the arena the sender is standing in, or the first one
 * configured. Where several arenas exist, a trailing name picks one.
 */
public final class WallsCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of(
            "up", "down", "reset", "start", "stop", "status", "join", "tp",
            "mode", "arenas", "watch", "stats", "help");

    private final WallsPlugin plugin;
    private final ArenaRegistry arenas;

    public WallsCommand(WallsPlugin plugin, ArenaRegistry arenas) {
        this.plugin = plugin;
        this.arenas = arenas;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        // These do not act on one arena.
        switch (sub) {
            case "help" -> {
                help(sender, label);
                return true;
            }
            case "arenas" -> {
                listArenas(sender);
                return true;
            }
            case "stats" -> {
                stats(sender, args);
                return true;
            }
            default -> {
                // fall through to arena-scoped handling
            }
        }

        ArenaInstance instance = resolve(sender, args, sub.equals("mode") ? 2 : 1);
        if (instance == null) {
            Msg.send(sender, "&cNo such arena. Try &f/" + label + " arenas");
            return true;
        }

        switch (sub) {
            case "up" -> up(sender, instance);
            case "down" -> down(sender, instance);
            case "reset" -> reset(sender, instance);
            case "start" -> start(sender, instance);
            case "stop" -> stop(sender, instance);
            case "status" -> status(sender, instance);
            case "join" -> join(sender, instance);
            case "tp" -> teleport(sender, instance);
            case "mode" -> mode(sender, instance, args);
            case "watch" -> watch(sender, instance);
            default -> Msg.send(sender, "&cUnknown subcommand &f" + args[0] + "&c. Try &f/" + label + " help");
        }
        return true;
    }

    /**
     * Work out which arena a command means: an explicit name, else the one the
     * sender is standing in, else the only (or first) one.
     */
    private ArenaInstance resolve(CommandSender sender, String[] args, int nameIndex) {
        if (args.length > nameIndex) {
            ArenaInstance named = arenas.byName(args[nameIndex]);
            if (named != null) {
                return named;
            }
            // Not a known arena name: fall through rather than fail, since the
            // argument may belong to the subcommand itself.
        }
        if (sender instanceof Player player) {
            ArenaInstance here = arenas.byWorld(player.getWorld().getName());
            if (here != null) {
                return here;
            }
        }
        return arenas.first();
    }

    private void up(CommandSender sender, ArenaInstance instance) {
        Arena arena = instance.arena();
        if (!requireBuilt(sender, instance)) {
            return;
        }
        if (arena.wallsUp()) {
            Msg.send(sender, "&eThe walls are already up.");
            return;
        }
        try {
            arena.raiseWalls(message -> Msg.broadcast("&7" + message), () -> {
            });
            Msg.send(sender, "&aRaising the walls...");
        } catch (IllegalStateException busy) {
            Msg.send(sender, "&c" + busy.getMessage());
        }
    }

    private void down(CommandSender sender, ArenaInstance instance) {
        Arena arena = instance.arena();
        if (!requireBuilt(sender, instance)) {
            return;
        }
        // Mid-match this ends the grace period properly rather than just clearing blocks.
        if (instance.game().forceOpen()) {
            Msg.send(sender, "&aGrace period cut short - the walls are coming down.");
            return;
        }
        if (!arena.wallsUp()) {
            Msg.send(sender, "&eThe walls are already down.");
            return;
        }
        try {
            arena.dropWalls(message -> Msg.broadcast("&7" + message), () -> {
            });
            Msg.send(sender, "&aDropping the walls...");
        } catch (IllegalStateException busy) {
            Msg.send(sender, "&c" + busy.getMessage());
        }
    }

    private void reset(CommandSender sender, ArenaInstance instance) {
        Arena arena = instance.arena();
        GameManager game = instance.game();
        if (arena.isBusy()) {
            Msg.send(sender, "&cThe arena is already being rebuilt.");
            return;
        }
        game.stop("arena reset");
        try {
            arena.rebuild(message -> Msg.send(sender, "&7" + message), () -> {
                for (int i = 0; i < game.teams().size(); i++) {
                    arena.clearObjective(i);
                }
                Msg.send(sender, "&aArena '" + instance.name() + "' reset. Run &f/walls start&a when ready.");
            });
        } catch (IllegalStateException busy) {
            Msg.send(sender, "&c" + busy.getMessage());
        }
    }

    private void start(CommandSender sender, ArenaInstance instance) {
        String error = instance.game().start(sender);
        if (error != null) {
            Msg.send(sender, "&c" + error);
            return;
        }
        Msg.send(sender, "&aMatch started in arena '" + instance.name() + "'.");
    }

    private void stop(CommandSender sender, ArenaInstance instance) {
        if (!instance.game().state().inProgress()) {
            Msg.send(sender, "&eNo match is running.");
            return;
        }
        instance.game().stop("stopped by " + sender.getName());
        Msg.send(sender, "&aMatch stopped.");
    }

    private void status(CommandSender sender, ArenaInstance instance) {
        Msg.send(sender, "&bArena '" + instance.name() + "' &7(mode &f"
                + instance.mode().name() + "&7)");
        for (String line : instance.game().statusLines()) {
            sender.sendMessage(Msg.color(line));
        }
    }

    private void join(CommandSender sender, ArenaInstance instance) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&cOnly a player can join.");
            return;
        }
        if (!requireBuilt(sender, instance)) {
            return;
        }
        if (instance.game().join(player)) {
            Msg.send(sender, "&aJoined '" + instance.name() + "'. &7Waiting players: &f"
                    + instance.game().lobbySize());
        } else {
            Msg.send(sender, "&cA match is already running there.");
        }
    }

    private void teleport(CommandSender sender, ArenaInstance instance) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&cOnly a player can teleport.");
            return;
        }
        if (!requireBuilt(sender, instance)) {
            return;
        }
        player.teleport(instance.arena().world().getSpawnLocation());
        Msg.send(sender, "&aTeleported to arena '" + instance.name() + "'.");
    }

    private void mode(CommandSender sender, ArenaInstance instance, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "&7Arena '" + instance.name() + "' is on mode &f" + instance.mode().name());
            Msg.send(sender, "&7Available modes:");
            for (ModeSettings mode : plugin.settings().modesByCapacity()) {
                sender.sendMessage(Msg.color("  &f" + mode.name() + " &7- " + mode.teams() + " teams, "
                        + mode.shape().name().toLowerCase(Locale.ROOT) + " radius " + mode.radius()
                        + ", up to &f" + mode.capacity() + "&7 players"));
            }
            return;
        }
        ModeSettings mode = plugin.settings().mode(args[1]);
        if (mode == null) {
            Msg.send(sender, "&cNo mode called &f" + args[1] + "&c. Try &f/walls mode&c to list them.");
            return;
        }
        String error = instance.applyMode(mode);
        if (error != null) {
            Msg.send(sender, "&c" + error);
            return;
        }
        arenas.reindex();
        instance.arena().ensureWorld();
        Msg.send(sender, "&aArena '" + instance.name() + "' switched to &f" + mode
                + "&a. Run &f/walls reset&a to build it.");
    }

    private void watch(CommandSender sender, ArenaInstance instance) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&cOnly a player can watch.");
            return;
        }
        SpectatorMenu.open(player, instance.game());
    }

    private void listArenas(CommandSender sender) {
        Msg.send(sender, "&bArenas");
        for (ArenaInstance instance : arenas.all()) {
            sender.sendMessage(Msg.color("  &f" + instance.name() + " &7- world &f" + instance.worldName()
                    + "&7, mode &f" + instance.mode().name()
                    + "&7, " + instance.game().state().displayName().toLowerCase(Locale.ROOT)));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        StatsStore store = plugin.stats();
        if (!store.enabled()) {
            Msg.send(sender, "&cStats are disabled in config.yml.");
            return;
        }
        if (args.length > 1) {
            StatsStore.Record record = store.byName(args[1]);
            if (record == null) {
                Msg.send(sender, "&7No record for &f" + args[1] + "&7 yet.");
                return;
            }
            showRecord(sender, args[1], record);
            return;
        }
        if (sender instanceof Player player) {
            StatsStore.Record own = store.of(player.getUniqueId());
            if (own != null) {
                showRecord(sender, player.getName(), own);
                return;
            }
        }
        Msg.send(sender, "&bTop players");
        int rank = 1;
        for (Map.Entry<UUID, StatsStore.Record> entry : store.top(10)) {
            StatsStore.Record record = entry.getValue();
            sender.sendMessage(Msg.color("  &7" + rank++ + ". &f" + record.name()
                    + " &7- &f" + record.wins() + "&7 wins, &f" + record.kills() + "&7 kills"));
        }
    }

    private void showRecord(CommandSender sender, String name, StatsStore.Record record) {
        Msg.send(sender, "&bStats for &f" + name);
        sender.sendMessage(Msg.color("  &7Matches: &f" + record.matches()
                + " &7Wins: &f" + record.wins()));
        sender.sendMessage(Msg.color("  &7Kills: &f" + record.kills()
                + " &7Deaths: &f" + record.deaths()
                + " &7K/D: &f" + String.format(Locale.ROOT, "%.2f", record.ratio())));
        sender.sendMessage(Msg.color("  &7Wool broken: &f" + record.woolsBroken()));
    }

    private boolean requireBuilt(CommandSender sender, ArenaInstance instance) {
        if (!instance.arena().isBuilt()) {
            Msg.send(sender, "&cArena '" + instance.name() + "' is not built yet. Run &f/walls reset&c first.");
            return false;
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        Msg.send(sender, "&bWalls &7v" + plugin.getDescription().getVersion());
        String[][] rows = {
                {"reset", "build or rebuild the arena"},
                {"start", "start a match"},
                {"up", "raise the walls"},
                {"down", "drop the walls"},
                {"stop", "end the current match"},
                {"status", "arena and team state"},
                {"join", "join the waiting lobby"},
                {"tp", "teleport to the arena"},
                {"mode [name]", "show or switch the game mode"},
                {"arenas", "list every arena"},
                {"watch", "spectate a surviving player"},
                {"stats [player]", "match records"},
        };
        for (String[] row : rows) {
            sender.sendMessage(Msg.color("  &f/" + label + " " + row[0] + " &7- " + row[1]));
        }
        if (arenas.size() > 1) {
            sender.sendMessage(Msg.color("  &7Add an arena name to target one: &f/" + label + " reset second"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> matches = new ArrayList<>();
        if (args.length == 1) {
            addMatching(matches, SUBCOMMANDS, args[0]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            addMatching(matches, plugin.settings().modes().keySet(), args[1]);
        } else if (args.length == 2) {
            List<String> names = new ArrayList<>();
            arenas.all().forEach(instance -> names.add(instance.name()));
            addMatching(matches, names, args[1]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("mode")) {
            List<String> names = new ArrayList<>();
            arenas.all().forEach(instance -> names.add(instance.name()));
            addMatching(matches, names, args[2]);
        }
        return matches;
    }

    private static void addMatching(List<String> into, Iterable<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                into.add(option);
            }
        }
    }
}
