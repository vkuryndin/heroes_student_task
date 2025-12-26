package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.Program;
import com.battle.heroes.util.GameSpeedUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SimulateBattleImplTest {

    enum Mode {
        ATTACK_FIRST_ALIVE,
        RETURN_NULL,
        RETURN_SELF
    }

    static class TestProgram extends Program {
        private final Mode mode;
        private final AtomicInteger calls;

        TestProgram(Unit unit, Army allyArmy, Army enemyArmy, Mode mode, AtomicInteger calls) {
            super(unit, allyArmy, enemyArmy, new GameSpeedUtil(0));
            this.mode = mode;
            this.calls = calls;
        }

        @Override
        public Unit attack() {
            calls.incrementAndGet();

            if (mode == Mode.RETURN_NULL) {
                return null;
            }
            if (mode == Mode.RETURN_SELF) {
                return this.unit; // некоторые дефолтные программы так делают
            }

            // ATTACK_FIRST_ALIVE
            for (Unit t : enemyArmy.getUnits()) {
                if (t != null && t.isAlive()) {
                    t.setHealth(t.getHealth() - unit.getBaseAttack());
                    if (t.getHealth() <= 0) t.setAlive(false);
                    return t;
                }
            }
            return null;
        }
    }

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

    @Test
    void endsImmediatelyIfOneArmyHasNoAlive() throws Exception {
        Army player = new Army();
        Army computer = new Army();

        // player empty => no alive
        Unit c1 = u("C1", 10, 5);
        computer.getUnits().add(c1);
        c1.setProgram(new TestProgram(c1, computer, player, Mode.ATTACK_FIRST_ALIVE, new AtomicInteger()));

        SimulateBattleImpl sim = new SimulateBattleImpl();
        String out = captureOut(() -> sim.simulate(player, computer));

        assertTrue(out.contains("Battle is over!"));
    }

    @Test
    void stalemateEndsWhenNoTargetsBothSides() throws Exception {
        Army player = new Army();
        Army computer = new Army();

        Unit p1 = u("P1", 10, 5);
        Unit c1 = u("C1", 10, 5);
        player.getUnits().add(p1);
        computer.getUnits().add(c1);

        p1.setProgram(new TestProgram(p1, player, computer, Mode.RETURN_NULL, new AtomicInteger()));
        c1.setProgram(new TestProgram(c1, computer, player, Mode.RETURN_NULL, new AtomicInteger()));

        SimulateBattleImpl sim = new SimulateBattleImpl();
        String out = captureOut(() -> sim.simulate(player, computer));

        // По ТЗ бой должен закончиться, если нет юнитов, способных сделать ход
        assertTrue(out.contains("Round 1 is over!") || out.contains("Battle is over!"));
        assertTrue(out.contains("Battle is over!"));

        // никто не умер
        assertTrue(p1.isAlive());
        assertTrue(c1.isAlive());
    }

    @Test
    void returnSelfTreatedAsNoTargetSoStalemateEnds() throws Exception {
        Army player = new Army();
        Army computer = new Army();

        Unit p1 = u("P1", 10, 5);
        Unit c1 = u("C1", 10, 5);
        player.getUnits().add(p1);
        computer.getUnits().add(c1);

        p1.setProgram(new TestProgram(p1, player, computer, Mode.RETURN_SELF, new AtomicInteger()));
        c1.setProgram(new TestProgram(c1, computer, player, Mode.RETURN_SELF, new AtomicInteger()));

        SimulateBattleImpl sim = new SimulateBattleImpl();
        String out = captureOut(() -> sim.simulate(player, computer));

        assertTrue(out.contains("Battle is over!"));
        // В нашей логике self трактуется как "нет цели", т.е. в логах будет "-> null" (если дефолтный логгер)
        // Если у тебя инжектится другой PrintBattleLog, строка может быть другой — это нормально.
    }

    @Test
    void deadUnitBeforeItsTurnMustNotAct_rebuildWorks() throws Exception {
        Army player = new Army();
        Army computer = new Army();

        // Игрок: P1 сильный, убьёт C1 сразу
        Unit p1 = u("P1", 50, 100);
        Unit p2 = u("P2", 50, 1);
        player.getUnits().add(p1);
        player.getUnits().add(p2);

        // Комп: C1 должен умереть до своего хода, C2 останется
        Unit c1 = u("C1", 30, 50);
        Unit c2 = u("C2", 30, 1);
        computer.getUnits().add(c1); // важно: C1 первый, чтобы его выбрали целью
        computer.getUnits().add(c2);

        AtomicInteger c1Calls = new AtomicInteger();
        AtomicInteger c2Calls = new AtomicInteger();

        p1.setProgram(new TestProgram(p1, player, computer, Mode.ATTACK_FIRST_ALIVE, new AtomicInteger()));
        p2.setProgram(new TestProgram(p2, player, computer, Mode.ATTACK_FIRST_ALIVE, new AtomicInteger()));
        c1.setProgram(new TestProgram(c1, computer, player, Mode.ATTACK_FIRST_ALIVE, c1Calls));
        c2.setProgram(new TestProgram(c2, computer, player, Mode.ATTACK_FIRST_ALIVE, c2Calls));

        SimulateBattleImpl sim = new SimulateBattleImpl();
        captureOut(() -> sim.simulate(player, computer));

        assertFalse(c1.isAlive(), "C1 должен умереть");
        assertEquals(0, c1Calls.get(), "C1 не должен был успеть походить (должен быть исключён после смерти)");
        // C2 может походить, может нет — зависит от того, как быстро закончится бой
    }

    @Test
    void battleEventuallyEndsByDeathsWhenBothSidesCanAttack() throws Exception {
        Army player = new Army();
        Army computer = new Army();

        Unit p1 = u("P1", 20, 10);
        Unit c1 = u("C1", 20, 3);

        player.getUnits().add(p1);
        computer.getUnits().add(c1);

        p1.setProgram(new TestProgram(p1, player, computer, Mode.ATTACK_FIRST_ALIVE, new AtomicInteger()));
        c1.setProgram(new TestProgram(c1, computer, player, Mode.ATTACK_FIRST_ALIVE, new AtomicInteger()));

        SimulateBattleImpl sim = new SimulateBattleImpl();
        captureOut(() -> sim.simulate(player, computer));

        // бой должен закончиться смертью одной из сторон
        assertTrue(!p1.isAlive() || !c1.isAlive());
    }
}
