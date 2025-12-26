package programs;

import com.battle.heroes.army.Unit;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuitableForAttackUnitsFinderImplTest {

    private static Unit alive(String name, int x, int y) {
        return new Unit(
                name,
                "TestType",
                10,
                1,
                1,
                "melee",
                Collections.emptyMap(),
                Collections.emptyMap(),
                x,
                y
        );
    }

    private static Unit dead(String name, int x, int y) {
        Unit u = alive(name, x, y);
        u.setAlive(false);
        return u;
    }

    private static String captureOut(Runnable r) {
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

    /**
     * unitsByRow — это 3 колонки цели по X в порядке слева->направо.
     * Для isLeftArmyTarget=false (цель справа): front = index 0, mid = 1, back = 2.
     * Для isLeftArmyTarget=true  (цель слева): front = index 2, mid = 1, back = 0.
     */

    @Test
    void frontColumnAlwaysVisible_rightTarget() {
        SuitableForAttackUnitsFinderImpl f = new SuitableForAttackUnitsFinderImpl();

        Unit front = alive("F", 24, 5);
        List<List<Unit>> cols = List.of(
                List.of(front),    // front
                List.of(),         // mid
                List.of()          // back
        );

        List<Unit> res = f.getSuitableUnits(cols, false);
        assertEquals(1, res.size());
        assertSame(front, res.get(0));
    }

    @Test
    void midBlockedByAliveFront_sameY_rightTarget() {
        SuitableForAttackUnitsFinderImpl f = new SuitableForAttackUnitsFinderImpl();

        Unit front = alive("F", 24, 7);
        Unit mid = alive("M", 25, 7);

        List<List<Unit>> cols = List.of(
                List.of(front),   // front
                List.of(mid),     // mid
                List.of()         // back
        );

        List<Unit> res = f.getSuitableUnits(cols, false);

        assertTrue(res.contains(front), "Front должен быть доступен");
        assertFalse(res.contains(mid), "Mid должен быть заблокирован front на том же y");
    }

    @Test
    void midVisibleIfFrontDead_rightTarget() {
        SuitableForAttackUnitsFinderImpl f = new SuitableForAttackUnitsFinderImpl();

        Unit frontDead = dead("Fdead", 24, 7);
        Unit mid = alive("M", 25, 7);

        List<List<Unit>> cols = List.of(
                List.of(frontDead), // front (мертвый -> не блокирует)
                List.of(mid),       // mid
                List.of()           // back
        );

        List<Unit> res = f.getSuitableUnits(cols, false);

        assertFalse(res.contains(frontDead), "Мёртвые не должны возвращаться");
        assertTrue(res.contains(mid), "Mid должен стать доступным, если front мёртв");
    }

    @Test
    void backBlockedOnlyByMid_notByFront_rightTarget() {
        SuitableForAttackUnitsFinderImpl f = new SuitableForAttackUnitsFinderImpl();

        // ВАЖНО: блокирует только соседняя колонка.
        // Даже если front занят на том же y, back должен быть доступен, если mid пуст.
        Unit front = alive("F", 24, 10);
        Unit back = alive("B", 26, 10);

        List<List<Unit>> cols = List.of(
                List.of(front), // front
                List.of(),      // mid empty
                List.of(back)   // back
        );

        List<Unit> res = f.getSuitableUnits(cols, false);

        assertTrue(res.contains(front));
        assertTrue(res.contains(back), "Back должен быть доступен, т.к. mid пуст на этом y");
    }

    @Test
    void leftTargetOrientation_frontIsRightmostColumn() {
        SuitableForAttackUnitsFinderImpl f = new SuitableForAttackUnitsFinderImpl();

        // isLeftArmyTarget=true => front = index 2 (правая колонка цели),
        // mid = index 1, back = index 0.
        Unit front = alive("F", 2, 3);
        Unit mid = alive("M", 1, 3);
        Unit back = alive("B", 0, 3);

        List<List<Unit>> cols = List.of(
                List.of(back),  // index 0
                List.of(mid),   // index 1
                List.of(front)  // index 2 (front)
        );

        List<Unit> res = f.getSuitableUnits(cols, true);

        assertTrue(res.contains(front), "Front всегда доступен");
        assertFalse(res.contains(mid), "Mid заблокирован front на том же y");
        assertFalse(res.contains(back), "Back заблокирован mid на том же y");
    }

    @Test
    void emptyResultPrintsLog() {
        SuitableForAttackUnitsFinderImpl f = new SuitableForAttackUnitsFinderImpl();

        List<List<Unit>> cols = List.of(
                List.of(),
                List.of(),
                List.of()
        );

        String out = captureOut(() -> {
            List<Unit> res = f.getSuitableUnits(cols, false);
            assertTrue(res.isEmpty());
        });

        assertTrue(out.contains("Unit can not find target for attack!"));
    }
}
