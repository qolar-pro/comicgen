package pro.qolar.walls.game;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pro.qolar.walls.util.Msg;

import java.util.ArrayList;
import java.util.List;

/**
 * A menu of who is still alive, for players who are out.
 *
 * <p>Being eliminated in a hundred-player match otherwise means staring at
 * whatever happens to be nearby. Clicking a head teleports you to that player.
 *
 * <p>Uses a plain Bukkit inventory with player heads - no dependency, and skull
 * metadata has been stable across every supported version.
 */
public final class SpectatorMenu implements Listener {

    private static final String TITLE = ChatColor.DARK_AQUA + "Still standing";

    /** Open the menu for a spectator, listing everyone still in the fight. */
    public static void open(Player viewer, GameManager game) {
        List<Player> survivors = game.survivors();
        if (survivors.isEmpty()) {
            Msg.send(viewer, "&7Nobody is left to watch.");
            return;
        }
        int rows = Math.max(1, Math.min(6, (survivors.size() + 8) / 9));
        Inventory menu = Bukkit.createInventory(null, rows * 9, TITLE);

        for (int i = 0; i < survivors.size() && i < rows * 9; i++) {
            menu.setItem(i, headOf(survivors.get(i), game));
        }
        viewer.openInventory(menu);
    }

    private static ItemStack headOf(Player player, GameManager game) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        GameTeam team = game.teamOf(player);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
        }
        if (meta != null) {
            String colour = team == null ? ChatColor.WHITE.toString() : team.color().chatColor().toString();
            meta.setDisplayName(colour + player.getName());
            meta.setLore(List.of(
                    ChatColor.GRAY + "Team: " + (team == null ? "none" : team.color().displayName()),
                    ChatColor.GRAY + "Health: " + ChatColor.WHITE
                            + Math.round(player.getHealth()) + "/" + Math.round(player.getMaxHealth()),
                    ChatColor.DARK_GRAY + "Click to watch"));
            head.setItemMeta(meta);
        }
        return head;
    }

    private final List<GameManager> games = new ArrayList<>();

    public SpectatorMenu(List<GameManager> games) {
        this.games.addAll(games);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        // It is a viewer, not a container: never let anything be taken out.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null) {
            return;
        }
        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        Player target = Bukkit.getPlayerExact(name);
        if (target == null || !target.isOnline()) {
            Msg.send(viewer, "&c" + name + " is no longer online.");
            return;
        }
        viewer.closeInventory();
        viewer.teleport(target.getLocation());
        Msg.send(viewer, "&7Now watching &f" + target.getName() + "&7.");
    }
}
