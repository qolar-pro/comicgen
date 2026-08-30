package pro.qolar.walls.arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where each team's base sits within an arena.
 *
 * <p>The division itself lives in {@link ArenaGeometry}, which knows the arms and
 * the shape; this type only turns sector indices into base positions. Any team
 * count the geometry accepts works here - four teams on a square is the classic
 * cross, and other counts get a circular arena with equal slices.
 */
public final class SectorLayout {

    private final List<Sector> sectors;

    private SectorLayout(List<Sector> sectors) {
        this.sectors = Collections.unmodifiableList(sectors);
    }

    /**
     * Lay out bases for every sector in {@code geometry}.
     *
     * @param baseOffset how far from the centre each base sits, measured in the
     *                   arena's own distance metric
     */
    public static SectorLayout forGeometry(ArenaGeometry geometry, int baseOffset) {
        if (baseOffset < 1) {
            throw new IllegalArgumentException("base offset must be positive, got " + baseOffset);
        }
        if (baseOffset <= geometry.vaultRadius() + 1 || baseOffset >= geometry.radius() - 1) {
            throw new IllegalArgumentException(
                    "base offset " + baseOffset + " must sit between the vault and the arena edge");
        }
        List<Sector> built = new ArrayList<>();
        for (int i = 0; i < geometry.sectorCount(); i++) {
            int[] point = geometry.basePoint(i, baseOffset);
            built.add(new Sector(geometry, i, point[0], point[1]));
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

    /** The sector containing a column, or {@code null} if it belongs to none. */
    public Sector at(int dx, int dz) {
        for (Sector sector : sectors) {
            if (sector.contains(dx, dz)) {
                return sector;
            }
        }
        return null;
    }
}
