package pro.qolar.walls.arena;

/**
 * A precomputed classification of every column in an arena.
 *
 * <p>Building an arena asks "what belongs at this position?" well over a million
 * times. Answering that from {@link ArenaGeometry} each time means trigonometry
 * across every arm plus eight-neighbour adjacency checks - far too slow to do per
 * block. The classification only depends on the column though, so it is computed
 * once into a flat array and then read in constant time.
 */
public final class ColumnMap {

    /** What a column is, which decides the whole stack of blocks in it. */
    public enum Column {
        /** Beyond the arena; leave whatever is there alone. */
        OUTSIDE,
        /** Ordinary ground: the layer stack, open air above. */
        PLATFORM,
        /** Part of a wall: solid from just above bedrock to the top of the wall. */
        WALL,
        /** The vault's air pocket, roofed by the lid. */
        VAULT_INTERIOR,
        /** The outer glass shell. */
        GLASS
    }

    private static final Column[] VALUES = Column.values();

    private final int reach;
    private final int size;
    private final byte[] cells;

    private ColumnMap(int reach, int size, byte[] cells) {
        this.reach = reach;
        this.size = size;
        this.cells = cells;
    }

    /** Classify every column of {@code geometry}, glass shell included. */
    public static ColumnMap of(ArenaGeometry geometry) {
        int reach = geometry.radius() + 1;
        int size = 2 * reach + 1;
        byte[] cells = new byte[size * size];

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                cells[(dx + reach) * size + (dz + reach)] = (byte) classify(geometry, dx, dz).ordinal();
            }
        }
        return new ColumnMap(reach, size, cells);
    }

    private static Column classify(ArenaGeometry geometry, int dx, int dz) {
        if (geometry.isGlassColumn(dx, dz)) {
            return Column.GLASS;
        }
        if (!geometry.isPlatform(dx, dz)) {
            return Column.OUTSIDE;
        }
        if (geometry.isWallColumn(dx, dz)) {
            return Column.WALL;
        }
        if (geometry.isVaultInterior(dx, dz)) {
            return Column.VAULT_INTERIOR;
        }
        return Column.PLATFORM;
    }

    public Column at(int dx, int dz) {
        if (dx < -reach || dx > reach || dz < -reach || dz > reach) {
            return Column.OUTSIDE;
        }
        return VALUES[cells[(dx + reach) * size + (dz + reach)]];
    }

    /** How far out the map extends from the centre, inclusive. */
    public int reach() {
        return reach;
    }
}
