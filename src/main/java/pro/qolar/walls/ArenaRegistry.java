package pro.qolar.walls;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every arena on the server, addressable by name or by the world it occupies.
 *
 * <p>The world lookup is what listeners use: an event carries a world, and that
 * world belongs to exactly one arena, so there is never any question about which
 * match an event is part of.
 */
public final class ArenaRegistry {

    private final Map<String, ArenaInstance> byName = new LinkedHashMap<>();
    private final Map<String, ArenaInstance> byWorld = new LinkedHashMap<>();

    public void add(ArenaInstance instance) {
        byName.put(instance.name().toLowerCase(Locale.ROOT), instance);
        byWorld.put(instance.worldName(), instance);
    }

    /** Re-index after a mode swap, in case the world name changed with it. */
    public void reindex() {
        byWorld.clear();
        for (ArenaInstance instance : byName.values()) {
            byWorld.put(instance.worldName(), instance);
        }
    }

    public ArenaInstance byName(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public ArenaInstance byWorld(String worldName) {
        return worldName == null ? null : byWorld.get(worldName);
    }

    /** The arena used when a command does not name one. */
    public ArenaInstance first() {
        return byName.isEmpty() ? null : byName.values().iterator().next();
    }

    public Collection<ArenaInstance> all() {
        return byName.values();
    }

    public int size() {
        return byName.size();
    }
}
