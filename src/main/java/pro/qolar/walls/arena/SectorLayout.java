package pro.qolar.walls.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How the arena is carved up between teams.
 *
 * <p>v1 ships the quadrant layout only - four teams, one square split by a cross
 * of four walls, exactly the classic Walls shape. This type is the seam where
 * radial "pie slice" layouts for 5+ teams slot in later without disturbing the
 * arena or game code, both of which only ever talk to {@link Sector}.
 */
public final class SectorLayout {

    /** Team counts this build can lay out. */
    public static final int QUADRANT_TEAMS = 4;

    private final List<Sector> sectors;

    private SectorLayout(List<Sector> sectors) {
        this.sectors = Collections.unmodifiableList(sectors);
    }

    /**
     * Build the layout for {@code teamCount} teams.
     *
     * @param baseOffset how far from the centre each base sits, in blocks
     * @throws IllegalArgumentException if this build cannot lay out that many teams
     */
    public static SectorLayout forTeams(int teamCount, int baseOffset) {
        if (teamCount != QUADRANT_TEAMS) {
            throw new IllegalArgumentException(
                    "this build only lays out " + QUADRANT_TEAMS + " teams (quadrants); "
                            + teamCount + " teams needs the radial layout, which is not implemented yet");
        }
        if (baseOffset < 1) {
            throw new IllegalArgumentException("base offset must be positive, got " + baseOffset);
        }
        // Clockwise from the north-east quadrant.
        int[][] signs = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
        List<Sector> built = new ArrayList<>();
        for (int i = 0; i < signs.length; i++) {
            built.add(new Sector(i, signs[i][0], signs[i][1], baseOffset));
        }
        return new SectorLayout(built);
    }

    public int size() {
        return sectors.size();
    }

    public Sector get(int index) {
        return sectors.get(index);
    }

    public List<Sector> all() {
        return sectors;
    }

    /** The sector containing a column, or {@code null} if it sits on a wall axis. */
    public Sector at(int dx, int dz) {
        for (Sector sector : sectors) {
            if (sector.contains(dx, dz)) {
                return sector;
            }
        }
        return null;
    }
}
