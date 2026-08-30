package pro.qolar.walls.arena;

import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import pro.qolar.walls.WallsConfig;
import pro.qolar.walls.loot.LootTable;

import java.util.Random;
import java.util.function.Consumer;

/**
 * The arena world: builds it, raises and drops the walls, and answers questions
 * about what is protected.
 *
 * <p>Knows nothing about teams, scores, or match flow - that is {@code GameManager}'s
 * job. This class only shapes blocks.
 */
public final class Arena {

    private final Plugin plugin;
    private final WallsConfig config;
    private final ArenaGeometry geo;
    private final SectorLayout layout;
    private final Random random = new Random();

    private World world;
    private ArenaBuilder builder;
    private BukkitTask wallTask;
    private boolean wallsUp;
    private boolean built;

    public Arena(Plugin plugin, WallsConfig config, SectorLayout layout) {
        this.plugin = plugin;
        this.config = config;
        this.geo = config.geometry();
        this.layout = layout;
    }

    // --- world -----------------------------------------------------------

    /** Create the arena world if it does not exist yet, and apply its rules. */
    public World ensureWorld() {
        if (world != null) {
            return world;
        }
        World existing = plugin.getServer().getWorld(config.worldName());
        world = existing != null ? existing
                : new WorldCreator(config.worldName())
                        .generator(new VoidChunkGenerator())
                        .createWorld();
        if (world == null) {
            throw new IllegalStateException("could not create arena world '" + config.worldName() + "'");
        }
        applyWorldRules(world);
        return world;
    }

    private void applyWorldRules(World w) {
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.KEEP_INVENTORY, false);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setDifficulty(Difficulty.NORMAL);
        w.setTime(6000L);
        w.setStorm(false);
        w.setAutoSave(false);
        // Land on the vault roof: a safe vantage point over all four quadrants.
        w.setSpawnLocation(config.centerX(), geo.wallTopY() + 1, config.centerZ());
    }

    public World world() {
        return world;
    }

    public ArenaGeometry geometry() {
        return geo;
    }

    public SectorLayout layout() {
        return layout;
    }

    public boolean isBuilt() {
        return built;
    }

    public boolean wallsUp() {
        return wallsUp;
    }

    public boolean isBusy() {
        return (builder != null && builder.isRunning()) || wallTask != null;
    }

    // --- coordinates -----------------------------------------------------

    public int worldX(int dx) {
        return config.centerX() + dx;
    }

    public int worldZ(int dz) {
        return config.centerZ() + dz;
    }

    public int localX(int x) {
        return x - config.centerX();
    }

    public int localZ(int z) {
        return z - config.centerZ();
    }

    /** Centre of the loot vault, standing on its floor. */
    public Location vaultCentre() {
        return new Location(world, worldX(0) + 0.5, geo.floorY() + 1.0, worldZ(0) + 0.5);
    }

    /** Where a team spawns: a few blocks in front of its objective, facing the centre. */
    public Location baseSpawn(int teamIndex) {
        Sector sector = layout.get(teamIndex);
        int dx = sector.baseDx() - Integer.signum(sector.baseDx()) * 4;
        int dz = sector.baseDz() - Integer.signum(sector.baseDz()) * 4;
        return new Location(world, worldX(dx) + 0.5, geo.floorY() + 1.0, worldZ(dz) + 0.5,
                sector.spawnYaw(), 0f);
    }

    /** The block holding a team's objective. */
    public Location objectiveLocation(int teamIndex) {
        Sector sector = layout.get(teamIndex);
        return new Location(world, worldX(sector.baseDx()), geo.floorY() + 2.0, worldZ(sector.baseDz()));
    }

    public boolean isInArena(Location location) {
        if (world == null || location.getWorld() == null
                || !location.getWorld().getName().equals(world.getName())) {
            return false;
        }
        return geo.isUnderCeiling(localX(location.getBlockX()), localZ(location.getBlockZ()));
    }

    // --- protection ------------------------------------------------------

    /**
     * True for blocks players must never break: the bedrock course, the glass
     * shell and ceiling, and - while they stand - the walls themselves.
     */
    public boolean isProtected(Block block) {
        if (world == null || !block.getWorld().getName().equals(world.getName())) {
            return false;
        }
        int dx = localX(block.getX());
        int dz = localZ(block.getZ());
        int y = block.getY();

        if (y <= geo.bottomY()) {
            return true;
        }
        if (geo.isGlassColumn(dx, dz) || y >= geo.ceilingY()) {
            return true;
        }
        return wallsUp && isWallBlock(dx, dz, y);
    }

    /** Whether a position belongs to the wall structure (arms plus the vault shell and lid). */
    public boolean isWallBlock(int dx, int dz, int y) {
        if (y <= geo.floorY() || y > geo.wallTopY()) {
            return false;
        }
        if (y == geo.wallTopY() && geo.isVaultLid(dx, dz)) {
            return true;
        }
        return geo.isWallColumn(dx, dz);
    }

    // --- building --------------------------------------------------------

    /**
     * Rebuild the whole arena from scratch: platform, glass shell, walls up,
     * team bases, objectives and stocked loot chests.
     *
     * <p>Also clears anything players built or dropped, which is what makes this
     * a genuine reset rather than a patch-up.
     */
    public void rebuild(Consumer<String> feedback, Runnable onDone) {
        ensureWorld();
        if (isBusy()) {
            throw new IllegalStateException("the arena is already being built");
        }
        clearLooseEntities();

        feedback.accept("Building arena (this takes a moment)...");
        builder = new BukkitBatchBuilder(plugin, world, config.blocksPerTick());
        builder.fill(fullVolume(), placed -> {
            placeBases();
            placeChests();
            wallsUp = true;
            built = true;
            builder = null;
            feedback.accept("Arena ready - " + placed + " blocks placed, walls up.");
            onDone.run();
        });
    }

    private void clearLooseEntities() {
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    /** The whole arena as one volume - platform, glass, walls, and air everywhere else. */
    private ArenaBuilder.BlockVolume fullVolume() {
        int reach = geo.radius() + 1;
        return new ArenaBuilder.BlockVolume() {
            @Override
            public int minX() {
                return worldX(-reach);
            }

            @Override
            public int maxX() {
                return worldX(reach);
            }

            @Override
            public int minZ() {
                return worldZ(-reach);
            }

            @Override
            public int maxZ() {
                return worldZ(reach);
            }

            @Override
            public int minY() {
                return geo.bottomY();
            }

            @Override
            public int maxY() {
                return geo.ceilingY();
            }

            @Override
            public Material materialAt(int x, int y, int z) {
                int dx = localX(x);
                int dz = localZ(z);

                if (y == geo.ceilingY()) {
                    return geo.isUnderCeiling(dx, dz) ? config.glassMaterial() : null;
                }
                if (geo.isGlassColumn(dx, dz)) {
                    return config.glassMaterial();
                }
                if (!geo.isPlatform(dx, dz)) {
                    return null;
                }
                if (y <= geo.floorY()) {
                    return layerMaterial(geo.layerAtY(y));
                }
                if (isWallBlock(dx, dz, y)) {
                    return config.wallMaterial();
                }
                // Above the floor and not a wall: air, so a reset wipes player builds.
                return Material.AIR;
            }
        };
    }

    private Material layerMaterial(ArenaGeometry.Layer layer) {
        return switch (layer) {
            case GRASS -> Material.GRASS_BLOCK;
            case DIRT -> Material.DIRT;
            case STONE -> Material.STONE;
            case BEDROCK -> Material.BEDROCK;
        };
    }

    private void placeBases() {
        for (Sector sector : layout.all()) {
            int bx = worldX(sector.baseDx());
            int bz = worldZ(sector.baseDz());
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    world.getBlockAt(bx + ox, geo.floorY(), bz + oz).setType(config.baseMaterial(), false);
                }
            }
            world.getBlockAt(bx, geo.floorY() + 1, bz).setType(config.baseMaterial(), false);
        }
    }

    /** Put a team's objective block back, full health. */
    public void placeObjective(int teamIndex, Material wool) {
        Location location = objectiveLocation(teamIndex);
        world.getBlockAt(location).setType(wool, false);
    }

    public void clearObjective(int teamIndex) {
        world.getBlockAt(objectiveLocation(teamIndex)).setType(Material.AIR, false);
    }

    private void placeChests() {
        for (int[] offset : geo.chestOffsets(config.chestCount(), config.chestRingRadius())) {
            Block block = world.getBlockAt(worldX(offset[0]), geo.floorY() + 1, worldZ(offset[1]));
            block.setType(Material.CHEST, false);
            BlockState state = block.getState();
            if (state instanceof Chest chest) {
                LootTable.fill(chest.getBlockInventory(), random, 6 + random.nextInt(5));
            }
        }
    }

    // --- the walls -------------------------------------------------------

    /** Put the walls back up, bottom-up. */
    public void raiseWalls(Consumer<String> feedback, Runnable onDone) {
        animateWalls(true, feedback, onDone);
    }

    /** Drop the walls, top-down. The moment the whole game is built around. */
    public void dropWalls(Consumer<String> feedback, Runnable onDone) {
        animateWalls(false, feedback, onDone);
    }

    private void animateWalls(boolean raising, Consumer<String> feedback, Runnable onDone) {
        ensureWorld();
        if (wallTask != null) {
            throw new IllegalStateException("the walls are already moving");
        }
        int height = geo.wallTopY() - geo.floorY();
        long interval = Math.max(1L, config.wallFallTicks() / Math.max(1, height));

        wallTask = new BukkitRunnable() {
            private int step;

            @Override
            public void run() {
                if (step >= height) {
                    cancel();
                    wallTask = null;
                    wallsUp = raising;
                    feedback.accept(raising ? "The walls are up." : "The walls have fallen!");
                    onDone.run();
                    return;
                }
                // Raising builds from the ground up; dropping peels from the top down.
                int y = raising ? geo.floorY() + 1 + step : geo.wallTopY() - step;
                applyWallLayer(y, raising);
                step++;
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    private void applyWallLayer(int y, boolean solid) {
        Material material = solid ? config.wallMaterial() : Material.AIR;
        int r = geo.radius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (!isWallBlock(dx, dz, y)) {
                    continue;
                }
                Block block = world.getBlockAt(worldX(dx), y, worldZ(dz));
                if (block.getType() != material) {
                    block.setType(material, false);
                }
            }
        }
    }

    /** Stop any running build or wall animation - used when the plugin shuts down. */
    public void shutdown() {
        if (builder != null) {
            builder.cancel();
            builder = null;
        }
        if (wallTask != null) {
            wallTask.cancel();
            wallTask = null;
        }
    }
}
