package pro.qolar.walls.listener;

import org.bukkit.World;
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
import pro.qolar.walls.ArenaInstance;
import pro.qolar.walls.ArenaRegistry;
import pro.qolar.walls.arena.Arena;
import pro.qolar.walls.game.GameManager;
import pro.qolar.walls.game.GameState;
import pro.qolar.walls.game.GameTeam;
import pro.qolar.walls.util.Msg;

/**
 * Wires world and player events into the right match.
 *
 * <p>Every arena owns its world, so the world an event happened in decides which
 * match it belongs to. Events from anywhere else are none of this plugin's
 * business and are left alone.
 */
public final class GameListener implements Listener {

    private final ArenaRegistry arenas;

    public GameListener(ArenaRegistry arenas) {
        this.arenas = arenas;
    }

    private ArenaInstance instanceAt(World world) {
        return world == null ? null : arenas.byWorld(world.getName());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ArenaInstance instance = instanceAt(event.getBlock().getWorld());
        if (instance == null) {
            return;
        }
        GameManager game = instance.game();
        Arena arena = instance.arena();
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
        ArenaInstance instance = instanceAt(event.getBlock().getWorld());
        if (instance == null) {
            return;
        }
        GameManager game = instance.game();
        Player player = event.getPlayer();

        if (instance.arena().isProtected(event.getBlock())) {
            event.setCancelled(true);
            Msg.send(player, "&cYou cannot build there.");
            return;
        }
        if (game.state() == GameState.COUNTDOWN) {
            event.setCancelled(true);
            Msg.send(player, "&cWait for the match to begin.");
            return;
        }
        // While the walls stand, nobody towers over them. The ceiling sits well
        // below the wall top: building level with it would let a player step
        // straight onto the wall and walk into the next sector.
        int ceiling = instance.arena().geometry().graceBuildCeilingY();
        if (game.state() == GameState.GRACE && event.getBlock().getY() > ceiling) {
            event.setCancelled(true);
            Msg.send(player, "&cYou cannot build that high before the walls fall.");
        }
    }

    /** TNT is in the loot table, so explosions must respect the arena shell. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        ArenaInstance instance = instanceAt(event.getLocation().getWorld());
        if (instance == null) {
            return;
        }
        event.blockList().removeIf(instance.arena()::isProtected);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        ArenaInstance instance = instanceAt(victim.getWorld());
        if (instance == null) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        GameManager game = instance.game();
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
        ArenaInstance instance = instanceAt(event.getEntity().getWorld());
        if (instance == null || instance.game().teamOf(event.getEntity()) == null) {
            return;
        }
        event.setDeathMessage(null);
        instance.game().handleDeath(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        for (ArenaInstance instance : arenas.all()) {
            if (instance.game().teamOf(event.getPlayer()) != null) {
                event.setRespawnLocation(instance.game().handleRespawn(event.getPlayer()));
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // A player may be in any arena's lobby or match; ask each.
        for (ArenaInstance instance : arenas.all()) {
            instance.game().leave(event.getPlayer());
        }
    }
}
