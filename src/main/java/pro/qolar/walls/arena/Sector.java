package pro.qolar.walls.arena;

/**
 * One team's slice of the arena, in arena-local coordinates.
 *
 * <p>Pure geometry, no Bukkit. A sector knows where its team's base sits and
 * which columns belong to it.
 */
public final class Sector {

    private final int index;
    private final int signX;
    private final int signZ;
    private final int baseDx;
    private final int baseDz;
    private final float spawnYaw;

    Sector(int index, int signX, int signZ, int baseOffset) {
        this.index = index;
        this.signX = signX;
        this.signZ = signZ;
        this.baseDx = signX * baseOffset;
        this.baseDz = signZ * baseOffset;
        // Face the arena centre, so players spawn looking at the action.
        this.spawnYaw = (float) Math.toDegrees(Math.atan2(signX, -signZ));
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
     * True when a column falls in this sector. Columns sitting exactly on an axis
     * belong to no sector - those are the wall arms.
     */
    public boolean contains(int dx, int dz) {
        return sameSide(dx, signX) && sameSide(dz, signZ);
    }

    private static boolean sameSide(int value, int sign) {
        return sign > 0 ? value > 0 : value < 0;
    }
}
