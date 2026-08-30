package pro.qolar.walls.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Chat output.
 *
 * <p>Legacy {@link ChatColor} strings on purpose: Adventure components are a Paper
 * extra, and this plugin has to run on plain Spigot too.
 */
public final class Msg {

    public static final String PREFIX =
            ChatColor.DARK_AQUA + "[" + ChatColor.AQUA + "Walls" + ChatColor.DARK_AQUA + "] " + ChatColor.RESET;

    private Msg() {
    }

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static void send(CommandSender to, String message) {
        to.sendMessage(PREFIX + color(message));
    }

    public static void broadcast(String message) {
        Bukkit.getServer().broadcastMessage(PREFIX + color(message));
    }
}
