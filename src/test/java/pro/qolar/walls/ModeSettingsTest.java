package pro.qolar.walls;

import org.junit.jupiter.api.Test;
import pro.qolar.walls.arena.ArenaGeometry;
import pro.qolar.walls.arena.ArenaShape;
import pro.qolar.walls.arena.Sector;
import pro.qolar.walls.arena.SectorLayout;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped game modes.
 *
 * <p>A preset that cannot build a valid arena is worse than no preset: it fails
 * at startup on somebody's server rather than here. Every default is therefore
 * built and checked for sealing.
 */
class ModeSettingsTest {

    private static final int FLOOR_Y = 64;
    private static final int THICKNESS = 50;
    private static final int WALL_THICKNESS = 3;

    @Test
    void theModesCoverTheSizesTheGameWasAskedFor() {
        Map<String, ModeSettings> modes = ModeSettings.defaults();

        ModeSettings mega = modes.get("mega");
        assertNotNull(mega, "a 100-player mode should exist");
        assertEquals(4, mega.teams());
        assertEquals(100, mega.capacity(), "four teams of twenty-five");

        ModeSettings large = modes.get("large");
        assertNotNull(large, "a five-team mode should exist");
        assertEquals(5, large.teams());
        assertEquals(60, large.capacity(), "five teams of twelve");
        assertEquals(ArenaShape.CIRCLE, large.shape(), "five teams cannot fairly share a square");

        assertNotNull(modes.get("small"), "a small-game mode should exist");
        assertNotNull(modes.get("duel"), "a two-player mode should exist");
    }

    @Test
    void fourTeamModesKeepTheClassicSquare() {
        for (ModeSettings mode : ModeSettings.defaults().values()) {
            if (mode.teams() == 4) {
                assertEquals(ArenaShape.SQUARE, mode.shape(),
                        "mode " + mode.name() + " should keep the classic cross");
            }
        }
    }

    /** Every shipped preset must produce an arena that actually builds. */
    @Test
    void everyDefaultModeBuildsAValidArena() {
        for (ModeSettings mode : ModeSettings.defaults().values()) {
            ArenaGeometry geometry = geometryFor(mode);
            assertEquals(mode.teams(), geometry.sectorCount(), mode.name());
            assertTrue(geometry.ceilingY() > geometry.wallTopY(), mode.name() + ": ceiling clears the walls");
            assertTrue(geometry.graceBuildCeilingY() + 2 < geometry.wallTopY() + 1,
                    mode.name() + ": build ceiling must keep the wall top out of reach");

            int baseOffset = Math.max(geometry.vaultRadius() + 4, geometry.radius() / 2);
            SectorLayout layout = SectorLayout.forGeometry(geometry, baseOffset);
            assertEquals(mode.teams(), layout.size(), mode.name());
            for (Sector sector : layout.all()) {
                assertTrue(geometry.isOpenGround(sector.baseDx(), sector.baseDz()),
                        mode.name() + ": base " + sector.index() + " must sit on open ground");
            }
        }
    }

    /** And an arena whose sectors are genuinely separated. */
    @Test
    void everyDefaultModeSealsItsSectors() {
        for (ModeSettings mode : ModeSettings.defaults().values()) {
            ArenaGeometry geometry = geometryFor(mode);
            int baseOffset = Math.max(geometry.vaultRadius() + 4, geometry.radius() / 2);
            SectorLayout layout = SectorLayout.forGeometry(geometry, baseOffset);

            for (Sector sector : layout.all()) {
                Set<Long> reached = flood(geometry, sector.baseDx(), sector.baseDz());
                assertFalse(reached.isEmpty(), mode.name() + ": sector " + sector.index() + " is empty");
                for (long cell : reached) {
                    int dx = (int) (cell >> 32);
                    int dz = (int) cell;
                    assertEquals(sector.index(), geometry.sectorOf(dx, dz),
                            mode.name() + ": sector " + sector.index() + " leaked at (" + dx + "," + dz + ")");
                }
            }
        }
    }

    @Test
    void modesSortFromSmallestToLargest() {
        var byCapacity = ModeSettings.defaults().values().stream()
                .sorted((a, b) -> Integer.compare(a.capacity(), b.capacity()))
                .map(ModeSettings::name)
                .toList();
        assertEquals(java.util.List.of("duel", "small", "large", "mega"), byCapacity,
                "auto-picking a mode by player count relies on this order");
    }

    private static ArenaGeometry geometryFor(ModeSettings mode) {
        int glass = Math.max(40, mode.wallHeight() + 10);
        return new ArenaGeometry(mode.shape(), mode.teams(), mode.radius(), FLOOR_Y, THICKNESS,
                WALL_THICKNESS, mode.wallHeight(), mode.vaultRadius(), glass);
    }

    private static Set<Long> flood(ArenaGeometry g, int startDx, int startDz) {
        Set<Long> seen = new HashSet<>();
        if (!g.isOpenGround(startDx, startDz)) {
            return seen;
        }
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startDx, startDz});
        seen.add(((long) startDx << 32) | (startDz & 0xffffffffL));
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int nz = at[1] + step[1];
                if (!g.isOpenGround(nx, nz) || !seen.add(((long) nx << 32) | (nz & 0xffffffffL))) {
                    continue;
                }
                queue.add(new int[]{nx, nz});
            }
        }
        return seen;
    }
}
