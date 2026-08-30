package pro.qolar.walls.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure integer geometry for a Walls arena.
 *
 * <p>Deliberately free of every Bukkit import so the layout rules - the easiest
 * thing in this plugin to get subtly wrong - can be unit tested without a running
 * server.
 *
 * <p>All coordinates passed in are <em>arena-local</em>: {@code dx} and {@code dz}
 * are offsets from the arena centre, and {@code depth} is measured downward from
 * the grass surface (depth 0 = grass).
 *
 * <h2>Layout</h2>
 * <pre>
 *   +---------------------------+  <- glass shell, one ring outside the platform
 *   |         |       |         |
 *   |    1    |       |    0    |  <- sectors, sealed from one another
 *   |         |       |         |
 *   |---------+-###+--+---------|
 *   |           #   #           |  <- vault shell
 *   |           # C #           |     (C = loot chests, interior stays air)
 *   |---------+-#####+----------|
 *   |         |       |         |
 *   |    2    |       |    3    |
 *   |         |       |         |
 *   +---------------------------+
 * </pre>
 *
 * <p>N arms radiate from the vault to the arena edge, one per sector boundary.
 * At four sectors on a square this is exactly the classic cross; other counts use
 * a circular arena so every sector is the same size.
 *
 * <p>The walls run the <em>full depth</em> of the platform, not just above ground.
 * Anything less and a player simply digs under them.
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

    /** Anything smaller than this is treated as an exact zero in the arm maths. */
    private static final double EPSILON = 1e-9;

    private static final int[][] NEIGHBOURS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private final ArenaShape shape;
    private final int sectorCount;
    private final int radius;
    private final int floorY;
    private final int thickness;
    private final int wallHalf;
    private final int wallHeight;
    private final int vaultRadius;
    private final int glassHeight;

    private final double[] armCos;
    private final double[] armSin;

    public ArenaGeometry(ArenaShape shape, int sectorCount, int radius, int floorY, int thickness,
                         int wallThickness, int wallHeight, int vaultRadius, int glassHeight) {
        if (shape == null) {
            throw new IllegalArgumentException("arena shape is required");
        }
        if (sectorCount < 2) {
            throw new IllegalArgumentException("need at least 2 teams to divide an arena, got " + sectorCount);
        }
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
        // Five is the minimum that leaves room for a build ceiling low enough that
        // a pillar cannot reach the top of the wall. See graceBuildCeilingY().
        if (wallHeight < 5) {
            throw new IllegalArgumentException("wall height must be at least 5, got " + wallHeight);
        }
        int half = (wallThickness - 1) / 2;
        // The vault has to clear the arms, or its shell would be swallowed by them.
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
        // Where the arms leave the vault they are at their closest. If they touch,
        // a sector would be sealed off entirely instead of merely divided.
        double spacingAtVault = (vaultRadius + 1.0) * (2 * Math.PI / sectorCount);
        if (spacingAtVault <= wallThickness + 1.0) {
            throw new IllegalArgumentException(
                    "with " + sectorCount + " teams the walls would touch at the vault edge; "
                            + "raise walls.vault-radius above " + (int) Math.ceil(
                            (wallThickness + 1.0) * sectorCount / (2 * Math.PI)) + " or use fewer teams");
        }

        this.shape = shape;
        this.sectorCount = sectorCount;
        this.radius = radius;
        this.floorY = floorY;
        this.thickness = thickness;
        this.wallHalf = half;
        this.wallHeight = wallHeight;
        this.vaultRadius = vaultRadius;
        this.glassHeight = glassHeight;

        this.armCos = new double[sectorCount];
        this.armSin = new double[sectorCount];
        for (int k = 0; k < sectorCount; k++) {
            double angle = (2 * Math.PI * k) / sectorCount;
            armCos[k] = snap(Math.cos(angle));
            armSin[k] = snap(Math.sin(angle));
        }
    }

    /** Keeps axis-aligned arms exactly axis-aligned despite floating point. */
    private static double snap(double value) {
        return Math.abs(value) < EPSILON ? 0.0 : value;
    }

    public ArenaShape shape() {
        return shape;
    }

    public int sectorCount() {
        return sectorCount;
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

    /** Lowest Y a wall reaches - one above bedrock, which stays untouchable. */
    public int wallBottomY() {
        return bottomY() + 1;
    }

    /** Y of the highest wall block. */
    public int wallTopY() {
        return floorY + wallHeight;
    }

    /** Y of the glass ceiling. */
    public int ceilingY() {
        return floorY + glassHeight;
    }

    /**
     * Highest Y a player may place a block at while the walls stand.
     *
     * <p>Sealing the sectors sideways and underground is not enough on its own -
     * a player can pillar straight up and walk over the wall instead. The wall's
     * walkable surface is {@code wallTopY + 1}, and a player standing on a block
     * at height H stands at H+1 and can jump to H+2, so anything above
     * {@code wallTopY - 3} would put that surface in reach.
     */
    public int graceBuildCeilingY() {
        return wallTopY() - 3;
    }

    /** Chebyshev distance - the metric a square arena is measured in. */
    public static int chebyshev(int dx, int dz) {
        return Math.max(Math.abs(dx), Math.abs(dz));
    }

    /** Distance from the arena centre under this arena's shape. */
    public double distance(int dx, int dz) {
        return shape.distance(dx, dz);
    }

    /** True for columns covered by the platform. */
    public boolean isPlatform(int dx, int dz) {
        return shape.distance(dx, dz) <= radius;
    }

    /**
     * True for the ring of columns forming the outer glass shell: just outside the
     * platform, and touching it.
     *
     * <p>Eight-way adjacency, not four - a four-adjacent ring leaves diagonal gaps.
     */
    public boolean isGlassColumn(int dx, int dz) {
        if (isPlatform(dx, dz)) {
            return false;
        }
        for (int[] step : NEIGHBOURS) {
            if (isPlatform(dx + step[0], dz + step[1])) {
                return true;
            }
        }
        return false;
    }

    /** True for columns under the glass ceiling. */
    public boolean isUnderCeiling(int dx, int dz) {
        return isPlatform(dx, dz) || isGlassColumn(dx, dz);
    }

    /** True inside the vault footprint, shell included. */
    public boolean inVault(int dx, int dz) {
        return shape.distance(dx, dz) <= vaultRadius;
    }

    /** The vault's wall: inside the vault, touching the outside. */
    public boolean isVaultShell(int dx, int dz) {
        if (!inVault(dx, dz)) {
            return false;
        }
        for (int[] step : NEIGHBOURS) {
            if (!inVault(dx + step[0], dz + step[1])) {
                return true;
            }
        }
        return false;
    }

    /** The vault's air pocket, where the loot chests stand. */
    public boolean isVaultInterior(int dx, int dz) {
        return inVault(dx, dz) && !isVaultShell(dx, dz);
    }

    /** True for the vault's roof slab, laid at {@link #wallTopY()}. */
    public boolean isVaultLid(int dx, int dz) {
        return inVault(dx, dz);
    }

    /** True when a column lies on one of the arms radiating out from the vault. */
    private boolean isArm(int dx, int dz) {
        for (int k = 0; k < sectorCount; k++) {
            double projection = dx * armCos[k] + dz * armSin[k];
            if (projection <= 0) {
                continue;
            }
            double perpendicular = Math.abs(dx * armSin[k] - dz * armCos[k]);
            if (perpendicular <= wallHalf + 0.5) {
                return true;
            }
        }
        return false;
    }

    /**
     * The wall predicate - the heart of the layout.
     *
     * <p>Inside the vault footprint only the shell is solid, which keeps the
     * interior (and the chests standing in it) air. Outside it, the arms run to
     * the arena edge.
     */
    public boolean isWallColumn(int dx, int dz) {
        if (!isPlatform(dx, dz)) {
            return false;
        }
        if (inVault(dx, dz)) {
            return isVaultShell(dx, dz);
        }
        return isArm(dx, dz);
    }

    /**
     * Which sector a column belongs to, or {@code -1} for columns that belong to
     * none: outside the arena, on a wall, or inside the vault.
     */
    public int sectorOf(int dx, int dz) {
        if (!isPlatform(dx, dz) || inVault(dx, dz) || isWallColumn(dx, dz)) {
            return -1;
        }
        double theta = Math.atan2(dz, dx);
        if (theta < 0) {
            theta += 2 * Math.PI;
        }
        int index = (int) (theta / (2 * Math.PI / sectorCount));
        return Math.min(index, sectorCount - 1);
    }

    /**
     * Where a sector's base sits: along the bisector between its two arms, at
     * {@code baseOffset} measured in this arena's own distance metric.
     *
     * <p>Measuring in the arena's metric is what keeps a square four-team arena's
     * bases exactly on the diagonal at (±offset, ±offset), unchanged from before
     * radial layouts existed.
     */
    public int[] basePoint(int sector, int baseOffset) {
        double angle = ((sector + 0.5) * 2 * Math.PI) / sectorCount;
        double ux = snap(Math.cos(angle));
        double uz = snap(Math.sin(angle));
        double scale = baseOffset / shape.distance(ux, uz);
        return new int[]{(int) Math.round(ux * scale), (int) Math.round(uz * scale)};
    }

    /**
     * True when a column is open ground a player can stand on with the walls up:
     * inside the arena, not a wall, not the vault.
     */
    public boolean isOpenGround(int dx, int dz) {
        return isPlatform(dx, dz) && !isWallColumn(dx, dz) && !inVault(dx, dz);
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

    /** True for the stone body of the platform - the only place caves may carve. */
    public boolean isStoneDepth(int y) {
        return y > bottomY() && y <= floorY && layerAtY(y) == Layer.STONE;
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
