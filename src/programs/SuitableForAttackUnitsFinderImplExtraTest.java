package programs;

import com.battle.heroes.army.Unit;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SuitableForAttackUnitsFinderImplExtraTest {

    private static Unit alive(String name, int x, int y) {
        return new Unit(
                name, "T", 10, 1, 1, "melee",
                Collections.emptyMap(), Collections.emptyMap(),
                x, y
        );
    }

    private static String captureOut(Runnable r) {
        PrintStream old = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try { r.run(); } finally { System.setOut(old); }
        return baos.toString();
    }

    @Test
    void nullOrWrongSizeInputReturnsEmptyAndLogs() {
        SuitableForAttackUnitsFinderImpl f = new SuitableForAttackUnitsFinderImpl();

        String out1 = captureOut(() -> assertTrue(f.getSuitableUnits(null, false).isEmpty()));
        assertTrue(out1.contains("Unit can not find target for attack!"));

        String out2 = captureOut(() -> assertTrue(f.getSuitableUnits(List.of(List.of()), false).isEmpty()));
        assertTrue(out2.contains("Unit can not find target for attack!"));
    }

    @Test
    void outOfBoundsYDoesNotBlockVisibilityMask() {
        SuitableForAttackUnitsFinderImpl f = new SuitableForAttackUnitsFinderImpl();

        // right target: front index 0 blocks mid only if same y within [0..20]
        Unit frontOut = alive("FrontOut", 0, 999);
        Unit mid = alive("Mid", 1, 5);

        List<List<Unit>> cols = List.of(
                List.of(frontOut),
                List.of(mid),
                List.of()
        );

        List<Unit> res = f.getSuitableUnits(cols, false);

        assertTrue(res.contains(frontOut), "Front units are always visible (if alive)");
        assertTrue(res.contains(mid), "Front with out-of-bounds y must NOT block mid");
    }
}
