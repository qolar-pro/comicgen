package pro.qolar.walls.game;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One team in a match.
 *
 * <p>Elimination follows the rule the game is built around: while the objective
 * stands, dead players come back; once it falls, they do not. A team is out only
 * when its objective is gone <em>and</em> every one of its players is down.
 *
 * <p>Named {@code GameTeam} to stay clearly distinct from {@code org.bukkit.scoreboard.Team}.
 * Free of static Bukkit calls, so it is unit testable.
 */
public final class GameTeam {

    private final int index;
    private final TeamColor color;
    private final TeamObjective objective;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Set<UUID> down = new LinkedHashSet<>();

    public GameTeam(int index, TeamColor color, int objectiveHealth) {
        this.index = index;
        this.color = color;
        this.objective = new TeamObjective(objectiveHealth);
    }

    public int index() {
        return index;
    }

    public TeamColor color() {
        return color;
    }

    public TeamObjective objective() {
        return objective;
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    public void add(UUID player) {
        members.add(player);
    }

    public void remove(UUID player) {
        members.remove(player);
        down.remove(player);
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    public int size() {
        return members.size();
    }

    /** While the objective stands, the team's dead come back. */
    public boolean canRespawn() {
        return objective.alive();
    }

    /** Mark a player permanently out of the match. */
    public void putDown(UUID player) {
        if (members.contains(player)) {
            down.add(player);
        }
    }

    public boolean isDown(UUID player) {
        return down.contains(player);
    }

    /** Players still in the fight. */
    public int aliveCount() {
        return members.size() - down.size();
    }

    /**
     * A team is out when its objective has fallen and none of its players are
     * left standing. A team nobody joined is trivially out - it is not playing.
     */
    public boolean isEliminated() {
        if (members.isEmpty()) {
            return true;
        }
        return !objective.alive() && aliveCount() <= 0;
    }

    /** Clear match state but keep the roster, ready for another round. */
    public void resetForNewRound() {
        objective.restore();
        down.clear();
    }

    /** Empty the team completely. */
    public void clear() {
        members.clear();
        down.clear();
        objective.restore();
    }
}
