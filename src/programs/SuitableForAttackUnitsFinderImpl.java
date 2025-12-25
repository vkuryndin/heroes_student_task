package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.SuitableForAttackUnitsFinder;

import java.util.ArrayList;
import java.util.List;

public class SuitableForAttackUnitsFinderImpl implements SuitableForAttackUnitsFinder {

    @Override
    public List<Unit> getSuitableUnits(List<List<Unit>> unitsByRow, boolean isLeftArmyTarget) {
        // Optimized version of the default logic.
        //
        // unitsByRow contains 3 columns of the TARGET army units:
        //   - for right-side army: x = 24..26 (passed as 3 lists)
        //   - for left-side army:  x = 0..2   (passed as 3 lists)
        //
        // A unit is suitable if it is NOT blocked by an alive unit in the adjacent column
        // closer to the attacker on the same y-coordinate.
        //
        // Important: the default checks ONLY the immediate neighbor column, not "any unit in front".
        // This allows back-column units to be targetable when the middle column is empty at that y.

        ArrayList<Unit> result = new ArrayList<>();
        if (unitsByRow == null || unitsByRow.isEmpty()) {
            System.out.println("Unit can not find target for attack!");
            return result;
        }

        // HEIGHT is 21 => y in [0..20]
        boolean[] prevColAliveY = new boolean[21];

        // Determine scan direction:
        // - If target is the left army, attacker is on the right => front column is index 2, then 1, then 0.
        // - If target is the right army, attacker is on the left => front column is index 0, then 1, then 2.
        int startCol = isLeftArmyTarget ? unitsByRow.size() - 1 : 0;
        int step = isLeftArmyTarget ? -1 : 1;

        for (int col = startCol; col >= 0 && col < unitsByRow.size(); col += step) {
            List<Unit> columnUnits = unitsByRow.get(col);
            if (columnUnits == null || columnUnits.isEmpty()) {
                // Even if the column is empty, it still affects blocking for the next column:
                // empty column => no y is blocked by it.
                prevColAliveY = new boolean[21];
                continue;
            }

            // Collect alive y in the current column (for the next iteration)
            boolean[] currentColAliveY = new boolean[21];

            boolean isFrontColumn = (col == startCol);

            for (Unit u : columnUnits) {
                if (u == null || !u.isAlive()) continue;

                int y = u.getyCoordinate();
                if (y < 0 || y >= 21) continue;

                currentColAliveY[y] = true;

                // Front column units are always suitable.
                // Back columns: suitable only if NOT blocked by an alive unit in the previous (front-neighbor) column.
                if (isFrontColumn || !prevColAliveY[y]) {
                    result.add(u);
                }
            }

            prevColAliveY = currentColAliveY;
        }

        if (result.isEmpty()) {
            System.out.println("Unit can not find target for attack!");
        }

        return result;
    }
}
