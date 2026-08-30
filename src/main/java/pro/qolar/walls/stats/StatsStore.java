package pro.qolar.walls.stats;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player match records, kept in {@code plugins/Walls/stats.yml}.
 *
 * <p>Bukkit's own YAML support does the work, so this adds no dependency. Writes
 * are batched in memory and flushed when a match ends or the plugin stops -
 * touching the disk on every kill would be wasteful in a hundred-player game.
 */
public final class StatsStore {

    /** One player's running totals. */
    public static final class Record {

        private String name = "";
        private int matches;
        private int wins;
        private int kills;
        private int deaths;
        private int woolsBroken;

        public String name() {
            return name;
        }

        public int matches() {
            return matches;
        }

        public int wins() {
            return wins;
        }

        public int kills() {
            return kills;
        }

        public int deaths() {
            return deaths;
        }

        public int woolsBroken() {
            return woolsBroken;
        }

        /** Kills per death, counting a death-free record as its kill count. */
        public double ratio() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }
    }

    private final Plugin plugin;
    private final boolean enabled;
    private final File file;
    private final Map<UUID, Record> records = new HashMap<>();
    private boolean dirty;

    public StatsStore(Plugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        if (enabled) {
            load();
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException notAUuid) {
                plugin.getLogger().warning("Skipping malformed stats entry '" + key + "'");
                continue;
            }
            Record record = new Record();
            record.name = yaml.getString(key + ".name", "");
            record.matches = yaml.getInt(key + ".matches");
            record.wins = yaml.getInt(key + ".wins");
            record.kills = yaml.getInt(key + ".kills");
            record.deaths = yaml.getInt(key + ".deaths");
            record.woolsBroken = yaml.getInt(key + ".wools");
            records.put(id, record);
        }
        plugin.getLogger().info("Loaded stats for " + records.size() + " player(s).");
    }

    /** Write to disk, if anything changed. */
    public void save() {
        if (!enabled || !dirty) {
            return;
        }
        FileConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Record> entry : records.entrySet()) {
            String key = entry.getKey().toString();
            Record record = entry.getValue();
            yaml.set(key + ".name", record.name);
            yaml.set(key + ".matches", record.matches);
            yaml.set(key + ".wins", record.wins);
            yaml.set(key + ".kills", record.kills);
            yaml.set(key + ".deaths", record.deaths);
            yaml.set(key + ".wools", record.woolsBroken);
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin data folder; stats not saved.");
                return;
            }
            yaml.save(file);
            dirty = false;
        } catch (IOException failed) {
            plugin.getLogger().warning("Could not save stats.yml: " + failed.getMessage());
        }
    }

    private Record record(Player player) {
        Record record = records.computeIfAbsent(player.getUniqueId(), id -> new Record());
        record.name = player.getName();
        dirty = true;
        return record;
    }

    public void addKill(Player player) {
        if (enabled) {
            record(player).kills++;
        }
    }

    public void addDeath(Player player) {
        if (enabled) {
            record(player).deaths++;
        }
    }

    public void addWool(Player player) {
        if (enabled) {
            record(player).woolsBroken++;
        }
    }

    public void addMatch(Player player, boolean won) {
        if (!enabled) {
            return;
        }
        Record record = record(player);
        record.matches++;
        if (won) {
            record.wins++;
        }
    }

    /** A player's record, or {@code null} if they have never played. */
    public Record of(UUID id) {
        return records.get(id);
    }

    /**
     * Find a record by name.
     *
     * <p>By stored name rather than by resolving the name to a UUID: that
     * resolution can block on a web request for a name nobody here has seen.
     */
    public Record byName(String name) {
        for (Record record : records.values()) {
            if (record.name.equalsIgnoreCase(name)) {
                return record;
            }
        }
        return null;
    }

    public boolean enabled() {
        return enabled;
    }

    /** The best players by wins, then kills. */
    public List<Map.Entry<UUID, Record>> top(int limit) {
        List<Map.Entry<UUID, Record>> sorted = new ArrayList<>(records.entrySet());
        sorted.sort((a, b) -> {
            int byWins = Integer.compare(b.getValue().wins, a.getValue().wins);
            return byWins != 0 ? byWins : Integer.compare(b.getValue().kills, a.getValue().kills);
        });
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
}
