package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GeneratePresetImplExtraTest {

    private final GeneratePresetImpl generator = new GeneratePresetImpl();

    private static Unit template(String type, int hp, int atk, int cost, String attackType) {
        return new Unit(
                type + "_template_" + hp + "_" + atk + "_" + cost,
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

    @Test
    void doesNotMutateInputListOrderOrContents() {
        Unit a = template("A", 10, 10, 10, "melee");
        Unit b = template("B", 10, 10, 10, "melee");
        Unit c = template("C", 10, 10, 10, "melee");

        ArrayList<Unit> input = new ArrayList<>(List.of(a, b, c));
        List<Unit> snapshot = new ArrayList<>(input);

        generator.generate(input, 50);

        assertEquals(snapshot, input, "Generator must not reorder/mutate the input templates list");
    }

    @Test
    void choosesBestTemplateWhenMultipleTemplatesHaveSameType() {
        // Same type, different efficiency: should pick best within that type.
        Unit bad = template("Archer", 100, 10, 10, "ranged");  // atk/cost = 1.0
        Unit good = template("Archer", 100, 20, 10, "ranged"); // atk/cost = 2.0 (better)
        Unit other = template("Knight", 100, 1, 10, "melee");

        // Make sure budget allows multiple Archers.
        Army army = generator.generate(List.of(bad, good, other), 200);

        // All generated Archers must match GOOD template stats.
        for (Unit u : army.getUnits()) {
            if ("Archer".equals(u.getUnitType())) {
                assertEquals(good.getBaseAttack(), u.getBaseAttack());
                assertEquals(good.getHealth(), u.getHealth());
                assertEquals(good.getCost(), u.getCost());
                assertEquals(good.getAttackType(), u.getAttackType());
            }
        }
    }

    @Test
    void ignoresNullAndNonPositiveCostTemplatesAndDoesNotCrash() {
        Unit ok = template("OkType", 10, 10, 5, "melee");
        Unit zero = template("ZeroCost", 10, 10, 0, "melee");
        Unit neg = template("NegCost", 10, 10, -5, "melee");

        List<Unit> input = Arrays.asList(null, ok, zero, neg);

        Army army = generator.generate(input, 100);

        assertNotNull(army);
        assertNotNull(army.getUnits());
        // Must contain only OkType (or be empty if something odd happens)
        for (Unit u : army.getUnits()) {
            assertNotNull(u);
            assertEquals("OkType", u.getUnitType());
            assertTrue(u.getCost() > 0);
        }
        assertTrue(army.getPoints() <= 100);
    }
}
