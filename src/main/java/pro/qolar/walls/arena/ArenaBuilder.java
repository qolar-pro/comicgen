package pro.qolar.walls.arena;

import org.bukkit.Material;

import java.util.function.IntConsumer;

/**
 * Places a volume of blocks into the world.
 *
 * <p>This interface exists so the arena never talks to a block-placing backend
 * directly. v1 ships {@link BukkitBatchBuilder}, which needs no server-side
 * dependencies. When designed maps arrive, a WorldEdit-backed implementation
 * (schematic pasting, faster region fills) drops in here without the arena or
 * game code changing.
 */
public interface ArenaBuilder {

    /**
     * A box of blocks to paint, and what to paint into it.
     *
     * <p>Bounds are inclusive.
     */
    interface BlockVolume {

        int minX();

        int minY();

        int minZ();

        int maxX();

        int maxY();

        int maxZ();

        /**
         * The material for one position, or {@code null} to leave whatever is
         * already there untouched.
         */
        Material materialAt(int x, int y, int z);
    }

    /**
     * Paint {@code volume}, spreading the work across ticks so the server keeps
     * responding.
     *
     * @param onDone called on the main thread with the number of blocks changed
     * @throws IllegalStateException if a build is already running
     */
    void fill(BlockVolume volume, IntConsumer onDone);

    /** Abandon any running build. */
    void cancel();

    boolean isRunning();
}
