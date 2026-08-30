package pro.qolar.walls.game;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;

/**
 * The four team identities.
 *
 * <p>Every material and colour here has been stable since 1.13, so this compiles
 * against 1.20.1 and still resolves on 1.21.x.
 */
public enum TeamColor {

    RED("Red", ChatColor.RED, Material.RED_WOOL, BarColor.RED),
    BLUE("Blue", ChatColor.BLUE, Material.BLUE_WOOL, BarColor.BLUE),
    GREEN("Green", ChatColor.GREEN, Material.LIME_WOOL, BarColor.GREEN),
    YELLOW("Yellow", ChatColor.YELLOW, Material.YELLOW_WOOL, BarColor.YELLOW);

    private final String displayName;
    private final ChatColor chatColor;
    private final Material wool;
    private final BarColor barColor;

    TeamColor(String displayName, ChatColor chatColor, Material wool, BarColor barColor) {
        this.displayName = displayName;
        this.chatColor = chatColor;
        this.wool = wool;
        this.barColor = barColor;
    }

    public String displayName() {
        return displayName;
    }

    public ChatColor chatColor() {
        return chatColor;
    }

    /** The wool used for this team's objective block. */
    public Material wool() {
        return wool;
    }

    public BarColor barColor() {
        return barColor;
    }

    public String colored() {
        return chatColor + displayName + ChatColor.RESET;
    }
}
