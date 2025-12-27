package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.Edge;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class UnitTargetPathFinderImplExtraTest {

    private final UnitTargetPathFinderImpl finder = new UnitTargetPathFinderImpl();

    private static Unit unitAt(String name, int x, int y) {
        return new Unit(name, "T", 10, 1, 1, "m",
                Collections.emptyMap(), Collections.emptyMap(), x, y);
    }

    private static String captureOut(Runnable r) {
        PrintStream old = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try { r.run(); } finally { System.setOut(old); }
        return baos.toString();
    }

    @Test
    void startEqualsTarget_returnsSingleCellPath() {
        Unit a = unitAt("A", 5, 5);
        Unit t = unitAt("T", 5, 5);

        List<Edge> path = finder.getTargetPath(a, t, List.of(a, t));
        assertEquals(1, path.size());
        assertEquals(5, path.get(0).getX());
        assertEquals(5, path.get(0).getY());
    }

    @Test
    void outOfBounds_returnsEmpty() {
        Unit a = unitAt("A", -1, 0);
        Unit t = unitAt("T", 1, 1);

        List<Edge> path = finder.getTargetPath(a, t, List.of(a, t));
        assertTrue(path.isEmpty());
    }

    @Test
    void unreachable_returnsEmptyAndLogs() {
        Unit a = unitAt("A", 13, 0);
        Unit t = unitAt("T", 13, 2);

        // Full wall at y=1 across all x: blocks any crossing from y=0 to y=2
        List<Unit> all = new ArrayList<>();
        all.add(a);
        all.add(t);
        for (int x = 0; x < 27; x++) {
            Unit wall = unitAt("W" + x, x, 1);
            wall.setAlive(true);
            all.add(wall);
        }

        String out = captureOut(() -> {
            List<Edge> path = finder.getTargetPath(a, t, all);
            assertTrue(path.isEmpty());
        });

        assertTrue(out.contains("cannot find path to attack unit"), "Should log unreachable path");
    }

    @Test
    void pathNeverStepsOnAliveObstacleCells() {
        Unit a = unitAt("A", 1, 0);
        Unit t = unitAt("T", 1, 3);

        Unit o1 = unitAt("O1", 1, 1);
        Unit o2 = unitAt("O2", 1, 2);
        o1.setAlive(true);
        o2.setAlive(true);

        List<Unit> all = List.of(a, t, o1, o2);

        List<Edge> path = finder.getTargetPath(a, t, all);

        // Might be empty if fully blocked, but if a path exists it must not step on obstacles
        Set<String> obstacles = Set.of("1,1", "1,2");
        for (Edge e : path) {
            String key = e.getX() + "," + e.getY();
            assertFalse(obstacles.contains(key), "Path must not step onto alive obstacles: " + key);
        }
    }
}
