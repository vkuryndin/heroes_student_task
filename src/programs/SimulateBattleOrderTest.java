package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.PrintBattleLog;
import com.battle.heroes.army.programs.Program;
import com.battle.heroes.util.GameSpeedUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulateBattleOrderTest {

    @Test
    void unitsActInDescendingOrderOfAttack() throws Exception {
        SimulateBattleImpl simulator = new SimulateBattleImpl();
        List<String> attackLog = new ArrayList<>();

        // 1. Create a logger with a "fuse".
        // As soon as 4 attacks are recorded (a full round for 4 units), throw a "Stop" exception.
        PrintBattleLog logger = (attacker, target) -> {
            if (attacker != null) {
                attackLog.add(attacker.getName());
            }
            // IMPORTANT: Forcefully stop the battle after 4 actions to prevent infinite loop
            if (attackLog.size() >= 4) {
                throw new RuntimeException("Stop");
            }
        };
        injectLogger(simulator, logger);

        // 2. Setup Player Army
        // P_Weak (Atk 10), P_Strong (Atk 100)
        Army playerArmy = new Army();
        Unit pWeak = createUnit("P_Weak", 10);
        Unit pStrong = createUnit("P_Strong", 100);
        playerArmy.getUnits().add(pWeak);
        playerArmy.getUnits().add(pStrong);

        // 3. Setup Computer Army
        // C_Medium (Atk 50), C_Weakest (Atk 5)
        Army computerArmy = new Army();
        Unit cMedium = createUnit("C_Medium", 50);
        Unit cWeakest = createUnit("C_Weakest", 5);
        computerArmy.getUnits().add(cMedium);
        computerArmy.getUnits().add(cWeakest);

        // 4. Setup mock attack (returns a target but does not kill)
        setupMockAttack(pWeak);
        setupMockAttack(pStrong);
        setupMockAttack(cMedium);
        setupMockAttack(cWeakest);

        // 5. Run simulation and catch our exception
        try {
            simulator.simulate(playerArmy, computerArmy);
        } catch (RuntimeException e) {
            // If this is our "Stop" exception, the test reached the desired point.
            // If it's any other exception, rethrow it (test fails).
            if (!"Stop".equals(e.getMessage())) {
                throw e;
            }
        }

        // 6. Verify the order
        // Expected order (by Attack descending):
        // 1. P_Strong (100) - Player
        // 2. C_Medium (50)  - Computer
        // 3. P_Weak (10)    - Player
        // 4. C_Weakest (5)  - Computer
        List<String> expected = List.of("P_Strong", "C_Medium", "P_Weak", "C_Weakest");

        assertEquals(4, attackLog.size(), "Should be exactly 4 attacks logged");
        assertEquals(expected, attackLog);
    }

    // --- Helper Methods ---

    private Unit createUnit(String name, int baseAttack) {
        // Health 1000 so they don't die accidentally
        return new Unit(name, "Type", 1000, baseAttack, 10, "melee",
                Collections.emptyMap(), Collections.emptyMap(), 0, 0);
    }

    private void setupMockAttack(Unit u) {
        // A program that simply returns a target so the simulation considers the move successful
        u.setProgram(new Program(u, null, null, new GameSpeedUtil(0)) {
            @Override
            public Unit attack() {
                return new Unit("DummyTarget", "", 1, 1, 1, "", null, null, 0, 0);
            }
        });
    }

    // Reflection to inject logger, since there is no setter
    private void injectLogger(SimulateBattleImpl sim, PrintBattleLog log) throws Exception {
        Field f = SimulateBattleImpl.class.getDeclaredField("printBattleLog");
        f.setAccessible(true);
        f.set(sim, log);
    }
}