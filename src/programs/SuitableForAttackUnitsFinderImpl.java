package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.SuitableForAttackUnitsFinder;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class SuitableForAttackUnitsFinderImpl implements SuitableForAttackUnitsFinder {

    private static final int HEIGHT = 21;

    @Override
    public List<Unit> getSuitableUnits(List<List<Unit>> unitsByRow, boolean isLeftArmyTarget) {
        // Non-default approach: BitSet visibility masks.
        // Semantics matches the task/default idea:
        // - 3 columns of the target side.
        // - Front column always visible.
        // - Middle column visible if front has no alive unit with same y.
        // - Back column visible if middle has no alive unit with same y.
        // Important: ONLY immediate neighbor blocks (not "any unit in front").

        ArrayList<Unit> result = new ArrayList<>();
        if (unitsByRow == null || unitsByRow.size() != 3) {
            System.out.println("Unit can not find target for attack!");
            return result;
        }

        int frontIdx = isLeftArmyTarget ? 2 : 0;
        int midIdx = 1;
        int backIdx = isLeftArmyTarget ? 0 : 2;

        List<Unit> front = unitsByRow.get(frontIdx);
        List<Unit> mid = unitsByRow.get(midIdx);
        List<Unit> back = unitsByRow.get(backIdx);

        BitSet frontAliveY = buildAliveY(front);
        BitSet midAliveY = buildAliveY(mid);

        for (Unit u : safe(front)) {
            if (u != null && u.isAlive()) result.add(u);
        }

        for (Unit u : safe(mid)) {
            if (u == null || !u.isAlive()) continue;
            int y = u.getyCoordinate();
            if (y >= 0 && y < HEIGHT && !frontAliveY.get(y)) {
                result.add(u);
            }
        }

        for (Unit u : safe(back)) {
            if (u == null || !u.isAlive()) continue;
            int y = u.getyCoordinate();
            if (y >= 0 && y < HEIGHT && !midAliveY.get(y)) {
                result.add(u);
            }
        }

        if (result.isEmpty()) {
            System.out.println("Unit can not find target for attack!");
        }

        return result;
    }

    private BitSet buildAliveY(List<Unit> units) {
        BitSet bs = new BitSet(HEIGHT);
        if (units == null) return bs;
        for (Unit u : units) {
            if (u == null || !u.isAlive()) continue;
            int y = u.getyCoordinate();
            if (y >= 0 && y < HEIGHT) bs.set(y);
        }
        return bs;
    }

    private List<Unit> safe(List<Unit> units) {
        return units == null ? List.of() : units;
    }
}
