package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GeneratePresetImplTest {

    private final GeneratePresetImpl generator = new GeneratePresetImpl();

    private static Unit template(String type, int hp, int atk, int cost, String attackType) {
        return new Unit(
                type + "_template",
                type,
                hp,
                atk,
                cost,
                attackType,
                Collections.emptyMap(),
                Collections.emptyMap(),
                0,
                0
        );
    }

    private static int sumCosts(List<Unit> units) {
        int s = 0;
        for (Unit u : units) {
            if (u != null) s += u.getCost();
        }
        return s;
    }

    @Test
    void returnsEmptyArmyWhenUnitListEmpty() {
        Army army = generator.generate(Collections.emptyList(), 1500);
        assertNotNull(army);
        assertNotNull(army.getUnits());
        assertTrue(army.getUnits().isEmpty());
        assertEquals(0, army.getPoints());
    }

    @Test
    void returnsEmptyArmyWhenMaxPointsNonPositive() {
        List<Unit> templates = List.of(
                template("Archer", 30, 8, 50, "ranged"),
                template("Knight", 50, 10, 100, "melee")
        );

        Army army0 = generator.generate(templates, 0);
        assertNotNull(army0);
        assertTrue(army0.getUnits().isEmpty());
        assertEquals(0, army0.getPoints());

        Army armyNeg = generator.generate(templates, -10);
        assertNotNull(armyNeg);
        assertTrue(armyNeg.getUnits().isEmpty());
        assertEquals(0, armyNeg.getPoints());
    }

    @Test
    void neverExceedsMaxPoints_pointsFieldMatchesSum() {
        List<Unit> templates = List.of(
                template("Archer", 30, 8, 50, "ranged"),
                template("Knight", 50, 12, 120, "melee"),
                template("Pikeman", 40, 10, 90, "melee"),
                template("Swordsman", 45, 11, 110, "melee")
        );

        int maxPoints = 500;
        Army army = generator.generate(templates, maxPoints);

        assertNotNull(army);
        assertNotNull(army.getUnits());

        int sum = sumCosts(army.getUnits());
        assertEquals(sum, army.getPoints(), "Army.points must equal the sum of unit costs");
        assertTrue(sum <= maxPoints, "Total points must not exceed maxPoints");
    }

    @Test
    void respectsPerTypeLimitAtMost11() {
        // Use very cheap cost to allow the generator to potentially exceed 11 without the limit.
        List<Unit> templates = List.of(
                template("Archer", 30, 8, 1, "ranged"),
                template("Knight", 50, 12, 1, "melee"),
                template("Pikeman", 40, 10, 1, "melee"),
                template("Swordsman", 45, 11, 1, "melee")
        );

        Army army = generator.generate(templates, 1500);

        Map<String, Long> counts = army.getUnits().stream()
                .collect(Collectors.groupingBy(Unit::getUnitType, Collectors.counting()));

        for (Map.Entry<String, Long> e : counts.entrySet()) {
            assertTrue(e.getValue() <= 11, "Type " + e.getKey() + " must not exceed 11 units");
        }
    }

    @Test
    void coordinatesAreWithinBoundsAndUnique() {
        List<Unit> templates = List.of(
                template("Archer", 30, 8, 50, "ranged"),
                template("Knight", 50, 12, 120, "melee"),
                template("Pikeman", 40, 10, 90, "melee"),
                template("Swordsman", 45, 11, 110, "melee")
        );

        Army army = generator.generate(templates, 1500);

        Set<String> seen = new HashSet<>();
        for (Unit u : army.getUnits()) {
            assertNotNull(u);

            int x = u.getxCoordinate();
            int y = u.getyCoordinate();

            assertTrue(x >= 0 && x <= 2, "X must be in [0..2], got " + x);
            assertTrue(y >= 0 && y <= 20, "Y must be in [0..20], got " + y);

            String key = x + "," + y;
            assertTrue(seen.add(key), "Duplicate coordinate cell detected: " + key);
        }
    }

    @Test
    void allUnitsUseOnlyProvidedTypesAndTemplateStats() {
        Unit archer = template("Archer", 30, 8, 50, "ranged");
        Unit knight = template("Knight", 50, 12, 120, "melee");
        Unit pikeman = template("Pikeman", 40, 10, 90, "melee");
        Unit swordsman = template("Swordsman", 45, 11, 110, "melee");

        List<Unit> templates = List.of(archer, knight, pikeman, swordsman);

        // Build per-type allowed templates (in case in future there are multiple templates per type).
        Map<String, List<Unit>> byType = new HashMap<>();
        for (Unit t : templates) {
            byType.computeIfAbsent(t.getUnitType(), k -> new ArrayList<>()).add(t);
        }

        Army army = generator.generate(templates, 1500);

        for (Unit u : army.getUnits()) {
            assertNotNull(u);

            List<Unit> allowed = byType.get(u.getUnitType());
            assertNotNull(allowed, "Unit type must come from templates list");

            boolean matchesAnyTemplate = false;
            for (Unit t : allowed) {
                if (u.getHealth() == t.getHealth()
                        && u.getBaseAttack() == t.getBaseAttack()
                        && u.getCost() == t.getCost()
                        && Objects.equals(u.getAttackType(), t.getAttackType())
                ) {
                    matchesAnyTemplate = true;
                    break;
                }
            }

            assertTrue(matchesAnyTemplate, "Unit stats must match one of the templates for its type");
        }
    }

    @Test
    void returnsEmptyArmyWhenMaxPointsBelowCheapestUnit() {
        List<Unit> templates = List.of(
                template("Archer", 30, 8, 100, "ranged"),
                template("Knight", 50, 12, 200, "melee")
        );

        Army army = generator.generate(templates, 50);

        assertNotNull(army);
        assertNotNull(army.getUnits());
        assertTrue(army.getUnits().isEmpty(), "No unit should fit into maxPoints");
        assertEquals(0, army.getPoints());
    }
}
