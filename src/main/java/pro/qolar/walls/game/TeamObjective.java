package pro.qolar.walls.game;

/**
 * A team's destructible block - the thing that has to fall before that team can
 * be finished off.
 *
 * <p>Health is chipped in fixed amounts rather than tracked against real mining
 * progress: a break attempt is cancelled and costs the objective a fixed slice of
 * its health. That keeps the cost of destroying it identical for every player
 * regardless of the tool they hold, and makes it straightforward to reason about
 * and to test.
 *
 * <p>Free of static Bukkit calls on purpose, so it can be unit tested without a
 * running server.
 */
public final class TeamObjective {

    private final int maxHealth;
    private int health;

    public TeamObjective(int maxHealth) {
        if (maxHealth < 1) {
            throw new IllegalArgumentException("objective health must be positive, got " + maxHealth);
        }
        this.maxHealth = maxHealth;
        this.health = maxHealth;
    }

    public int maxHealth() {
        return maxHealth;
    }

    public int health() {
        return health;
    }

    public boolean alive() {
        return health > 0;
    }

    /** 1.0 at full health, 0.0 when destroyed. */
    public double fraction() {
        return (double) health / maxHealth;
    }

    /**
     * Chip the objective.
     *
     * @return how much health was actually removed, which is less than
     *         {@code amount} on the final blow and zero once it is already down
     */
    public int damage(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("damage cannot be negative, got " + amount);
        }
        int applied = Math.min(amount, health);
        health -= applied;
        return applied;
    }

    /** Back to full health, for a fresh match. */
    public void restore() {
        health = maxHealth;
    }
}
