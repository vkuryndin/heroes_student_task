package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.Edge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnitTargetPathFinderImplTest {

    private final UnitTargetPathFinderImpl finder = new UnitTargetPathFinderImpl();

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

    private static List<Unit> list(Unit... units) {
        ArrayList<Unit> l = new ArrayList<>();
        Collections.addAll(l, units);
        return l;
    }

    private static void assertPathBasicRules(List<Edge> path, int sx, int sy, int tx, int ty) {
        assertNotNull(path, "Path must not be null");
        assertFalse(path.isEmpty(), "Path must not be empty");

        Edge first = path.get(0);
        Edge last = path.get(path.size() - 1);

        assertEquals(sx, first.getX(), "Path must start at attacker X");
        assertEquals(sy, first.getY(), "Path must start at attacker Y");

        assertEquals(tx, last.getX(), "Path must end at target X");
        assertEquals(ty, last.getY(), "Path must end at target Y");

        for (int i = 1; i < path.size(); i++) {
            Edge a = path.get(i - 1);
            Edge b = path.get(i);

            int dx = Math.abs(b.getX() - a.getX());
            int dy = Math.abs(b.getY() - a.getY());

            assertTrue(dx <= 1 && dy <= 1 && (dx + dy) > 0,
                    "Each step must move to a neighbor cell (including diagonals)");
        }
    }

    private static void assertNoBlockedCells(List<Edge> path, boolean[][] blocked, int tx, int ty) {
        for (Edge e : path) {
            int x = e.getX();
            int y = e.getY();
            if (x == tx && y == ty) continue; // target cell is allowed
            assertFalse(blocked[x][y], "Path must not go through blocked cells");
        }
    }

    @Test
    void directNeighborDiagonalPath() {
        Unit attacker = alive("A", 1, 1);
        Unit target = alive("T", 2, 2);

        List<Edge> path = finder.getTargetPath(attacker, target, list(attacker, target));

        assertPathBasicRules(path, 1, 1, 2, 2);
        assertEquals(2, path.size(), "Diagonal neighbor should be reached in one step");
    }

    @Test
    void straightLinePathNoObstacles() {
        Unit attacker = alive("A", 1, 1);
        Unit target = alive("T", 1, 4);

        List<Edge> path = finder.getTargetPath(attacker, target, list(attacker, target));

        assertPathBasicRules(path, 1, 1, 1, 4);
        // Minimal steps = 3, nodes = 4
        assertEquals(4, path.size(), "Expected a shortest straight-line path");
    }

    @Test
    void ignoresDeadUnitsAsObstacles() {
        Unit attacker = alive("A", 1, 1);
        Unit target = alive("T", 1, 4);

        // Dead unit placed on the direct line; must not block
        Unit deadObstacle = dead("D", 1, 2);

        List<Edge> path = finder.getTargetPath(attacker, target, list(attacker, target, deadObstacle));

        assertPathBasicRules(path, 1, 1, 1, 4);
        assertEquals(4, path.size(), "Dead units must not block the shortest path");
    }

    @Test
    void avoidsAliveObstacleAndStillFindsShortest() {
        Unit attacker = alive("A", 1, 1);
        Unit target = alive("T", 1, 4);

        // Alive obstacle blocks the straight line at (1,2)
        Unit obstacle = alive("O", 1, 2);

        List<Unit> all = list(attacker, target, obstacle);
        List<Edge> path = finder.getTargetPath(attacker, target, all);

        assertPathBasicRules(path, 1, 1, 1, 4);

        // Build blocked map just for assertions (same rules: alive units except attacker/target)
        boolean[][] blocked = new boolean[27][21];
        blocked[1][2] = true;

        assertNoBlockedCells(path, blocked, target.getxCoordinate(), target.getyCoordinate());

        // With diagonals allowed, shortest detour length should be:
        // (1,1)->(0,2)->(1,3)->(1,4)  => 3 steps, 4 nodes
        assertEquals(4, path.size(), "Expected a shortest detour with diagonals");
    }

    @Test
    void returnsEmptyWhenNoRouteExists() {
        Unit attacker = alive("A", 1, 1);
        Unit target = alive("T", 1, 3);

        // Block all neighbor exits from attacker (including diagonals)
        Unit o1 = alive("O1", 0, 0);
        Unit o2 = alive("O2", 0, 1);
        Unit o3 = alive("O3", 0, 2);
        Unit o4 = alive("O4", 1, 0);
        Unit o5 = alive("O5", 1, 2);
        Unit o6 = alive("O6", 2, 0);
        Unit o7 = alive("O7", 2, 1);
        Unit o8 = alive("O8", 2, 2);

        List<Edge> path = finder.getTargetPath(attacker, target, list(attacker, target, o1, o2, o3, o4, o5, o6, o7, o8));

        assertNotNull(path, "Path must not be null");
        assertTrue(path.isEmpty(), "Expected an empty path when no route exists");
    }

    @Test
    void startEqualsTargetReturnsSingleCellPath() {
        Unit attacker = alive("A", 5, 5);
        Unit target = alive("T", 5, 5);

        List<Edge> path = finder.getTargetPath(attacker, target, list(attacker, target));

        assertNotNull(path, "Path must not be null");
        assertEquals(1, path.size(), "Same start/target should return a single-cell path");
        assertEquals(5, path.get(0).getX());
        assertEquals(5, path.get(0).getY());
    }
}
