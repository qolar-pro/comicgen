package pro.qolar.walls.game;

/** Where a match is in its lifecycle. */
public enum GameState {

    /** No match. Admins can rebuild and fiddle with the arena. */
    IDLE("Idle"),

    /** Teams are assigned and teleported; players are locked in place. */
    COUNTDOWN("Starting"),

    /** Walls up. Gather, build, prepare. No PvP between teams. */
    GRACE("Preparing"),

    /** Walls down. Fight. */
    OPEN("Fighting"),

    /** A winner has been decided; the arena resets shortly. */
    ENDED("Finished");

    private final String displayName;

    GameState(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** True while a match is running in any phase. */
    public boolean inProgress() {
        return this == COUNTDOWN || this == GRACE || this == OPEN;
    }
}
