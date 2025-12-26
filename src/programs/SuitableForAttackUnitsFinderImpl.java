package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.SuitableForAttackUnitsFinder;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Finds units that are currently visible/available to be targeted for an attack.
 *
 * <h2>What this class does</h2>
 * The game engine provides enemy units grouped by their X "columns" (3 columns total).
 * Depending on which side is being targeted (left or right army), the columns have a
 * different meaning of "front", "middle", and "back".
 *
 * <p>This implementation returns all units that are reachable by line-of-sight rules:</p>
 * <ul>
 *   <li><b>Front column</b>: always targetable (if alive).</li>
 *   <li><b>Middle column</b>: targetable only if there is <b>no alive</b> unit in the front column
 *       on the same {@code y} coordinate (i.e., not blocked directly in front).</li>
 *   <li><b>Back column</b>: targetable only if there is <b>no alive</b> unit in the middle column
 *       on the same {@code y} coordinate (i.e., not blocked directly in front).</li>
 * </ul>
 *
 * <h2>Important detail (blocking semantics)</h2>
 * Blocking is checked only against the <b>immediate neighbor column</b>:
 * <ul>
 *   <li>Back is blocked only by Middle (not by Front).</li>
 *   <li>Middle is blocked only by Front.</li>
 * </ul>
 * This matches the common interpretation used by the default game logic for "covering" in 3-column formations.
 *
 * <h2>Algorithmic approach</h2>
 * A typical naive approach would repeatedly scan lists to check whether a unit is blocked,
 * which may introduce extra nested loops. Here we use {@link BitSet} as a compact "visibility mask":
 * <ul>
 *   <li>Build a BitSet for the front column that marks all {@code y} positions occupied by alive units.</li>
 *   <li>Build a BitSet for the middle column that marks all {@code y} positions occupied by alive units.</li>
 *   <li>Filter middle units by checking {@code !frontAliveY.get(y)}.</li>
 *   <li>Filter back units by checking {@code !midAliveY.get(y)}.</li>
 * </ul>
 *
 * <h2>Complexity</h2>
 * Let {@code N} be the total number of units in the 3 target columns.
 * <ul>
 *   <li>Building masks: {@code O(N)}.</li>
 *   <li>Producing result list: {@code O(N)}.</li>
 *   <li>Overall: {@code O(N)} time, {@code O(HEIGHT)} extra memory (BitSets).</li>
 * </ul>
 *
 * <h2>Robustness / edge cases</h2>
 * <ul>
 *   <li>If {@code unitsByRow} is {@code null} or does not contain exactly 3 lists,
 *       the method returns an empty list and prints the expected message.</li>
 *   <li>{@code null} units are ignored.</li>
 *   <li>Units with invalid {@code y} coordinates are ignored for masking decisions.</li>
 *   <li>If no suitable targets are found, an informational message is printed.</li>
 * </ul>
 */
public class SuitableForAttackUnitsFinderImpl implements SuitableForAttackUnitsFinder {

    /**
     * Battlefield height in cells (valid y: 0..20).
     *
     * <p>We use this value to bound-check y-coordinates when building BitSet masks
     * and when checking whether a unit is blocked.</p>
     */
    private static final int HEIGHT = 21;

    /**
     * Returns the list of enemy units that can currently be targeted for an attack.
     *
     * <p>The enemy formation is provided as 3 lists representing the 3 columns (x=0..2) on the target side.
     * The meaning of "front" depends on which side is being targeted:</p>
     * <ul>
     *   <li>If {@code isLeftArmyTarget == true}, the target's front is column index 2 (rightmost),
     *       then middle is 1, back is 0.</li>
     *   <li>If {@code isLeftArmyTarget == false}, the target's front is column index 0 (leftmost),
     *       then middle is 1, back is 2.</li>
     * </ul>
     *
     * @param unitsByRow       list of 3 lists (columns) of units on the target side
     * @param isLeftArmyTarget {@code true} if the target is the left army, {@code false} otherwise
     * @return list of alive units that are visible / not blocked according to the rules
     */
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

        // Defensive validation: the game logic expects exactly 3 columns.
        // If input is malformed, return empty list and print the expected message.
        if (unitsByRow == null || unitsByRow.size() != 3) {
            System.out.println("Unit can not find target for attack!");
            return result;
        }

        // Determine which indices correspond to front/middle/back for the given target side.
        int frontIdx = isLeftArmyTarget ? 2 : 0;
        int midIdx = 1;
        int backIdx = isLeftArmyTarget ? 0 : 2;

        // Extract column lists (may be null; handled by safe()).
        List<Unit> front = unitsByRow.get(frontIdx);
        List<Unit> mid = unitsByRow.get(midIdx);
        List<Unit> back = unitsByRow.get(backIdx);

        // Build compact masks of occupied y-positions for alive units in blocking columns.
        // - Middle units are blocked by Front.
        // - Back units are blocked by Middle.
        BitSet frontAliveY = buildAliveY(front);
        BitSet midAliveY = buildAliveY(mid);

        // 1) Front column: all alive units are always targetable.
        for (Unit u : safe(front)) {
            if (u != null && u.isAlive()) result.add(u);
        }

        // 2) Middle column: alive units are targetable only if NOT blocked by an alive unit in front at same y.
        for (Unit u : safe(mid)) {
            if (u == null || !u.isAlive()) continue;

            int y = u.getyCoordinate();
            // If y is within field bounds and there is no alive front-unit on that y, the unit is visible.
            if (y >= 0 && y < HEIGHT && !frontAliveY.get(y)) {
                result.add(u);
            }
        }

        // 3) Back column: alive units are targetable only if NOT blocked by an alive unit in middle at same y.
        for (Unit u : safe(back)) {
            if (u == null || !u.isAlive()) continue;

            int y = u.getyCoordinate();
            if (y >= 0 && y < HEIGHT && !midAliveY.get(y)) {
                result.add(u);
            }
        }

        // Match the runtime/debug expectations: if no targets are available, print a message.
        if (result.isEmpty()) {
            System.out.println("Unit can not find target for attack!");
        }

        return result;
    }

    /**
     * Builds a {@link BitSet} mask that marks y-positions occupied by alive units in the given list.
     *
     * <p>Only y-coordinates in range {@code [0..HEIGHT-1]} are considered. Invalid y values are ignored.
     * {@code null} lists result in an empty mask.</p>
     *
     * @param units units in a single column (may be {@code null})
     * @return BitSet where bit {@code y} is {@code true} if there is an alive unit at that y
     */
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

    /**
     * Returns a non-null iterable list for convenience.
     *
     * <p>This helper avoids repeated {@code null} checks in loops.</p>
     *
     * @param units input list (may be {@code null})
     * @return {@code units} if non-null, otherwise an immutable empty list
     */
    private List<Unit> safe(List<Unit> units) {
        return units == null ? List.of() : units;
    }
}
