package pro.qolar.walls.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The objective and elimination rules.
 *
 * <p>These types deliberately make no static Bukkit calls, so the rules that
 * decide who wins can be tested without standing up a server.
 */
class GameLogicTest {

    // --- the 1000 HP objective ------------------------------------------

    @Test
    void objectiveStartsAtFullHealth() {
        TeamObjective objective = new TeamObjective(1000);
        assertEquals(1000, objective.health());
        assertEquals(1000, objective.maxHealth());
        assertTrue(objective.alive());
        assertEquals(1.0, objective.fraction());
    }

    @Test
    void twentyBreaksDestroyAThousandHealthObjective() {
        TeamObjective objective = new TeamObjective(1000);
        for (int i = 1; i <= 19; i++) {
            objective.damage(50);
            assertTrue(objective.alive(), "should survive break " + i);
        }
        objective.damage(50);
        assertFalse(objective.alive(), "the twentieth break should finish it");
        assertEquals(0, objective.health());
    }

    @Test
    void damageIsClampedAndReportsWhatItActuallyRemoved() {
        TeamObjective objective = new TeamObjective(100);
        assertEquals(60, objective.damage(60));
        assertEquals(40, objective.damage(500), "the final blow only removes what is left");
        assertEquals(0, objective.health());
        assertEquals(0, objective.damage(50), "a destroyed objective cannot be damaged further");
        assertFalse(objective.alive());
    }

    @Test
    void objectiveRejectsNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new TeamObjective(0));
        assertThrows(IllegalArgumentException.class, () -> new TeamObjective(-5));
        assertThrows(IllegalArgumentException.class, () -> new TeamObjective(100).damage(-1));
    }

    @Test
    void restoreBringsItBack() {
        TeamObjective objective = new TeamObjective(1000);
        objective.damage(1000);
        assertFalse(objective.alive());
        objective.restore();
        assertTrue(objective.alive());
        assertEquals(1000, objective.health());
    }

    // --- elimination: objective guards respawns ---------------------------

    @Test
    void aTeamWithItsWoolIntactIsNeverEliminated() {
        GameTeam team = team(2);
        assertTrue(team.canRespawn());
        assertFalse(team.isEliminated());
        // Even with everyone down, an intact objective keeps the team in.
        for (UUID member : team.members()) {
            team.putDown(member);
        }
        assertFalse(team.isEliminated(), "while the wool stands, the team is still in the match");
    }

    @Test
    void breakingTheWoolStopsRespawnsButDoesNotEliminate() {
        GameTeam team = team(2);
        team.objective().damage(1000);
        assertFalse(team.canRespawn(), "no respawns once the wool is gone");
        assertFalse(team.isEliminated(), "but the team is still alive while players remain");
        assertEquals(2, team.aliveCount());
    }

    @Test
    void aTeamIsOutOnlyWhenTheWoolIsGoneAndEveryoneIsDown() {
        GameTeam team = team(3);
        team.objective().damage(1000);

        UUID[] members = team.members().toArray(new UUID[0]);
        team.putDown(members[0]);
        assertFalse(team.isEliminated());
        assertEquals(2, team.aliveCount());

        team.putDown(members[1]);
        assertFalse(team.isEliminated());

        team.putDown(members[2]);
        assertTrue(team.isEliminated(), "wool gone and nobody left standing");
        assertEquals(0, team.aliveCount());
    }

    @Test
    void aTeamNobodyJoinedIsNotInTheMatch() {
        GameTeam team = new GameTeam(0, TeamColor.RED, 1000);
        assertEquals(0, team.size());
        assertTrue(team.isEliminated(), "an empty team should not keep a match alive");
    }

    @Test
    void puttingDownANonMemberDoesNothing() {
        GameTeam team = team(1);
        team.objective().damage(1000);
        team.putDown(UUID.randomUUID());
        assertFalse(team.isEliminated(), "a stranger going down must not eliminate the team");
        assertEquals(1, team.aliveCount());
    }

    @Test
    void leavingRemovesAPlayerFromBothRosters() {
        GameTeam team = team(2);
        UUID first = team.members().iterator().next();
        team.putDown(first);
        team.remove(first);
        assertEquals(1, team.size());
        assertFalse(team.isDown(first));
        assertEquals(1, team.aliveCount());
    }

    @Test
    void resetKeepsTheRosterButClearsTheRound() {
        GameTeam team = team(2);
        team.objective().damage(1000);
        team.members().forEach(team::putDown);
        assertTrue(team.isEliminated());

        team.resetForNewRound();
        assertFalse(team.isEliminated());
        assertTrue(team.canRespawn());
        assertEquals(2, team.size(), "the roster survives a reset");
        assertEquals(2, team.aliveCount());
    }

    @Test
    void clearEmptiesTheTeam() {
        GameTeam team = team(2);
        team.clear();
        assertEquals(0, team.size());
        assertEquals(1000, team.objective().health());
    }

    /** A four-team match should end exactly when one team is left standing. */
    @Test
    void lastTeamStandingWins() {
        GameTeam[] all = {team(1), team(1), team(1), team(1)};
        assertEquals(4, standing(all));

        // Knock out three teams the only way that works: wool first, then players.
        for (int i = 1; i < 4; i++) {
            all[i].objective().damage(1000);
            all[i].members().forEach(all[i]::putDown);
        }
        assertEquals(1, standing(all));
        assertFalse(all[0].isEliminated(), "the survivor is the winner");
    }

    private static int standing(GameTeam[] teams) {
        int count = 0;
        for (GameTeam team : teams) {
            if (!team.isEliminated()) {
                count++;
            }
        }
        return count;
    }

    private static GameTeam team(int memberCount) {
        GameTeam team = new GameTeam(0, TeamColor.RED, 1000);
        for (int i = 0; i < memberCount; i++) {
            team.add(UUID.randomUUID());
        }
        return team;
    }

    // --- misc -------------------------------------------------------------

    @Test
    void timerFormatting() {
        assertEquals("0:00", GameManager.formatTime(0));
        assertEquals("0:05", GameManager.formatTime(5));
        assertEquals("2:45", GameManager.formatTime(165));
        assertEquals("0:00", GameManager.formatTime(-3), "a negative timer reads as zero, not garbage");
    }

    @Test
    void statesKnowWhetherAMatchIsRunning() {
        assertFalse(GameState.IDLE.inProgress());
        assertTrue(GameState.COUNTDOWN.inProgress());
        assertTrue(GameState.GRACE.inProgress());
        assertTrue(GameState.OPEN.inProgress());
        assertFalse(GameState.ENDED.inProgress());
    }
}
