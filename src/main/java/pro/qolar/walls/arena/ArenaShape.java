package pro.qolar.walls.arena;

/**
 * The outline of an arena, expressed as the distance metric used to decide what
 * is inside it.
 *
 * <p>A square arena split by a cross is the classic Walls shape and stays the
 * default for four teams. It cannot be divided fairly into five, though - a
 * sector covering a corner is far larger than one covering an edge - so any
 * other team count uses a circle, where every sector is the same size.
 */
public enum ArenaShape {

    /** Chebyshev distance: the classic square arena. */
    SQUARE {
        @Override
        public double distance(double dx, double dz) {
            return Math.max(Math.abs(dx), Math.abs(dz));
        }
    },

    /** Euclidean distance: a circular arena, so N sectors are all equal. */
    CIRCLE {
        @Override
        public double distance(double dx, double dz) {
            return Math.sqrt(dx * dx + dz * dz);
        }
    };

    public abstract double distance(double dx, double dz);

    /** The shape a given team count should use unless told otherwise. */
    public static ArenaShape defaultFor(int teamCount) {
        return teamCount == 4 ? SQUARE : CIRCLE;
    }
}
