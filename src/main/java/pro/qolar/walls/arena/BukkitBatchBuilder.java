package pro.qolar.walls.arena;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.IntConsumer;

/**
 * Paints volumes using nothing but the Bukkit API, paced across ticks.
 *
 * <p>Pacing is not optional. A default arena is roughly 1.3 million positions;
 * placing those in one tick would freeze the server for seconds and time players
 * out. Instead a fixed budget of block changes is spent per tick, so a full
 * rebuild costs a couple of seconds of wall-clock time and no lag spike.
 *
 * <p>Two budgets are tracked. The placement budget bounds the expensive work
 * (actual block changes); the scan budget bounds the cheap work, so a volume that
 * is mostly already-correct blocks cannot spin through millions of positions in a
 * single tick.
 */
public final class BukkitBatchBuilder implements ArenaBuilder {

    /** Positions inspected per tick, as a multiple of the placement budget. */
    private static final int SCAN_BUDGET_FACTOR = 8;

    private final Plugin plugin;
    private final World world;
    private final int blocksPerTick;

    private BukkitTask task;

    public BukkitBatchBuilder(Plugin plugin, World world, int blocksPerTick) {
        this.plugin = plugin;
        this.world = world;
        this.blocksPerTick = Math.max(1, blocksPerTick);
    }

    @Override
    public void fill(BlockVolume volume, IntConsumer onDone) {
        if (isRunning()) {
            throw new IllegalStateException("a build is already running");
        }
        // Walk x, then z, then y: a whole column at a time keeps us inside one
        // chunk for as long as possible.
        task = new BukkitRunnable() {
            private int x = volume.minX();
            private int z = volume.minZ();
            private int y = volume.minY();
            private int placed;

            @Override
            public void run() {
                int placeBudget = blocksPerTick;
                int scanBudget = blocksPerTick * SCAN_BUDGET_FACTOR;

                while (placeBudget > 0 && scanBudget > 0) {
                    if (x > volume.maxX()) {
                        finish();
                        return;
                    }
                    scanBudget--;
                    Material material = volume.materialAt(x, y, z);
                    if (material != null) {
                        Block block = world.getBlockAt(x, y, z);
                        if (block.getType() != material) {
                            block.setType(material, false);
                            placed++;
                            placeBudget--;
                        }
                    }
                    advance(volume);
                }
            }

            private void advance(BlockVolume v) {
                if (++y > v.maxY()) {
                    y = v.minY();
                    if (++z > v.maxZ()) {
                        z = v.minZ();
                        x++;
                    }
                }
            }

            private void finish() {
                cancel();
                task = null;
                onDone.accept(placed);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public boolean isRunning() {
        return task != null;
    }
}
