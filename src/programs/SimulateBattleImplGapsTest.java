package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.PrintBattleLog;
import com.battle.heroes.army.programs.Program;
import com.battle.heroes.util.GameSpeedUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extra tests to close gaps in SimulateBattleImpl:
 * - Symmetric case: a PLAYER unit dies before its turn -> must not act.
 * - "One side cannot make a move" termination (per wording: "one of the armies ...").
 */
class SimulateBattleImplGapsTest {

    private static Unit u(String name, int hp, int atk) {
        return new Unit(
                name,
                "TestType",
                hp,
                atk,
                1,
                "melee",
                Collections.emptyMap(),
                Collections.emptyMap(),
                0,
                0
        );
    }

    private static String captureOut(ThrowingRunnable r) throws Exception {
        PrintStream old = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            r.run();
        } finally {
            System.setOut(old);
        }
        return baos.toString();
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void injectLogger(SimulateBattleImpl sim, PrintBattleLog log) throws Exception {
        Field f = SimulateBattleImpl.class.getDeclaredField("printBattleLog");
        f.setAccessible(true);
        f.set(sim, log);
    }

    /**
     * Program that attacks FIRST alive unit in enemyArmy (by list order),
     * optionally applying damage.
     */
    static class AttackFirstAliveProgram extends Program {
        private final int damage;
        private final AtomicInteger calls;

        AttackFirstAliveProgram(Unit unit, Army allyArmy, Army enemyArmy, int damage, AtomicInteger calls) {
            super(unit, allyArmy, enemyArmy, new GameSpeedUtil(0));
            this.damage = damage;
            this.calls = calls;
        }

        @Override
        public Unit attack() {
            calls.incrementAndGet();
            for (Unit t : enemyArmy.getUnits()) {
                if (t != null && t.isAlive()) {
                    if (damage > 0) {
                        t.setHealth(t.getHealth() - damage);
                        if (t.getHealth() <= 0) t.setAlive(false);
                    }
                    return t;
                }
            }
            return null;
        }
    }

    static class ReturnNullProgram extends Program {
        private final AtomicInteger calls;

        ReturnNullProgram(Unit unit, AtomicInteger calls) {
            super(unit, null, null, new GameSpeedUtil(0));
            this.calls = calls;
        }

        @Override
        public Unit attack() {
            calls.incrementAndGet();
            return null;
        }
    }

    /**
     * Program that returns a NON-null target (real enemy unit),
     * but deals 0 damage (so nothing dies).
     */
    static class NoDamageButHasTargetProgram extends Program {
        private final AtomicInteger calls;

        NoDamageButHasTargetProgram(Unit unit, Army allyArmy, Army enemyArmy, AtomicInteger calls) {
            super(unit, allyArmy, enemyArmy, new GameSpeedUtil(0));
            this.calls = calls;
        }

        @Override
        public Unit attack() {
            calls.incrementAndGet();
            for (Unit t : enemyArmy.getUnits()) {
                if (t != null && t.isAlive()) {
                    return t; // no damage
                }
            }
            return null;
        }
    }

    @Test
    void deadPlayerUnitBeforeItsTurnMustNotAct_whenKilledByComputer() throws Exception {
        Army player = new Army();
        Army computer = new Army();

        // Player has two units:
        // - P_Strong acts first (higher atk), does NOT kill anyone
        // - P_Victim would act later, but must die before its turn
        Unit pVictim = u("P_Victim", 10, 10);
        Unit pStrong = u("P_Strong", 1000, 100);

        // IMPORTANT: enemy selection in test programs is "first alive by list order".
        // Put victim FIRST in player's list so computer targets it.
        player.getUnits().add(pVictim);
        player.getUnits().add(pStrong);

        // Computer has one attacker strong enough to kill victim in one hit.
        Unit cStrong = u("C_Strong", 1000, 50);
        computer.getUnits().add(cStrong);

        AtomicInteger victimCalls = new AtomicInteger();
        AtomicInteger strongCalls = new AtomicInteger();
        AtomicInteger computerCalls = new AtomicInteger();

        // Player strong: has a target but deals 0 damage (so no one dies from player side).
        pStrong.setProgram(new NoDamageButHasTargetProgram(pStrong, player, computer, strongCalls));

        // Victim: if it ever gets to act, calls++ (should remain 0).
        pVictim.setProgram(new AttackFirstAliveProgram(pVictim, player, computer, 1, victimCalls));

        // Computer: kills first alive player unit (victim) with big damage.
        cStrong.setProgram(new AttackFirstAliveProgram(cStrong, computer, player, 999, computerCalls));

        SimulateBattleImpl sim = new SimulateBattleImpl();
        // Stop after some logs just in case (should finish anyway once victim is dead? not necessarily)
        injectLogger(sim, (att, trg) -> { /* no-op */ });

        captureOut(() -> sim.simulate(player, computer));

        assertFalse(pVictim.isAlive(), "Victim must be killed by computer before its own turn");
        assertEquals(0, victimCalls.get(), "Victim must NOT act if it died before its turn");
        assertTrue(strongCalls.get() >= 1, "Strong player unit should act at least once");
        assertTrue(computerCalls.get() >= 1, "Computer should act at least once");
    }

    @Test
    void battleMustEndIfOneSideCannotMakeAMove_perTaskWording() throws Exception {
        Army player = new Army();
        Army computer = new Army();

        // Player: always returns null (no move)
        Unit p1 = u("P1", 1000, 10);
        player.getUnits().add(p1);

        // Computer: always returns a real target (so it CAN make a move), but deals 0 damage (no deaths)
        Unit c1 = u("C1", 1000, 10);
        computer.getUnits().add(c1);

        AtomicInteger pCalls = new AtomicInteger();
        AtomicInteger cCalls = new AtomicInteger();

        p1.setProgram(new ReturnNullProgram(p1, pCalls));
        c1.setProgram(new NoDamageButHasTargetProgram(c1, computer, player, cCalls));

        // Fuse: if battle doesn't end, stop the test quickly (otherwise infinite rounds are possible)
        SimulateBattleImpl sim = new SimulateBattleImpl();
        injectLogger(sim, new PrintBattleLog() {
            int cnt = 0;
            @Override
            public void printBattleLog(Unit attacker, Unit target) {
                cnt++;
                if (cnt >= 20) throw new RuntimeException("Stop");
            }
        });

        String out;
        try {
            out = captureOut(() -> sim.simulate(player, computer));
        } catch (RuntimeException e) {
            if (!"Stop".equals(e.getMessage())) throw e;
            // if we got here -> battle did NOT end in time
            out = ""; // make assertion fail below
        }

        assertTrue(out.contains("Battle is over!"),
                "Per task wording, battle should end if ONE army cannot make a move. " +
                        "If this fails, change condition from '&&' to '||' in SimulateBattleImpl.");
        assertTrue(pCalls.get() >= 1);
        assertTrue(cCalls.get() >= 1);
        assertTrue(p1.isAlive() && c1.isAlive(), "No one should die in this scenario");
    }
}
