package pro.qolar.walls.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pro.qolar.walls.WallsPlugin;
import pro.qolar.walls.arena.Arena;
import pro.qolar.walls.game.GameManager;
import pro.qolar.walls.util.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /walls} - the admin surface.
 *
 * <p>The four commands the minigame is driven by are {@code up}, {@code down},
 * {@code reset} and {@code start}. The rest exist because without them the plugin
 * cannot actually be exercised on a live server.
 */
public final class WallsCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS =
            List.of("up", "down", "reset", "start", "stop", "status", "join", "tp", "help");

    private final WallsPlugin plugin;
    private final GameManager game;
    private final Arena arena;

    public WallsCommand(WallsPlugin plugin, GameManager game) {
        this.plugin = plugin;
        this.game = game;
        this.arena = game.arena();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "up" -> up(sender);
            case "down" -> down(sender);
            case "reset" -> reset(sender);
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "status" -> status(sender);
            case "join" -> join(sender);
            case "tp" -> teleport(sender);
            case "help" -> help(sender, label);
            default -> Msg.send(sender, "&cUnknown subcommand &f" + args[0] + "&c. Try &f/" + label + " help");
        }
        return true;
    }

    private void up(CommandSender sender) {
        if (!requireBuilt(sender)) {
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

    private void down(CommandSender sender) {
        if (!requireBuilt(sender)) {
            return;
        }
        // Mid-match this ends the grace period properly rather than just clearing blocks.
        if (game.forceOpen()) {
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

    private void reset(CommandSender sender) {
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
                Msg.send(sender, "&aArena reset. Run &f/walls start&a when players are ready.");
            });
        } catch (IllegalStateException busy) {
            Msg.send(sender, "&c" + busy.getMessage());
        }
    }

    private void start(CommandSender sender) {
        String error = game.start(sender);
        if (error != null) {
            Msg.send(sender, "&c" + error);
            return;
        }
        Msg.send(sender, "&aMatch started.");
    }

    private void stop(CommandSender sender) {
        if (!game.state().inProgress()) {
            Msg.send(sender, "&eNo match is running.");
            return;
        }
        game.stop("stopped by " + sender.getName());
        Msg.send(sender, "&aMatch stopped.");
    }

    private void status(CommandSender sender) {
        Msg.send(sender, "&bArena status");
        for (String line : game.statusLines()) {
            sender.sendMessage(Msg.color(line));
        }
    }

    private void join(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&cOnly a player can join.");
            return;
        }
        if (!requireBuilt(sender)) {
            return;
        }
        if (game.join(player)) {
            Msg.send(sender, "&aJoined the lobby. &7Waiting players: &f" + game.lobbySize());
        } else {
            Msg.send(sender, "&cA match is already running.");
        }
    }

    private void teleport(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.send(sender, "&cOnly a player can teleport.");
            return;
        }
        if (!requireBuilt(sender)) {
            return;
        }
        player.teleport(arena.world().getSpawnLocation());
        Msg.send(sender, "&aTeleported to the arena.");
    }

    private boolean requireBuilt(CommandSender sender) {
        if (!arena.isBuilt()) {
            Msg.send(sender, "&cThe arena has not been built yet. Run &f/walls reset&c first.");
            return false;
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        Msg.send(sender, "&bWalls &7v" + plugin.getDescription().getVersion());
        sender.sendMessage(Msg.color("  &f/" + label + " reset &7- build or rebuild the arena"));
        sender.sendMessage(Msg.color("  &f/" + label + " start &7- start a match"));
        sender.sendMessage(Msg.color("  &f/" + label + " up &7- raise the walls"));
        sender.sendMessage(Msg.color("  &f/" + label + " down &7- drop the walls"));
        sender.sendMessage(Msg.color("  &f/" + label + " stop &7- end the current match"));
        sender.sendMessage(Msg.color("  &f/" + label + " status &7- show arena and team state"));
        sender.sendMessage(Msg.color("  &f/" + label + " join &7- join the waiting lobby"));
        sender.sendMessage(Msg.color("  &f/" + label + " tp &7- teleport to the arena"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (sub.startsWith(prefix)) {
                matches.add(sub);
            }
        }
        return matches;
    }
}
