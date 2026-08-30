package pro.qolar.walls.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure integer geometry for a Walls arena.
 *
 * <p>Deliberately free of every Bukkit import so the layout rules - which are the
 * easiest thing in this plugin to get subtly wrong - can be unit tested without a
 * running server.
 *
 * <p>All coordinates passed in are <em>arena-local</em>: {@code dx} and {@code dz}
 * are offsets from the arena centre, and {@code depth} is measured downward from
 * the grass surface (depth 0 = grass).
 *
 * <h2>Layout</h2>
 * <pre>
 *   +---------------------------+  <- glass shell at cheb == radius + 1
 *   |         |       |         |
 *   |    2    |       |    1    |  <- quadrants, sealed from one another
 *   |         |       |         |
 *   |---------+-###+--+---------|
 *   |           #   #           |  <- vault shell ring at cheb == vaultRadius
 *   |           # C #           |     (C = loot chests, interior stays air)
 *   |---------+-#####+----------|
 *   |         |       |         |
 *   |    3    |       |    0    |
 *   |         |       |         |
 *   +---------------------------+
 * </pre>
 *
 * The four arms radiating from the vault to the arena edge are "the walls"; each
 * quadrant is closed by two arms, the outer glass, and the vault's corner.
 */
public final class ArenaGeometry {

    /** A course of blocks in the platform, top-down. */
    public enum Layer {
        GRASS,
        DIRT,
        STONE,
        BEDROCK
    }

    private static final int GRASS_DEPTH = 1;
    private static final int DIRT_DEPTH = 4;
    private static final int BEDROCK_DEPTH = 1;

    /** Courses that are not stone. Stone fills whatever remains. */
    public static final int FIXED_LAYER_DEPTH = GRASS_DEPTH + DIRT_DEPTH + BEDROCK_DEPTH;

    private final int radius;
    private final int floorY;
    private final int thickness;
    private final int wallHalf;
    private final int wallHeight;
    private final int vaultRadius;
    private final int glassHeight;

    public ArenaGeometry(int radius, int floorY, int thickness, int wallThickness,
                         int wallHeight, int vaultRadius, int glassHeight) {
        if (radius < 16) {
            throw new IllegalArgumentException("arena radius must be at least 16, got " + radius);
        }
        if (thickness <= FIXED_LAYER_DEPTH) {
            throw new IllegalArgumentException(
                    "platform thickness must exceed " + FIXED_LAYER_DEPTH + " to leave room for stone, got " + thickness);
        }
        if (wallThickness < 1 || wallThickness % 2 == 0) {
            throw new IllegalArgumentException("wall thickness must be a positive odd number, got " + wallThickness);
        }
        if (wallHeight < 2) {
            throw new IllegalArgumentException("wall height must be at least 2, got " + wallHeight);
        }
        // The vault has to clear the arms, or its ring would be swallowed by them.
        int half = (wallThickness - 1) / 2;
        if (vaultRadius <= half + 1) {
            throw new IllegalArgumentException(
                    "vault radius must exceed half the wall thickness + 1 (" + (half + 1) + "), got " + vaultRadius);
        }
        if (vaultRadius >= radius) {
            throw new IllegalArgumentException("vault radius must be smaller than the arena radius");
        }
        if (glassHeight <= wallHeight) {
            throw new IllegalArgumentException("glass height must exceed wall height so walls cannot be climbed out of");
        }
        this.radius = radius;
        this.floorY = floorY;
        this.thickness = thickness;
        this.wallHalf = half;
        this.wallHeight = wallHeight;
        this.vaultRadius = vaultRadius;
        this.glassHeight = glassHeight;
    }

    public int radius() {
        return radius;
    }

    public int vaultRadius() {
        return vaultRadius;
    }

    public int thickness() {
        return thickness;
    }

    /** Y of the grass surface. */
    public int floorY() {
        return floorY;
    }

    /** Y of the bedrock course. */
    public int bottomY() {
        return floorY - thickness + 1;
    }

    /** Y of the highest wall block. */
    public int wallTopY() {
        return floorY + wallHeight;
    }

    /** Y of the glass ceiling. */
    public int ceilingY() {
        return floorY + glassHeight;
    }

    /** Chebyshev distance from the arena centre - the natural metric for a square arena. */
    public static int chebyshev(int dx, int dz) {
        return Math.max(Math.abs(dx), Math.abs(dz));
    }

    /** True for columns covered by the platform. */
    public boolean isPlatform(int dx, int dz) {
        return chebyshev(dx, dz) <= radius;
    }

    /** True for the single ring of columns forming the outer glass shell. */
    public boolean isGlassColumn(int dx, int dz) {
        return chebyshev(dx, dz) == radius + 1;
    }

    /** True for columns under the glass ceiling. */
    public boolean isUnderCeiling(int dx, int dz) {
        return chebyshev(dx, dz) <= radius + 1;
    }

    /**
     * The wall predicate - the heart of the layout.
     *
     * <p>Inside the vault footprint only the shell ring is solid, which keeps the
     * interior (and the chests standing in it) air. Outside it, the four arms run
     * to the arena edge.
     */
    public boolean isWallColumn(int dx, int dz) {
        int cheb = chebyshev(dx, dz);
        if (cheb > radius) {
            return false;
        }
        if (cheb <= vaultRadius) {
            return cheb == vaultRadius;
        }
        return Math.abs(dx) <= wallHalf || Math.abs(dz) <= wallHalf;
    }

    /** True for the vault's roof slab, laid at {@link #wallTopY()}. */
    public boolean isVaultLid(int dx, int dz) {
        return chebyshev(dx, dz) <= vaultRadius;
    }

    /** True for the vault's air pocket, where the loot chests stand. */
    public boolean isVaultInterior(int dx, int dz) {
        return chebyshev(dx, dz) < vaultRadius;
    }

    /**
     * True when a column is open ground a player can stand on once the walls are
     * up: inside the arena, not a wall, not the vault.
     */
    public boolean isOpenGround(int dx, int dz) {
        return isPlatform(dx, dz) && !isWallColumn(dx, dz) && !isVaultInterior(dx, dz);
    }

    /** Which course sits at {@code depth} blocks below the grass surface. */
    public Layer layerAt(int depth) {
        if (depth < 0 || depth >= thickness) {
            throw new IllegalArgumentException("depth " + depth + " outside platform of thickness " + thickness);
        }
        if (depth < GRASS_DEPTH) {
            return Layer.GRASS;
        }
        if (depth < GRASS_DEPTH + DIRT_DEPTH) {
            return Layer.DIRT;
        }
        if (depth >= thickness - BEDROCK_DEPTH) {
            return Layer.BEDROCK;
        }
        return Layer.STONE;
    }

    /** Convenience: the course at absolute world height {@code y}. */
    public Layer layerAtY(int y) {
        return layerAt(floorY - y);
    }

    /**
     * Evenly spaced chest positions on the square ring at {@code ringRadius},
     * walked clockwise from the north-west corner.
     */
    public List<int[]> chestOffsets(int count, int ringRadius) {
        if (ringRadius < 1 || ringRadius >= vaultRadius) {
            throw new IllegalArgumentException("chest ring must sit inside the vault, got radius " + ringRadius);
        }
        List<int[]> ring = squareRing(ringRadius);
        List<int[]> picked = new ArrayList<>();
        if (count <= 0) {
            return picked;
        }
        for (int i = 0; i < count && i < ring.size(); i++) {
            picked.add(ring.get((int) ((long) i * ring.size() / count)));
        }
        return picked;
    }

    /** The columns forming a square ring at Chebyshev distance {@code r}, in walk order. */
    public static List<int[]> squareRing(int r) {
        List<int[]> ring = new ArrayList<>();
        for (int dx = -r; dx < r; dx++) {
            ring.add(new int[]{dx, -r});
        }
        for (int dz = -r; dz < r; dz++) {
            ring.add(new int[]{r, dz});
        }
        for (int dx = r; dx > -r; dx--) {
            ring.add(new int[]{dx, r});
        }
        for (int dz = r; dz > -r; dz--) {
            ring.add(new int[]{-r, dz});
        }
        return ring;
    }
}
