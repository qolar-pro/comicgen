package pro.qolar.walls.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Caves and ore worked into the stone body of the platform.
 *
 * <p>This is what the fifty-block depth was for: somewhere to dig, and something
 * worth finding down there.
 *
 * <p>Generated once per rebuild from a seed and then read as a lookup, so the
 * arena stays a pure function of its configuration - the same seed always gives
 * the same caves, which is what lets them be tested.
 *
 * <p>Carving never touches a wall column, nor anything within
 * {@code wallMargin} of one. That is not a nicety: a tunnel crossing under a wall
 * would undo the sealing the whole game depends on, so it is prevented by
 * construction rather than hoped for.
 *
 * <p>Free of Bukkit imports; {@link Ore} is mapped to real blocks by the caller.
 */
public final class CaveField {

    /** Ores that have existed unchanged since 1.13, so they resolve on every supported version. */
    public enum Ore {
        COAL, COPPER, IRON, LAPIS, GOLD, REDSTONE, DIAMOND, EMERALD
    }

    /** One ore's depth band (measured down from the grass) and how much of it to scatter. */
    private record Vein(Ore ore, int minDepth, int maxDepth, int clusters, int minSize, int maxSize) {
    }

    private static final List<Vein> VEINS = List.of(
            new Vein(Ore.COAL, 6, 40, 90, 4, 12),
            new Vein(Ore.COPPER, 8, 32, 55, 4, 10),
            new Vein(Ore.IRON, 10, 46, 70, 3, 8),
            new Vein(Ore.LAPIS, 20, 47, 22, 2, 6),
            new Vein(Ore.GOLD, 30, 47, 22, 2, 6),
            new Vein(Ore.REDSTONE, 30, 47, 30, 3, 8),
            new Vein(Ore.DIAMOND, 38, 47, 12, 1, 4),
            new Vein(Ore.EMERALD, 40, 47, 6, 1, 2));

    private final Set<Long> carved;
    private final Map<Long, Ore> ores;
    private final List<int[]> chestSpots;

    private CaveField(Set<Long> carved, Map<Long, Ore> ores, List<int[]> chestSpots) {
        this.carved = carved;
        this.ores = ores;
        this.chestSpots = chestSpots;
    }

    /** An empty field, for when caves are switched off. */
    public static CaveField none() {
        return new CaveField(Set.of(), Map.of(), List.of());
    }

    /**
     * Carve a cave system.
     *
     * @param wormCount  how many tunnels to dig
     * @param wormLength steps each tunnel takes
     * @param wallMargin how far carving must stay clear of any wall column
     * @param chestCount how many loot chests to leave in the caves
     */
    public static CaveField generate(ArenaGeometry geometry, ColumnMap columns, long seed,
                                     int wormCount, int wormLength, int wallMargin, int chestCount) {
        Random random = new Random(seed);
        Set<Long> carved = new HashSet<>();
        int radius = geometry.radius();

        for (int worm = 0; worm < wormCount; worm++) {
            int[] start = findStart(geometry, columns, random, wallMargin);
            if (start == null) {
                continue;
            }
            digWorm(geometry, columns, random, carved, start, wormLength, wallMargin);
        }

        Map<Long, Ore> ores = new HashMap<>();
        for (Vein vein : VEINS) {
            for (int cluster = 0; cluster < vein.clusters(); cluster++) {
                int dx = random.nextInt(2 * radius + 1) - radius;
                int dz = random.nextInt(2 * radius + 1) - radius;
                int depth = vein.minDepth() + random.nextInt(vein.maxDepth() - vein.minDepth() + 1);
                int y = geometry.floorY() - depth;
                int size = vein.minSize() + random.nextInt(vein.maxSize() - vein.minSize() + 1);
                scatterOre(geometry, columns, random, carved, ores, vein.ore(), dx, y, dz, size, wallMargin);
            }
        }

        List<int[]> chestSpots = pickChestSpots(geometry, carved, random, chestCount);
        return new CaveField(carved, ores, chestSpots);
    }

    private static int[] findStart(ArenaGeometry geometry, ColumnMap columns, Random random, int margin) {
        int radius = geometry.radius();
        for (int attempt = 0; attempt < 64; attempt++) {
            int dx = random.nextInt(2 * radius + 1) - radius;
            int dz = random.nextInt(2 * radius + 1) - radius;
            // Start in the upper half of the stone, where players will meet it first.
            int y = geometry.floorY() - (8 + random.nextInt(Math.max(1, geometry.thickness() / 2)));
            if (mayCarve(geometry, columns, dx, y, dz, margin)) {
                return new int[]{dx, y, dz};
            }
        }
        return null;
    }

    private static void digWorm(ArenaGeometry geometry, ColumnMap columns, Random random,
                                Set<Long> carved, int[] start, int steps, int margin) {
        double x = start[0];
        double y = start[1];
        double z = start[2];

        double yaw = random.nextDouble() * Math.PI * 2;
        // Mostly horizontal, so tunnels run across the map rather than straight down.
        double pitch = (random.nextDouble() - 0.5) * 0.5;
        double radius = 1.6 + random.nextDouble() * 1.4;

        for (int step = 0; step < steps; step++) {
            yaw += (random.nextDouble() - 0.5) * 0.35;
            pitch += (random.nextDouble() - 0.5) * 0.18;
            pitch = Math.max(-0.8, Math.min(0.8, pitch));

            x += Math.cos(yaw) * Math.cos(pitch);
            z += Math.sin(yaw) * Math.cos(pitch);
            y += Math.sin(pitch);

            int cx = (int) Math.round(x);
            int cy = (int) Math.round(y);
            int cz = (int) Math.round(z);
            if (!geometry.isPlatform(cx, cz)) {
                return;
            }
            carveBall(geometry, columns, carved, cx, cy, cz, radius, margin);

            if (random.nextInt(40) == 0) {
                radius = 1.6 + random.nextDouble() * 1.6;
            }
        }
    }

    private static void carveBall(ArenaGeometry geometry, ColumnMap columns, Set<Long> carved,
                                  int cx, int cy, int cz, double radius, int margin) {
        int reach = (int) Math.ceil(radius);
        for (int ox = -reach; ox <= reach; ox++) {
            for (int oy = -reach; oy <= reach; oy++) {
                for (int oz = -reach; oz <= reach; oz++) {
                    if (ox * ox + oy * oy + oz * oz > radius * radius) {
                        continue;
                    }
                    int dx = cx + ox;
                    int y = cy + oy;
                    int dz = cz + oz;
                    if (mayCarve(geometry, columns, dx, y, dz, margin)) {
                        carved.add(key(dx, y, dz));
                    }
                }
            }
        }
    }

    private static void scatterOre(ArenaGeometry geometry, ColumnMap columns, Random random,
                                   Set<Long> carved, Map<Long, Ore> ores, Ore ore,
                                   int cx, int cy, int cz, int size, int margin) {
        int x = cx;
        int y = cy;
        int z = cz;
        for (int i = 0; i < size; i++) {
            if (mayCarve(geometry, columns, x, y, z, margin) && !carved.contains(key(x, y, z))) {
                ores.put(key(x, y, z), ore);
            }
            x += random.nextInt(3) - 1;
            y += random.nextInt(3) - 1;
            z += random.nextInt(3) - 1;
        }
    }

    /**
     * Whether a position may be hollowed out: stone, on ordinary ground, and well
     * clear of any wall.
     */
    private static boolean mayCarve(ArenaGeometry geometry, ColumnMap columns,
                                    int dx, int y, int dz, int margin) {
        if (!geometry.isStoneDepth(y)) {
            return false;
        }
        if (columns.at(dx, dz) != ColumnMap.Column.PLATFORM) {
            return false;
        }
        // Stay clear of the walls, so no tunnel can ever cross under one.
        for (int ox = -margin; ox <= margin; ox++) {
            for (int oz = -margin; oz <= margin; oz++) {
                ColumnMap.Column near = columns.at(dx + ox, dz + oz);
                if (near == ColumnMap.Column.WALL || near == ColumnMap.Column.OUTSIDE
                        || near == ColumnMap.Column.GLASS) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Cave floors with headroom, where a chest can sit and be found. */
    private static List<int[]> pickChestSpots(ArenaGeometry geometry, Set<Long> carved,
                                              Random random, int wanted) {
        if (wanted <= 0 || carved.isEmpty()) {
            return List.of();
        }
        List<int[]> candidates = new ArrayList<>();
        for (long cell : carved) {
            int dx = decode(cell, 42);
            int y = decode(cell, 21);
            int dz = decode(cell, 0);
            boolean floorBelow = !carved.contains(key(dx, y - 1, dz));
            boolean headroom = carved.contains(key(dx, y + 1, dz));
            if (floorBelow && headroom) {
                candidates.add(new int[]{dx, y, dz});
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        // Sort for determinism - iteration order of a hash set is not stable.
        candidates.sort((a, b) -> {
            int byX = Integer.compare(a[0], b[0]);
            if (byX != 0) {
                return byX;
            }
            int byY = Integer.compare(a[1], b[1]);
            return byY != 0 ? byY : Integer.compare(a[2], b[2]);
        });

        List<int[]> picked = new ArrayList<>();
        for (int i = 0; i < wanted && !candidates.isEmpty(); i++) {
            picked.add(candidates.remove(random.nextInt(candidates.size())));
        }
        return picked;
    }

    public boolean isCarved(int dx, int y, int dz) {
        return carved.contains(key(dx, y, dz));
    }

    /** The ore at a position, or {@code null} for plain stone. */
    public Ore oreAt(int dx, int y, int dz) {
        return ores.get(key(dx, y, dz));
    }

    /** Positions for cave loot chests: {dx, y, dz}. */
    public List<int[]> chestSpots() {
        return chestSpots;
    }

    public int carvedCount() {
        return carved.size();
    }

    public int oreCount() {
        return ores.size();
    }

    private static long key(int dx, int y, int dz) {
        return (((long) (dx + 512)) << 42) | (((long) (y + 512)) << 21) | (dz + 512);
    }

    private static int decode(long cell, int shift) {
        return (int) ((cell >>> shift) & 0x1FFFFFL) - 512;
    }
}
