package pro.qolar.walls.listener;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.projectiles.ProjectileSource;
import pro.qolar.walls.arena.Arena;
import pro.qolar.walls.game.GameManager;
import pro.qolar.walls.game.GameState;
import pro.qolar.walls.game.GameTeam;
import pro.qolar.walls.util.Msg;

/** Wires world and player events into the match. */
public final class GameListener implements Listener {

    private final GameManager game;
    private final Arena arena;

    public GameListener(GameManager game) {
        this.game = game;
        this.arena = game.arena();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!inArenaWorld(event.getBlock().getWorld().getName())) {
            return;
        }
        Player player = event.getPlayer();

        // Objectives are never really broken - they are chipped.
        if (game.handleBlockBreak(player, event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (arena.isProtected(event.getBlock())) {
            event.setCancelled(true);
            Msg.send(player, "&cThat block is part of the arena and cannot be broken.");
            return;
        }
        if (game.state() == GameState.COUNTDOWN) {
            event.setCancelled(true);
            Msg.send(player, "&cWait for the match to begin.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!inArenaWorld(event.getBlock().getWorld().getName())) {
            return;
        }
        Player player = event.getPlayer();

        if (arena.isProtected(event.getBlock())) {
            event.setCancelled(true);
            Msg.send(player, "&cYou cannot build there.");
            return;
        }
        if (game.state() == GameState.COUNTDOWN) {
            event.setCancelled(true);
            Msg.send(player, "&cWait for the match to begin.");
            return;
        }
        // While the walls stand, nobody towers over them.
        int wallTop = arena.geometry().wallTopY();
        if (game.state() == GameState.GRACE && event.getBlock().getY() > wallTop) {
            event.setCancelled(true);
            Msg.send(player, "&cYou cannot build above the walls before they fall.");
        }
    }

    /** TNT is in the loot table, so explosions must respect the arena shell. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (!inArenaWorld(event.getLocation().getWorld().getName())) {
            return;
        }
        event.blockList().removeIf(arena::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        GameTeam victimTeam = game.teamOf(victim);
        GameTeam attackerTeam = game.teamOf(attacker);
        if (victimTeam == null || attackerTeam == null) {
            return;
        }
        if (game.state() != GameState.OPEN) {
            event.setCancelled(true);
            Msg.send(attacker, "&cPvP is disabled until the walls fall.");
            return;
        }
        if (victimTeam.index() == attackerTeam.index()) {
            event.setCancelled(true);
            Msg.send(attacker, "&cThat is your teammate.");
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (game.teamOf(event.getEntity()) == null) {
            return;
        }
        event.setDeathMessage(null);
        game.handleDeath(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (game.teamOf(event.getPlayer()) == null) {
            return;
        }
        event.setRespawnLocation(game.handleRespawn(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        game.leave(event.getPlayer());
    }

    private boolean inArenaWorld(String worldName) {
        return arena.world() != null && arena.world().getName().equals(worldName);
    }
}
