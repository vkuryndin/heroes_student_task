package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GeneratePresetLogicTest {

    private final GeneratePresetImpl generator = new GeneratePresetImpl();

    private Unit createUnit(String name, int cost, int attack, int health) {
        return new Unit(name, name, health, attack, cost, "melee",
                Collections.emptyMap(), Collections.emptyMap(), 0, 0);
    }

    @Test
    void generatesEfficientArmy_PrioritizesAttackCostRatio() {
        // Create two types of units:
        // 1. "Elite": Cost 10, Attack 100 (Ratio = 10.0) -> Very efficient
        // 2. "Trash": Cost 10, Attack 1   (Ratio = 0.1)  -> Inefficient
        Unit elite = createUnit("Elite", 10, 100, 100);
        Unit trash = createUnit("Trash", 10, 1, 100);

        List<Unit> input = List.of(elite, trash);

        // Provide a budget for ~15 units (150 points).
        // The algorithm should first buy the maximum number of "Elite" units (11),
        // and only then pick "Trash" units with the remaining points.
        Army result = generator.generate(input, 150);

        Map<String, Long> counts = result.getUnits().stream()
                .collect(Collectors.groupingBy(Unit::getUnitType, Collectors.counting()));

        // Verify that we picked the maximum number of efficient units
        assertEquals(11, counts.getOrDefault("Elite", 0L),
                "Algorithm should pick the maximum (11) of the most efficient units");

        // Verify that the remaining points were used to buy less efficient units
        long trashCount = counts.getOrDefault("Trash", 0L);
        assertTrue(trashCount > 0, "Less efficient units should be bought with remaining points");
        assertEquals(150, result.getPoints(), "The entire budget should be utilized");
    }

    @Test
    void handlesGridSaturation_StopsWhenNoSpaceLeft() {
        // Scenario: Huge budget (10,000), but limited space on the grid (3x21 = 63 cells).
        // Unit costs 1 point.
        Unit cheap = createUnit("Cheap", 1, 1, 1);

        // To bypass the "11 per type" limit, create 10 different unit types
        List<Unit> manyTypes = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> createUnit("Type" + i, 1, 1, 1))
                .toList();

        Army result = generator.generate(manyTypes, 10000);

        assertEquals(63, result.getUnits().size(),
                "Cannot create more than 63 units (grid size 3x21)");
    }
}