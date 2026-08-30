package pro.qolar.walls.arena;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caves are carved into the same rock the walls are sunk through, so the tests
 * that matter most here are the ones proving they cannot open a route between
 * sectors.
 */
class CaveFieldTest {

    private static final int MARGIN = 3;

    private static ArenaGeometry geometry() {
        return new ArenaGeometry(ArenaShape.SQUARE, 4, 40, 64, 50, 3, 14, 6, 40);
    }

    private static CaveField caves(ArenaGeometry g, long seed) {
        return CaveField.generate(g, ColumnMap.of(g), seed, 20, 140, MARGIN, 6);
    }

    @Test
    void cavesActuallyGetCarved() {
        ArenaGeometry g = geometry();
        CaveField field = caves(g, 12345L);
        assertTrue(field.carvedCount() > 2000,
                "a cave system should be worth exploring, carved " + field.carvedCount());
        assertTrue(field.oreCount() > 200, "ore should be findable, placed " + field.oreCount());
    }

    /** The property the sealing of the whole game rests on. */
    @Test
    void carvingNeverTouchesAWallOrComesNearOne() {
        ArenaGeometry g = geometry();
        ColumnMap columns = ColumnMap.of(g);
        CaveField field = caves(g, 999L);

        int radius = g.radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                boolean nearWall = false;
                for (int ox = -MARGIN; ox <= MARGIN && !nearWall; ox++) {
                    for (int oz = -MARGIN; oz <= MARGIN && !nearWall; oz++) {
                        if (columns.at(dx + ox, dz + oz) == ColumnMap.Column.WALL) {
                            nearWall = true;
                        }
                    }
                }
                if (!nearWall) {
                    continue;
                }
                for (int y = g.bottomY(); y <= g.floorY(); y++) {
                    assertFalse(field.isCarved(dx, y, dz),
                            "carved a cave within " + MARGIN + " of a wall at (" + dx + "," + y + "," + dz + ")");
                }
            }
        }
    }

    @Test
    void carvingStaysInsideTheStone() {
        ArenaGeometry g = geometry();
        ColumnMap columns = ColumnMap.of(g);
        CaveField field = caves(g, 4242L);

        int radius = g.radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = g.bottomY() - 2; y <= g.floorY() + 2; y++) {
                    if (!field.isCarved(dx, y, dz)) {
                        continue;
                    }
                    assertTrue(g.isStoneDepth(y),
                            "carved outside the stone band at y=" + y);
                    assertEquals(ColumnMap.Column.PLATFORM, columns.at(dx, dz),
                            "carved somewhere other than ordinary ground at (" + dx + "," + dz + ")");
                    assertNotEquals(ArenaGeometry.Layer.BEDROCK, g.layerAtY(y), "never breach the bedrock");
                    assertNotEquals(ArenaGeometry.Layer.GRASS, g.layerAtY(y), "never breach the surface");
                    assertNotEquals(ArenaGeometry.Layer.DIRT, g.layerAtY(y), "never breach the dirt");
                }
            }
        }
    }

    @Test
    void oreSitsInStoneAndNeverInsideACave() {
        ArenaGeometry g = geometry();
        CaveField field = caves(g, 777L);
        int radius = g.radius();
        int found = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = g.bottomY(); y <= g.floorY(); y++) {
                    if (field.oreAt(dx, y, dz) == null) {
                        continue;
                    }
                    found++;
                    assertTrue(g.isStoneDepth(y), "ore outside the stone band at y=" + y);
                    assertFalse(field.isCarved(dx, y, dz), "ore placed inside a carved cave");
                }
            }
        }
        assertEquals(field.oreCount(), found, "every placed ore should be findable");
    }

    @Test
    void deepOresStayDeep() {
        ArenaGeometry g = geometry();
        CaveField field = caves(g, 31337L);
        int radius = g.radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = g.bottomY(); y <= g.floorY(); y++) {
                    CaveField.Ore ore = field.oreAt(dx, y, dz);
                    if (ore == null) {
                        continue;
                    }
                    int depth = g.floorY() - y;
                    if (ore == CaveField.Ore.DIAMOND || ore == CaveField.Ore.EMERALD) {
                        assertTrue(depth >= 30, ore + " should be a deep find, was at depth " + depth);
                    }
                }
            }
        }
    }

    @Test
    void theSameSeedAlwaysCarvesTheSameCaves() {
        ArenaGeometry g = geometry();
        CaveField first = caves(g, 2024L);
        CaveField second = caves(g, 2024L);
        CaveField different = caves(g, 2025L);

        assertEquals(first.carvedCount(), second.carvedCount());
        assertEquals(first.chestSpots().size(), second.chestSpots().size());
        for (int i = 0; i < first.chestSpots().size(); i++) {
            assertArrayEqualsInt(first.chestSpots().get(i), second.chestSpots().get(i));
        }
        assertNotEquals(first.carvedCount(), different.carvedCount(),
                "a different seed should give a different cave system");
    }

    @Test
    void caveChestsStandOnAFloorWithHeadroom() {
        ArenaGeometry g = geometry();
        CaveField field = caves(g, 55L);
        List<int[]> spots = field.chestSpots();
        assertFalse(spots.isEmpty(), "cave loot should exist");

        Set<String> seen = new HashSet<>();
        for (int[] spot : spots) {
            int dx = spot[0];
            int y = spot[1];
            int dz = spot[2];
            assertTrue(field.isCarved(dx, y, dz), "a chest must sit in open cave");
            assertFalse(field.isCarved(dx, y - 1, dz), "a chest needs solid ground under it");
            assertTrue(field.isCarved(dx, y + 1, dz), "a chest needs headroom to be reachable");
            assertTrue(seen.add(dx + ":" + y + ":" + dz), "two chests in the same spot");
        }
    }

    @Test
    void cavesCanBeSwitchedOff() {
        CaveField none = CaveField.none();
        assertEquals(0, none.carvedCount());
        assertEquals(0, none.oreCount());
        assertTrue(none.chestSpots().isEmpty());
        assertFalse(none.isCarved(0, 30, 0));
    }

    private static void assertArrayEqualsInt(int[] a, int[] b) {
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], "element " + i);
        }
    }
}
