package pro.qolar.walls.game;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;
import pro.qolar.walls.ModeSettings;
import pro.qolar.walls.WallsConfig;
import pro.qolar.walls.WallsPlugin;
import pro.qolar.walls.arena.Arena;
import pro.qolar.walls.util.Msg;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

/** Runs a match: teams, phases, respawns, objectives and the win condition. */
public final class GameManager {

    private final WallsPlugin plugin;
    private final WallsConfig config;
    private final Arena arena;
    private final ModeSettings mode;

    private final List<GameTeam> teams = new ArrayList<>();
    private final Map<UUID, Integer> assignment = new HashMap<>();
    private final Set<UUID> lobby = new LinkedHashSet<>();

    private GameState state = GameState.IDLE;
    private int secondsLeft;
    private BukkitTask ticker;
    private BossBar bossBar;
    private Scoreboard scoreboard;
    private org.bukkit.scoreboard.Objective sidebar;
    private List<String> renderedLines = new ArrayList<>();

    public GameManager(WallsPlugin plugin, WallsConfig config, Arena arena, ModeSettings mode) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.mode = mode;
        TeamColor[] colors = TeamColor.values();
        for (int i = 0; i < arena.layout().size(); i++) {
            teams.add(new GameTeam(i, colors[i % colors.length], config.objectiveMaxHealth()));
        }
    }

    public GameState state() {
        return state;
    }

    public List<GameTeam> teams() {
        return teams;
    }

    public Arena arena() {
        return arena;
    }

    public ModeSettings mode() {
        return mode;
    }

    public GameTeam teamOf(Player player) {
        Integer index = assignment.get(player.getUniqueId());
        return index == null ? null : teams.get(index);
    }

    // --- lobby -----------------------------------------------------------

    public boolean join(Player player) {
        if (state.inProgress()) {
            return false;
        }
        lobby.add(player.getUniqueId());
        arena.ensureWorld();
        player.teleport(arena.baseSpawn(0));
        return true;
    }

    public void leave(Player player) {
        UUID id = player.getUniqueId();
        lobby.remove(id);
        GameTeam team = teamOf(player);
        if (team != null) {
            team.putDown(id);
            assignment.remove(id);
            team.remove(id);
            if (state == GameState.OPEN) {
                checkForWinner();
            }
        }
        clearScoreboard(player);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }
    }

    public int lobbySize() {
        return onlineLobby().size();
    }

    private List<Player> onlineLobby() {
        List<Player> players = new ArrayList<>();
        for (UUID id : lobby) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    // --- starting and stopping -------------------------------------------

    /** Begin a match. Returns an error message, or {@code null} on success. */
    public String start(CommandSender sender) {
        if (state.inProgress()) {
            return "A match is already running (" + state.displayName() + "). Use /walls stop first.";
        }
        if (!arena.isBuilt()) {
            return "The arena has not been built yet. Run /walls reset first.";
        }
        if (arena.isBusy()) {
            return "The arena is still building. Try again in a moment.";
        }

        // Explicit joins win; otherwise everyone online plays.
        List<Player> candidates = onlineLobby();
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(Bukkit.getOnlinePlayers());
        }
        if (candidates.isEmpty()) {
            return "Nobody is online to play.";
        }

        assignment.clear();
        for (GameTeam team : teams) {
            team.clear();
        }
        for (int i = 0; i < candidates.size(); i++) {
            GameTeam team = teams.get(i % teams.size());
            UUID id = candidates.get(i).getUniqueId();
            team.add(id);
            assignment.put(id, team.index());
        }

        long populated = teams.stream().filter(t -> t.size() > 0).count();
        if (populated < 2) {
            assignment.clear();
            teams.forEach(GameTeam::clear);
            return "Need players on at least 2 teams to start (" + candidates.size()
                    + " player(s) online, " + populated + " team(s) filled).";
        }

        if (candidates.size() > mode.capacity()) {
            Msg.broadcast("&e" + candidates.size() + " players on a '" + mode.name()
                    + "' arena built for " + mode.capacity()
                    + ". &7Consider a larger mode with /walls mode.");
        }

        setUpScoreboard();
        for (GameTeam team : teams) {
            if (team.size() == 0) {
                arena.clearObjective(team.index());
                continue;
            }
            arena.placeObjective(team.index(), team.color().wool());
        }
        for (Player player : candidates) {
            prepare(player);
        }

        state = GameState.COUNTDOWN;
        secondsLeft = config.countdownSeconds();
        startTicker();
        Msg.broadcast("&bMatch starting! &7" + populated + " teams, " + candidates.size() + " players.");
        return null;
    }

    /** End a match early and return to idle without rebuilding. */
    public void stop(String reason) {
        if (state == GameState.IDLE) {
            return;
        }
        Msg.broadcast("&cMatch stopped: &7" + reason);
        toIdle();
    }

    private void toIdle() {
        state = GameState.IDLE;
        stopTicker();
        for (UUID id : new ArrayList<>(assignment.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                clearScoreboard(player);
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
        assignment.clear();
        for (GameTeam team : teams) {
            team.clear();
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        scoreboard = null;
        sidebar = null;
        renderedLines = new ArrayList<>();
    }

    private void prepare(Player player) {
        GameTeam team = teamOf(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        player.setLevel(0);
        player.setExp(0f);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setHealth(player.getMaxHealth());
        // Remove effects by instance rather than by static type constant: the
        // PotionEffectType constants were reshuffled between 1.20 and 1.21.
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
        if (team != null) {
            player.teleport(arena.baseSpawn(team.index()));
            Msg.send(player, "You are on " + team.color().colored() + "&7. Defend your "
                    + team.color().chatColor() + "wool&7!");
        }
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
        if (scoreboard != null) {
            player.setScoreboard(scoreboard);
        }
    }

    // --- phases ----------------------------------------------------------

    private void startTicker() {
        stopTicker();
        ticker = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void tick() {
        switch (state) {
            case COUNTDOWN -> {
                secondsLeft--;
                if (secondsLeft <= 0) {
                    beginGrace();
                } else if (secondsLeft <= 5 || secondsLeft % 10 == 0) {
                    Msg.broadcast("&eMatch begins in &f" + secondsLeft + "s");
                }
            }
            case GRACE -> {
                secondsLeft--;
                if (secondsLeft <= 0) {
                    beginOpen();
                } else if (secondsLeft <= 5 || secondsLeft % 30 == 0) {
                    Msg.broadcast("&eThe walls fall in &f" + formatTime(secondsLeft));
                }
            }
            case OPEN -> checkForWinner();
            case ENDED -> {
                secondsLeft--;
                if (secondsLeft <= 0) {
                    finishAndRebuild();
                    return;
                }
            }
            default -> {
                return;
            }
        }
        updateBossBar();
        updateSidebar();
    }

    private void beginGrace() {
        state = GameState.GRACE;
        secondsLeft = mode.graceSeconds();
        Msg.broadcast("&aGo! &7Gather and fortify - the walls fall in " + formatTime(secondsLeft) + ".");
        title("&a&lGO!", "&7Prepare your base");
    }

    private void beginOpen() {
        state = GameState.OPEN;
        secondsLeft = 0;
        title("&c&lWALLS DOWN!", "&7Break their wool. Last team standing wins.");
        Msg.broadcast("&c&lThe walls are falling! &7Loot the centre and fight.");
        try {
            arena.dropWalls(message -> Msg.broadcast("&7" + message), () -> {
            });
        } catch (IllegalStateException alreadyMoving) {
            plugin.getLogger().warning("Walls were already moving when the grace period ended: "
                    + alreadyMoving.getMessage());
        }
        for (Player player : onlinePlayersInMatch()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        }
    }

    /** Admin forced the walls down mid-grace. */
    public boolean forceOpen() {
        if (state == GameState.COUNTDOWN || state == GameState.GRACE) {
            beginOpen();
            return true;
        }
        return false;
    }

    // --- combat ----------------------------------------------------------

    /**
     * A player broke a block. If it was a team objective, chip it and report that
     * the break was handled (the caller cancels the event).
     */
    public boolean handleBlockBreak(Player player, Block block) {
        GameTeam target = objectiveAt(block);
        if (target == null) {
            return false;
        }
        if (state != GameState.OPEN) {
            Msg.send(player, "&cThe wool can only be broken once the walls are down.");
            return true;
        }
        GameTeam own = teamOf(player);
        if (own != null && own.index() == target.index()) {
            Msg.send(player, "&cThat is your own team's wool.");
            return true;
        }
        if (!target.objective().alive()) {
            return true;
        }

        target.objective().damage(config.objectiveDamagePerBreak());
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);

        if (target.objective().alive()) {
            Msg.send(player, target.color().colored() + " &7wool: &f" + target.objective().health()
                    + "&7/" + target.objective().maxHealth() + " HP");
        } else {
            arena.clearObjective(target.index());
            plugin.stats().addWool(player);
            Msg.broadcast(target.color().colored() + "&c's wool has been destroyed! &7They can no longer respawn.");
            for (Player online : onlinePlayersInMatch()) {
                online.playSound(online.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
            }
            checkForWinner();
        }
        updateSidebar();
        return true;
    }

    private GameTeam objectiveAt(Block block) {
        for (GameTeam team : teams) {
            Location location = arena.objectiveLocation(team.index());
            if (location.getWorld() != null
                    && block.getWorld().getName().equals(location.getWorld().getName())
                    && block.getX() == location.getBlockX()
                    && block.getY() == location.getBlockY()
                    && block.getZ() == location.getBlockZ()) {
                return team;
            }
        }
        return null;
    }

    /** Called when a player in a match dies. */
    public void handleDeath(Player player) {
        GameTeam team = teamOf(player);
        if (team == null || !state.inProgress()) {
            return;
        }
        plugin.stats().addDeath(player);
        Player killer = player.getKiller();
        if (killer != null && !killer.equals(player) && teamOf(killer) != null) {
            plugin.stats().addKill(killer);
        }
        if (team.canRespawn()) {
            Msg.broadcast(team.color().chatColor() + player.getName() + " &7died and will respawn.");
            return;
        }
        team.putDown(player.getUniqueId());
        Msg.broadcast(team.color().chatColor() + player.getName() + " &7is out for good. "
                + team.color().colored() + " &7has " + team.aliveCount() + " left.");
        checkForWinner();
    }

    /** Where a dying player should reappear, and in what state. */
    public Location handleRespawn(Player player) {
        GameTeam team = teamOf(player);
        if (team == null) {
            return arena.world() != null ? arena.world().getSpawnLocation() : player.getLocation();
        }
        Location spawn = arena.baseSpawn(team.index());
        if (team.isDown(player.getUniqueId())) {
            // Out of the match: stay to watch.
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                Msg.send(player, "&7You are out. Use &f/walls watch&7 to follow the survivors.");
            });
            return spawn;
        }
        if (config.respawnSeconds() > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                Msg.send(player, "&7Respawning in " + config.respawnSeconds() + "s...");
            });
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && state.inProgress() && !team.isDown(player.getUniqueId())) {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.teleport(arena.baseSpawn(team.index()));
                }
            }, config.respawnSeconds() * 20L);
        }
        return spawn;
    }

    private void checkForWinner() {
        if (state != GameState.OPEN) {
            return;
        }
        List<GameTeam> alive = teams.stream().filter(t -> !t.isEliminated()).toList();
        if (alive.size() > 1) {
            return;
        }
        endMatch(alive.isEmpty() ? null : alive.get(0));
    }

    private void endMatch(GameTeam winner) {
        state = GameState.ENDED;
        secondsLeft = config.endSeconds();
        if (winner == null) {
            Msg.broadcast("&eThe match ended with no survivors.");
            title("&eDraw", "&7No team survived");
        } else {
            Msg.broadcast("&6&l" + winner.color().displayName().toUpperCase() + " WINS!");
            title(winner.color().chatColor() + "&l" + winner.color().displayName() + " wins!",
                    "&7Last team standing");
        }
        for (Player player : onlinePlayersInMatch()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            GameTeam theirs = teamOf(player);
            plugin.stats().addMatch(player, winner != null && theirs != null
                    && theirs.index() == winner.index());
        }
        plugin.stats().save();
    }

    private void finishAndRebuild() {
        toIdle();
        if (!arena.isBusy()) {
            arena.rebuild(message -> Msg.broadcast("&7" + message), () -> {
            });
        }
    }

    // --- display ---------------------------------------------------------

    private void setUpScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        sidebar = scoreboard.registerNewObjective("walls", Criteria.DUMMY,
                ChatColor.AQUA + "" + ChatColor.BOLD + "WALLS");
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);

        for (GameTeam team : teams) {
            org.bukkit.scoreboard.Team bukkitTeam = scoreboard.registerNewTeam("walls" + team.index());
            bukkitTeam.setColor(team.color().chatColor());
            bukkitTeam.setPrefix(team.color().chatColor().toString());
            bukkitTeam.setAllowFriendlyFire(false);
            for (UUID id : team.members()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    bukkitTeam.addEntry(player.getName());
                }
            }
        }

        bossBar = Bukkit.createBossBar(ChatColor.AQUA + "Walls", BarColor.BLUE, BarStyle.SOLID);
        bossBar.setProgress(1.0);
    }

    private void updateBossBar() {
        if (bossBar == null) {
            return;
        }
        String text;
        double progress;
        switch (state) {
            case COUNTDOWN -> {
                text = ChatColor.YELLOW + "Starting in " + secondsLeft + "s";
                progress = clamp((double) secondsLeft / Math.max(1, config.countdownSeconds()));
            }
            case GRACE -> {
                text = ChatColor.YELLOW + "Walls fall in " + formatTime(secondsLeft);
                progress = clamp((double) secondsLeft / Math.max(1, mode.graceSeconds()));
            }
            case OPEN -> {
                long remaining = teams.stream().filter(t -> !t.isEliminated()).count();
                text = ChatColor.RED + "Fight! " + remaining + " teams remain";
                progress = 1.0;
            }
            case ENDED -> {
                text = ChatColor.GOLD + "Match over";
                progress = clamp((double) secondsLeft / Math.max(1, config.endSeconds()));
            }
            default -> {
                text = ChatColor.GRAY + "Idle";
                progress = 1.0;
            }
        }
        bossBar.setTitle(text);
        bossBar.setProgress(progress);
        bossBar.setColor(state == GameState.OPEN ? BarColor.RED : BarColor.BLUE);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private void updateSidebar() {
        if (sidebar == null || scoreboard == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(ChatColor.GRAY + "Phase: " + ChatColor.WHITE + state.displayName());
        if (state == GameState.COUNTDOWN || state == GameState.GRACE) {
            lines.add(ChatColor.GRAY + "Walls fall: " + ChatColor.WHITE + formatTime(secondsLeft));
        }
        lines.add(" ");
        for (GameTeam team : teams) {
            if (team.size() == 0) {
                continue;
            }
            String status;
            if (team.isEliminated()) {
                status = ChatColor.DARK_GRAY + "OUT";
            } else if (!team.objective().alive()) {
                status = ChatColor.RED + "no respawn " + ChatColor.GRAY + "(" + team.aliveCount() + ")";
            } else {
                status = ChatColor.WHITE.toString() + team.objective().health() + "hp "
                        + ChatColor.GRAY + "(" + team.aliveCount() + ")";
            }
            lines.add(team.color().chatColor() + team.color().displayName() + " " + status);
        }

        for (String old : renderedLines) {
            scoreboard.resetScores(old);
        }
        renderedLines = lines;
        int score = lines.size();
        for (String line : lines) {
            sidebar.getScore(line).setScore(score--);
        }
    }

    private void clearScoreboard(Player player) {
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void title(String heading, String subtitle) {
        for (Player player : onlinePlayersInMatch()) {
            player.sendTitle(Msg.color(heading), Msg.color(subtitle), 5, 50, 10);
        }
    }

    private List<Player> onlinePlayersInMatch() {
        List<Player> players = new ArrayList<>();
        for (UUID id : assignment.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    /** Players still in the fight, for the spectator menu to point at. */
    public List<Player> survivors() {
        List<Player> alive = new ArrayList<>();
        for (GameTeam team : teams) {
            if (team.isEliminated()) {
                continue;
            }
            for (UUID id : team.members()) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline() && !team.isDown(id)) {
                    alive.add(player);
                }
            }
        }
        return alive;
    }

    public static String formatTime(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("%d:%02d", safe / 60, safe % 60);
    }

    /** Lines for {@code /walls status}. */
    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("&7State: &f" + state.displayName()
                + (state.inProgress() && secondsLeft > 0 ? " &7(" + formatTime(secondsLeft) + ")" : ""));
        lines.add("&7Arena: &f" + (arena.isBuilt() ? "built" : "not built")
                + "&7, walls &f" + (arena.wallsUp() ? "up" : "down")
                + (arena.isBusy() ? " &e(working)" : ""));
        lines.add("&7Caves: &f" + arena.caves().carvedCount() + "&7 carved, &f"
                + arena.caves().oreCount() + "&7 ore, &f" + arena.caves().chestSpots().size() + "&7 chests");
        lines.add("&7Lobby: &f" + lobbySize() + " waiting");
        for (GameTeam team : teams) {
            lines.add("  " + team.color().colored() + "&7: &f" + team.size() + " players&7, wool &f"
                    + team.objective().health() + "&7/&f" + team.objective().maxHealth()
                    + (team.isEliminated() && team.size() > 0 ? " &c(out)" : ""));
        }
        return lines;
    }

    public void shutdown() {
        stopTicker();
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }
}
