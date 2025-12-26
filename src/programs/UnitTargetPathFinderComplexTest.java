package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.Edge;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnitTargetPathFinderComplexTest {

    private final UnitTargetPathFinderImpl finder = new UnitTargetPathFinderImpl();

    @Test
    void findsPathAroundWall() {
        // Scenario: A wall of units blocks the direct path.
        // A . .
        // # # #
        // . T .

        Unit attacker = createUnit(1, 0); // (1, 0)
        Unit target = createUnit(1, 2);   // (1, 2)

        List<Unit> obstacles = new ArrayList<>();
        obstacles.add(attacker);
        obstacles.add(target);
        // Wall at Y=1
        obstacles.add(createUnit(0, 1));
        obstacles.add(createUnit(1, 1));
        obstacles.add(createUnit(2, 1));

        List<Edge> path = finder.getTargetPath(attacker, target, obstacles);

        assertFalse(path.isEmpty(), "Path should exist (detour)");

        // Verify that path does not go through the wall
        for (Edge e : path) {
            boolean hitsWall = (e.getY() == 1 && (e.getX() >= 0 && e.getX() <= 2));
            assertFalse(hitsWall, "Path must not go through obstacles (0,1), (1,1), (2,1)");
        }

        // Check length: direct path is 2 steps, detour must be longer
        assertTrue(path.size() > 2);
    }

    private Unit createUnit(int x, int y) {
        return new Unit("U", "T", 10, 1, 1, "m",
                Collections.emptyMap(), Collections.emptyMap(), x, y);
    }
}