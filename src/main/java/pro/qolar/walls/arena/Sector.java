package pro.qolar.walls.arena;

/**
 * One team's slice of the arena, in arena-local coordinates.
 *
 * <p>Pure geometry, no Bukkit. A sector knows where its team's base sits and
 * defers to {@link ArenaGeometry} for which columns belong to it, so there is
 * only ever one definition of where the boundaries are.
 */
public final class Sector {

    private final ArenaGeometry geometry;
    private final int index;
    private final int baseDx;
    private final int baseDz;
    private final float spawnYaw;

    Sector(ArenaGeometry geometry, int index, int baseDx, int baseDz) {
        this.geometry = geometry;
        this.index = index;
        this.baseDx = baseDx;
        this.baseDz = baseDz;
        // Face the arena centre, so players spawn looking at the action.
        // Minecraft yaw: 0 is +Z, 90 is -X.
        this.spawnYaw = (float) Math.toDegrees(Math.atan2(baseDx, -baseDz));
    }

    public int index() {
        return index;
    }

    public int baseDx() {
        return baseDx;
    }

    public int baseDz() {
        return baseDz;
    }

    public float spawnYaw() {
        return spawnYaw;
    }

    /**
     * True when a column falls in this sector. Columns on a wall, inside the
     * vault, or outside the arena belong to no sector.
     */
    public boolean contains(int dx, int dz) {
        return geometry.sectorOf(dx, dz) == index;
    }
}
