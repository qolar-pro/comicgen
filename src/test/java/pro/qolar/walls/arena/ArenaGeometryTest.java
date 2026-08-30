package pro.qolar.walls.arena;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arena layout rules - the easiest thing here to get subtly and invisibly wrong. */
class ArenaGeometryTest {

    // The shipped defaults.
    private static final int RADIUS = 60;
    private static final int FLOOR_Y = 64;
    private static final int THICKNESS = 50;
    private static final int WALL_THICKNESS = 3;
    private static final int WALL_HEIGHT = 14;
    private static final int VAULT_RADIUS = 6;
    private static final int GLASS_HEIGHT = 40;

    private static ArenaGeometry geometry() {
        return defaults(ArenaShape.SQUARE, 4);
    }

    private static ArenaGeometry defaults(ArenaShape shape, int teams) {
        return new ArenaGeometry(shape, teams, RADIUS, FLOOR_Y, THICKNESS,
                WALL_THICKNESS, WALL_HEIGHT, VAULT_RADIUS, GLASS_HEIGHT);
    }

    /** A smaller arena, so the exhaustive 3D checks stay quick. */
    private static ArenaGeometry small(ArenaShape shape, int teams) {
        return new ArenaGeometry(shape, teams, 24, 64, 20, 3, 8, 6, 20);
    }

    // --- the layer stack -------------------------------------------------

    @Test
    void layerStackCoversExactlyTheStatedThickness() {
        ArenaGeometry g = geometry();
        int grass = 0;
        int dirt = 0;
        int stone = 0;
        int bedrock = 0;
        for (int depth = 0; depth < THICKNESS; depth++) {
            switch (g.layerAt(depth)) {
                case GRASS -> grass++;
                case DIRT -> dirt++;
                case STONE -> stone++;
                case BEDROCK -> bedrock++;
            }
        }
        assertEquals(1, grass, "one grass course");
        assertEquals(4, dirt, "four dirt courses");
        assertEquals(1, bedrock, "one bedrock course, at the very bottom");
        assertEquals(THICKNESS - ArenaGeometry.FIXED_LAYER_DEPTH, stone, "stone fills the rest");
        assertEquals(THICKNESS, grass + dirt + stone + bedrock);
    }

    @Test
    void grassIsOnTopAndBedrockIsOnTheBottom() {
        ArenaGeometry g = geometry();
        assertEquals(ArenaGeometry.Layer.GRASS, g.layerAtY(g.floorY()));
        assertEquals(ArenaGeometry.Layer.BEDROCK, g.layerAtY(g.bottomY()));
        assertEquals(ArenaGeometry.Layer.STONE, g.layerAtY(g.floorY() - 10));
        assertEquals(THICKNESS, g.floorY() - g.bottomY() + 1, "platform is exactly as thick as configured");
        assertEquals(g.bottomY() + 1, g.wallBottomY(), "walls start one course above bedrock");
    }

    @Test
    void depthOutsideThePlatformIsRejected() {
        ArenaGeometry g = geometry();
        assertThrows(IllegalArgumentException.class, () -> g.layerAt(-1));
        assertThrows(IllegalArgumentException.class, () -> g.layerAt(THICKNESS));
    }

    // --- the four-team square arena must not have moved -------------------

    /**
     * The radial arm formula has to reproduce the original axis-aligned cross
     * exactly, or the arena people already played on has silently changed shape.
     */
    @Test
    void fourTeamsOnASquareIsStillTheOriginalCross() {
        ArenaGeometry g = geometry();
        int half = (WALL_THICKNESS - 1) / 2;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int cheb = ArenaGeometry.chebyshev(dx, dz);
                boolean original = cheb <= VAULT_RADIUS
                        ? cheb == VAULT_RADIUS
                        : Math.abs(dx) <= half || Math.abs(dz) <= half;
                assertEquals(original, g.isWallColumn(dx, dz),
                        "wall predicate changed at (" + dx + "," + dz + ")");
            }
        }
    }

    @Test
    void thereAreExactlyFourArmsReachingTheArenaEdge() {
        ArenaGeometry g = geometry();
        List<int[]> edge = ArenaGeometry.squareRing(RADIUS);
        int wallColumns = 0;
        int runs = 0;
        int[] last = edge.get(edge.size() - 1);
        boolean previous = g.isWallColumn(last[0], last[1]);
        for (int[] c : edge) {
            boolean wall = g.isWallColumn(c[0], c[1]);
            if (wall) {
                wallColumns++;
                if (!previous) {
                    runs++;
                }
            }
            previous = wall;
        }
        assertEquals(4, runs, "four separate arms should touch the arena edge");
        assertEquals(4 * WALL_THICKNESS, wallColumns, "each arm is the configured thickness wide");
    }

    @Test
    void vaultShellIsSolidAndItsInteriorIsAir() {
        ArenaGeometry g = geometry();
        for (int[] c : ArenaGeometry.squareRing(VAULT_RADIUS)) {
            assertTrue(g.isWallColumn(c[0], c[1]),
                    "vault shell column (" + c[0] + "," + c[1] + ") must be solid");
        }
        for (int dx = -VAULT_RADIUS + 1; dx <= VAULT_RADIUS - 1; dx++) {
            for (int dz = -VAULT_RADIUS + 1; dz <= VAULT_RADIUS - 1; dz++) {
                assertFalse(g.isWallColumn(dx, dz),
                        "vault interior (" + dx + "," + dz + ") must stay air so the chests survive /walls down");
                assertTrue(g.isVaultInterior(dx, dz));
            }
        }
    }

    @Test
    void chestsStandInsideTheVaultAndDoNotOverlap() {
        ArenaGeometry g = geometry();
        List<int[]> chests = g.chestOffsets(8, 3);
        assertEquals(8, chests.size());
        Set<String> seen = new HashSet<>();
        for (int[] c : chests) {
            assertTrue(g.isVaultInterior(c[0], c[1]),
                    "chest at (" + c[0] + "," + c[1] + ") must sit in the vault's air pocket");
            assertFalse(g.isWallColumn(c[0], c[1]), "a chest must never be inside a wall block");
            assertTrue(seen.add(c[0] + ":" + c[1]), "chests must not stack on one another");
        }
    }

    @Test
    void chestRingMustFitInsideTheVault() {
        ArenaGeometry g = geometry();
        assertThrows(IllegalArgumentException.class, () -> g.chestOffsets(8, VAULT_RADIUS));
    }

    // --- sealing across the surface --------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6, 8})
    void sectorsAreSealedOnASquareArena(int teams) {
        assertSectorsSealed(small(ArenaShape.SQUARE, teams));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6, 8})
    void sectorsAreSealedOnACircularArena(int teams) {
        assertSectorsSealed(small(ArenaShape.CIRCLE, teams));
    }

    private static void assertSectorsSealed(ArenaGeometry g) {
        SectorLayout layout = SectorLayout.forGeometry(g, g.radius() / 2);
        for (Sector sector : layout.all()) {
            Set<Long> reached = floodFillOpenGround(g, sector.baseDx(), sector.baseDz());
            assertFalse(reached.isEmpty(), "flood fill should start on open ground");
            for (long key : reached) {
                int dx = (int) (key >> 32);
                int dz = (int) key;
                assertFalse(g.inVault(dx, dz),
                        "sector " + sector.index() + " leaked into the loot vault at (" + dx + "," + dz + ")");
                assertEquals(sector.index(), g.sectorOf(dx, dz),
                        "sector " + sector.index() + " leaked at (" + dx + "," + dz + ")");
            }
        }
    }

    @Test
    void everySectorOfACircularArenaIsTheSameSize() {
        ArenaGeometry g = small(ArenaShape.CIRCLE, 5);
        SectorLayout layout = SectorLayout.forGeometry(g, g.radius() / 2);
        List<Integer> sizes = new ArrayList<>();
        for (Sector sector : layout.all()) {
            sizes.add(floodFillOpenGround(g, sector.baseDx(), sector.baseDz()).size());
        }
        int smallest = sizes.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int largest = sizes.stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertTrue(smallest > 100, "each sector should be a real playing space, got " + smallest);
        // Block grids cannot divide a circle perfectly, but they should be close.
        assertTrue(largest - smallest <= largest * 0.06,
                "circular sectors should be near-equal, got " + sizes);
    }

    @Test
    void everyQuadrantOfTheSquareArenaIsExactlyTheSameSize() {
        ArenaGeometry g = geometry();
        SectorLayout layout = SectorLayout.forGeometry(g, RADIUS / 2);
        int first = floodFillOpenGround(g, layout.get(0).baseDx(), layout.get(0).baseDz()).size();
        assertTrue(first > 1000, "a quadrant should be a real playing space, got " + first);
        for (Sector sector : layout.all()) {
            assertEquals(first, floodFillOpenGround(g, sector.baseDx(), sector.baseDz()).size(),
                    "all four quadrants should be identical on a square");
        }
    }

    /** Four-way flood fill across columns that are open ground with the walls up. */
    private static Set<Long> floodFillOpenGround(ArenaGeometry g, int startDx, int startDz) {
        Set<Long> seen = new HashSet<>();
        if (!g.isOpenGround(startDx, startDz)) {
            return seen;
        }
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startDx, startDz});
        seen.add(key(startDx, startDz));
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int nz = at[1] + step[1];
                if (!g.isOpenGround(nx, nz) || !seen.add(key(nx, nz))) {
                    continue;
                }
                queue.add(new int[]{nx, nz});
            }
        }
        return seen;
    }

    private static long key(int dx, int dz) {
        return ((long) dx << 32) | (dz & 0xffffffffL);
    }

    // --- sealing underground: the defect this test exists to catch ---------

    /**
     * Surface walls are not enough. Players can mine, so anything that is not
     * protected is a route - including the fifty blocks of dirt and stone beneath
     * a wall. This fills through every diggable cell in three dimensions and
     * insists a sector still cannot reach its neighbours.
     */
    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void sectorsAreSealedUndergroundToo(int teams) {
        ArenaGeometry g = small(ArenaShape.defaultFor(teams), teams);
        SectorLayout layout = SectorLayout.forGeometry(g, g.radius() / 2);
        Sector start = layout.get(0);

        Set<Long> reached = floodFillDiggable(g, start.baseDx(), start.baseDz(), g.wallBottomY());
        for (long cell : reached) {
            int dx = decodeX(cell);
            int dz = decodeZ(cell);
            int sector = g.sectorOf(dx, dz);
            assertFalse(sector >= 0 && sector != start.index(),
                    "dug from sector 0 into sector " + sector + " at (" + dx + "," + dz + ")");
            assertFalse(g.isVaultInterior(dx, dz),
                    "dug into the loot vault at (" + dx + "," + dz + ")");
        }
    }

    /**
     * Proves the check above has teeth: model walls that stop at the surface -
     * the way they originally did - and the very same fill escapes underground.
     */
    @Test
    void surfaceOnlyWallsWouldLeakUnderground() {
        ArenaGeometry g = small(ArenaShape.SQUARE, 4);
        SectorLayout layout = SectorLayout.forGeometry(g, g.radius() / 2);
        Sector start = layout.get(0);

        // Walls beginning above the floor leave the whole platform open beneath.
        Set<Long> reached = floodFillDiggable(g, start.baseDx(), start.baseDz(), g.floorY() + 1);
        boolean leaked = reached.stream().anyMatch(cell -> {
            int sector = g.sectorOf(decodeX(cell), decodeZ(cell));
            return sector >= 0 && sector != start.index();
        });
        assertTrue(leaked, "surface-only walls should be tunnelled under - if not, this test proves nothing");
    }

    /**
     * Flood fill through every cell a player could reach by digging: anything that
     * is not bedrock, glass, ceiling, or a standing wall block.
     *
     * @param wallFromY lowest Y at which walls are solid
     */
    private static Set<Long> floodFillDiggable(ArenaGeometry g, int startDx, int startDz, int wallFromY) {
        Set<Long> seen = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        int[] origin = {startDx, g.floorY() + 1, startDz};
        queue.add(origin);
        seen.add(cellKey(origin[0], origin[1], origin[2]));

        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                int nz = at[2] + step[2];
                if (!diggable(g, nx, ny, nz, wallFromY) || !seen.add(cellKey(nx, ny, nz))) {
                    continue;
                }
                queue.add(new int[]{nx, ny, nz});
            }
        }
        return seen;
    }

    private static boolean diggable(ArenaGeometry g, int dx, int y, int dz, int wallFromY) {
        // Above the wall top is not a route while the walls stand: the grace
        // build ceiling keeps players from pillaring up to it.
        if (y <= g.bottomY() || y > g.wallTopY()) {
            return false;
        }
        if (!g.isPlatform(dx, dz)) {
            return false;
        }
        boolean wall = y >= wallFromY && y <= g.wallTopY()
                && (g.isWallColumn(dx, dz) || (g.isVaultInterior(dx, dz) && y == g.wallTopY()));
        return !wall;
    }

    private static long cellKey(int dx, int y, int dz) {
        return (((long) (dx + 512)) << 42) | (((long) (y + 512)) << 21) | (dz + 512);
    }

    private static int decodeX(long cell) {
        return (int) (cell >>> 42) - 512;
    }

    private static int decodeZ(long cell) {
        return (int) (cell & 0x1FFFFFL) - 512;
    }

    /**
     * The build ceiling has to sit far enough below the wall that a pillar cannot
     * reach its walkable top. A player on a block at H stands at H+1 and jumps to
     * H+2; the wall's surface is wallTopY + 1.
     */
    @Test
    void theGraceBuildCeilingPutsTheWallTopOutOfReach() {
        for (int teams : new int[]{3, 4, 5}) {
            ArenaGeometry g = small(ArenaShape.defaultFor(teams), teams);
            int highestBlock = g.graceBuildCeilingY();
            int highestReach = highestBlock + 2;
            int wallSurface = g.wallTopY() + 1;
            assertTrue(highestReach < wallSurface,
                    "a pillar to " + highestBlock + " reaches " + highestReach
                            + ", which must stay below the wall surface at " + wallSurface);
            assertTrue(highestBlock > g.floorY(), "players still need room to build");
        }
    }

    // --- sectors and bases -----------------------------------------------

    @Test
    void eachBaseSitsOnOpenGroundInsideItsOwnSector() {
        for (int teams : new int[]{3, 4, 5, 6}) {
            ArenaGeometry g = small(ArenaShape.defaultFor(teams), teams);
            SectorLayout layout = SectorLayout.forGeometry(g, g.radius() / 2);
            Set<String> seen = new HashSet<>();
            for (Sector sector : layout.all()) {
                assertTrue(g.isOpenGround(sector.baseDx(), sector.baseDz()),
                        teams + " teams: base " + sector.index() + " must sit on open ground");
                assertEquals(sector.index(), g.sectorOf(sector.baseDx(), sector.baseDz()),
                        teams + " teams: base " + sector.index() + " must be inside its own sector");
                assertTrue(seen.add(sector.baseDx() + ":" + sector.baseDz()),
                        teams + " teams: two bases share a spot");
            }
        }
    }

    @Test
    void squareFourTeamBasesStayOnTheDiagonal() {
        ArenaGeometry g = geometry();
        SectorLayout layout = SectorLayout.forGeometry(g, 30);
        assertEquals(30, layout.get(0).baseDx());
        assertEquals(30, layout.get(0).baseDz());
        assertEquals(-30, layout.get(1).baseDx());
        assertEquals(30, layout.get(1).baseDz());
        assertEquals(-30, layout.get(2).baseDx());
        assertEquals(-30, layout.get(2).baseDz());
        assertEquals(30, layout.get(3).baseDx());
        assertEquals(-30, layout.get(3).baseDz());
    }

    @Test
    void columnsOnAWallBelongToNoSector() {
        ArenaGeometry g = geometry();
        SectorLayout layout = SectorLayout.forGeometry(g, 30);
        assertEquals(-1, g.sectorOf(0, 20), "on an arm");
        assertEquals(-1, g.sectorOf(20, 0), "on an arm");
        assertEquals(-1, g.sectorOf(0, 0), "in the vault");
        assertEquals(null, layout.at(0, 20));
        assertNotNull(layout.at(30, 30));
    }

    // --- configuration guards --------------------------------------------

    @Test
    void nonsenseConfigurationIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> defaults(ArenaShape.SQUARE, 1), "one team is not a match");
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaGeometry(ArenaShape.SQUARE, 4, 4, 64, 50, 3, 14, 6, 40), "tiny radius");
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaGeometry(ArenaShape.SQUARE, 4, 60, 64, 5, 3, 14, 6, 40), "platform too thin");
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaGeometry(ArenaShape.SQUARE, 4, 60, 64, 50, 4, 14, 6, 40), "even wall thickness");
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaGeometry(ArenaShape.SQUARE, 4, 60, 64, 50, 3, 14, 2, 40), "vault swallowed by arms");
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaGeometry(ArenaShape.SQUARE, 4, 60, 64, 50, 3, 14, 6, 10), "glass below the walls");
    }

    @Test
    void tooManyTeamsForTheVaultIsRejectedWithAdvice() {
        // Twenty arms leaving a vault of radius six would merge into a solid disc.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> defaults(ArenaShape.CIRCLE, 20));
        assertTrue(thrown.getMessage().contains("vault-radius"),
                "the error should say how to fix it, got: " + thrown.getMessage());
    }

    @Test
    void theGlassShellWrapsTheWholePlatform() {
        ArenaGeometry g = geometry();
        assertTrue(g.isGlassColumn(RADIUS + 1, 0));
        assertTrue(g.isGlassColumn(RADIUS + 1, RADIUS + 1));
        assertFalse(g.isGlassColumn(RADIUS, 0), "the glass stands outside the platform, not on it");
        assertFalse(g.isPlatform(RADIUS + 1, 0));
        assertTrue(g.isUnderCeiling(RADIUS + 1, RADIUS + 1));
        assertFalse(g.isUnderCeiling(RADIUS + 2, 0));
        assertTrue(g.ceilingY() > g.wallTopY(), "the ceiling must clear the walls");
    }

    /** A circular shell must not leave a diagonal gap a player could slip through. */
    @Test
    void theCircularGlassShellHasNoGaps() {
        ArenaGeometry g = small(ArenaShape.CIRCLE, 5);
        int reach = g.radius() + 4;
        Set<Long> outside = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{reach, reach});
        outside.add(key(reach, reach));
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int nz = at[1] + step[1];
                if (Math.abs(nx) > reach || Math.abs(nz) > reach) {
                    continue;
                }
                if (g.isGlassColumn(nx, nz) || g.isPlatform(nx, nz) || !outside.add(key(nx, nz))) {
                    continue;
                }
                queue.add(new int[]{nx, nz});
            }
        }
        for (int dx = -g.radius(); dx <= g.radius(); dx++) {
            for (int dz = -g.radius(); dz <= g.radius(); dz++) {
                if (g.isPlatform(dx, dz)) {
                    assertFalse(outside.contains(key(dx, dz)), "the outside reached the platform");
                }
            }
        }
    }

    // --- size --------------------------------------------------------------

    /**
     * Guards against silent drift in the arena's size. A live server building the
     * default arena reported exactly 800,476 block placements, and the count is
     * unchanged by walls running the full depth: a wall column held fifty layers
     * plus fourteen wall blocks before, and one bedrock plus sixty-three now.
     */
    @Test
    void theArenaIsExactlyTheSizeWeThinkItIs() {
        ArenaGeometry g = geometry();

        int platformColumns = 0;
        int wallColumns = 0;
        int vaultShellColumns = 0;
        int vaultInteriorColumns = 0;
        int glassColumns = 0;
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                if (g.isGlassColumn(dx, dz)) {
                    glassColumns++;
                } else if (g.isPlatform(dx, dz)) {
                    platformColumns++;
                    if (g.isWallColumn(dx, dz)) {
                        wallColumns++;
                        if (g.isVaultShell(dx, dz)) {
                            vaultShellColumns++;
                        }
                    } else if (g.isVaultInterior(dx, dz)) {
                        vaultInteriorColumns++;
                    }
                }
            }
        }

        assertEquals(121 * 121, platformColumns, "a 121x121 playing surface");
        assertEquals(8 * VAULT_RADIUS, vaultShellColumns, "the vault shell is one square ring");
        assertEquals(4 * WALL_THICKNESS * (RADIUS - VAULT_RADIUS), wallColumns - vaultShellColumns,
                "four arms of the configured width reaching the edge");
        assertEquals(696, wallColumns);
        assertEquals(123 * 123 - 121 * 121, glassColumns, "one ring of glass around the platform");

        int plainColumns = platformColumns - wallColumns - vaultInteriorColumns;
        int wallSpan = g.wallTopY() - g.wallBottomY() + 1;
        int total = plainColumns * THICKNESS                       // layers
                + vaultInteriorColumns * (THICKNESS + 1)           // layers plus the lid
                + wallColumns * (1 + wallSpan)                     // bedrock plus wall
                + glassColumns * (g.ceilingY() - g.bottomY() + 1)  // the shell
                + platformColumns;                                 // the ceiling
        assertEquals(800_476, total, "a live server placed exactly this many blocks");
    }
}
