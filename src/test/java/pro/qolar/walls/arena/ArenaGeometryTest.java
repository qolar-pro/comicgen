package pro.qolar.walls.arena;

import org.junit.jupiter.api.Test;

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

    private static final int RADIUS = 60;
    private static final int FLOOR_Y = 64;
    private static final int THICKNESS = 50;
    private static final int WALL_THICKNESS = 3;
    private static final int WALL_HEIGHT = 14;
    private static final int VAULT_RADIUS = 6;
    private static final int GLASS_HEIGHT = 40;

    private static ArenaGeometry geometry() {
        return new ArenaGeometry(RADIUS, FLOOR_Y, THICKNESS, WALL_THICKNESS,
                WALL_HEIGHT, VAULT_RADIUS, GLASS_HEIGHT);
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
    }

    @Test
    void depthOutsideThePlatformIsRejected() {
        ArenaGeometry g = geometry();
        assertThrows(IllegalArgumentException.class, () -> g.layerAt(-1));
        assertThrows(IllegalArgumentException.class, () -> g.layerAt(THICKNESS));
    }

    // --- the walls -------------------------------------------------------

    @Test
    void thereAreExactlyFourArmsReachingTheArenaEdge() {
        ArenaGeometry g = geometry();
        List<int[]> edge = ArenaGeometry.squareRing(RADIUS);
        int wallColumns = 0;
        int runs = 0;
        boolean previous = g.isWallColumn(edge.get(edge.size() - 1)[0], edge.get(edge.size() - 1)[1]);
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
    void vaultRingIsSolidAndItsInteriorIsAir() {
        ArenaGeometry g = geometry();
        for (int[] c : ArenaGeometry.squareRing(VAULT_RADIUS)) {
            assertTrue(g.isWallColumn(c[0], c[1]),
                    "vault ring column (" + c[0] + "," + c[1] + ") must be solid");
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

    /**
     * Guards against silent drift in the arena's size. These counts are derived by
     * hand from the default settings, and a live server building this arena
     * reported exactly 800,476 block placements - so if this test starts failing,
     * the shape of the map has changed.
     */
    @Test
    void theArenaIsExactlyTheSizeWeThinkItIs() {
        ArenaGeometry g = geometry();

        int platformColumns = 0;
        int wallColumns = 0;
        int vaultRingColumns = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (g.isPlatform(dx, dz)) {
                    platformColumns++;
                }
                if (g.isWallColumn(dx, dz)) {
                    wallColumns++;
                    if (ArenaGeometry.chebyshev(dx, dz) == VAULT_RADIUS) {
                        vaultRingColumns++;
                    }
                }
            }
        }

        assertEquals(121 * 121, platformColumns, "a 121x121 playing surface");
        assertEquals(8 * VAULT_RADIUS, vaultRingColumns, "the vault ring is one square ring");
        // Four arms, each WALL_THICKNESS wide, running from the vault edge (7) to
        // the arena edge (60): 4 * 3 * 54 = 648.
        assertEquals(4 * WALL_THICKNESS * (RADIUS - VAULT_RADIUS), wallColumns - vaultRingColumns,
                "four arms of the configured width reaching the edge");
        assertEquals(696, wallColumns);

        int glassColumns = 0;
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                if (g.isGlassColumn(dx, dz)) {
                    glassColumns++;
                }
            }
        }
        assertEquals(123 * 123 - 121 * 121, glassColumns, "one ring of glass around the platform");

        // Total placements, matching what the server actually reported.
        int wallHeight = g.wallTopY() - g.floorY();
        int lidExtra = (2 * VAULT_RADIUS + 1) * (2 * VAULT_RADIUS + 1) - vaultRingColumns;
        int total = platformColumns * THICKNESS
                + glassColumns * (g.ceilingY() - g.bottomY())
                + 123 * 123
                + wallColumns * wallHeight + lidExtra;
        assertEquals(800_476, total, "a live server placed exactly this many blocks");
    }

    // --- sealing: the property the whole minigame depends on -------------

    /**
     * With the walls up, a player in one quadrant must not be able to walk to any
     * other quadrant or into the loot vault. Flood fills the open ground from each
     * base and asserts the reachable set never leaves its own sector.
     */
    @Test
    void quadrantsAreSealedFromEachOtherAndFromTheVault() {
        ArenaGeometry g = geometry();
        SectorLayout layout = SectorLayout.forTeams(4, RADIUS / 2);

        for (Sector sector : layout.all()) {
            Set<Long> reached = floodFillOpenGround(g, sector.baseDx(), sector.baseDz());
            assertFalse(reached.isEmpty(), "flood fill should start on open ground");

            for (long key : reached) {
                int dx = (int) (key >> 32);
                int dz = (int) key;
                assertFalse(g.isVaultInterior(dx, dz),
                        "sector " + sector.index() + " leaked into the loot vault at (" + dx + "," + dz + ")");
                Sector owner = layout.at(dx, dz);
                assertNotNull(owner, "reached a column on a wall axis at (" + dx + "," + dz + ")");
                assertEquals(sector.index(), owner.index(),
                        "sector " + sector.index() + " leaked into sector " + owner.index()
                                + " at (" + dx + "," + dz + ")");
            }
        }
    }

    @Test
    void everyQuadrantGetsAFairShareOfOpenGround() {
        ArenaGeometry g = geometry();
        SectorLayout layout = SectorLayout.forTeams(4, RADIUS / 2);
        int first = floodFillOpenGround(g, layout.get(0).baseDx(), layout.get(0).baseDz()).size();
        assertTrue(first > 1000, "a quadrant should be a real playing space, got " + first + " columns");
        for (Sector sector : layout.all()) {
            int size = floodFillOpenGround(g, sector.baseDx(), sector.baseDz()).size();
            assertEquals(first, size, "all four quadrants should be the same size");
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

    // --- sectors ---------------------------------------------------------

    @Test
    void eachBaseSitsOnOpenGroundInsideItsOwnQuadrant() {
        ArenaGeometry g = geometry();
        SectorLayout layout = SectorLayout.forTeams(4, RADIUS / 2);
        List<int[]> bases = new ArrayList<>();
        for (Sector sector : layout.all()) {
            assertTrue(sector.contains(sector.baseDx(), sector.baseDz()), "a base must be inside its own sector");
            assertTrue(g.isOpenGround(sector.baseDx(), sector.baseDz()), "a base must sit on open ground");
            assertEquals(sector.index(), layout.at(sector.baseDx(), sector.baseDz()).index());
            bases.add(new int[]{sector.baseDx(), sector.baseDz()});
        }
        for (int i = 0; i < bases.size(); i++) {
            for (int j = i + 1; j < bases.size(); j++) {
                assertFalse(bases.get(i)[0] == bases.get(j)[0] && bases.get(i)[1] == bases.get(j)[1],
                        "two teams share a base");
            }
        }
    }

    @Test
    void columnsOnAWallAxisBelongToNoSector() {
        SectorLayout layout = SectorLayout.forTeams(4, RADIUS / 2);
        assertEquals(null, layout.at(0, 20));
        assertEquals(null, layout.at(20, 0));
        assertEquals(null, layout.at(0, 0));
    }

    @Test
    void unsupportedTeamCountsFailLoudlyRatherThanBuildingABrokenArena() {
        assertThrows(IllegalArgumentException.class, () -> SectorLayout.forTeams(5, 30));
        assertThrows(IllegalArgumentException.class, () -> SectorLayout.forTeams(2, 30));
    }

    // --- configuration guards -------------------------------------------

    @Test
    void nonsenseConfigurationIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaGeometry(4, 64, 50, 3, 14, 6, 40), "tiny radius");
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaGeometry(60, 64, 5, 3, 14, 6, 40), "platform too thin for its layers");
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaGeometry(60, 64, 50, 4, 14, 6, 40), "even wall thickness has no centre");
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaGeometry(60, 64, 50, 3, 14, 2, 40), "vault swallowed by the arms");
        assertThrows(IllegalArgumentException.class,
                () -> new ArenaGeometry(60, 64, 50, 3, 14, 6, 10), "glass lower than the walls");
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
}
