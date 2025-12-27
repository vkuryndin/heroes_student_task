package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.GeneratePreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;

/**
 * Generates a preset (composition) of the computer army under a points budget.
 *
 * <h2>Goal</h2>
 * Build an {@link Army} that:
 * <ul>
 *   <li>Does not exceed {@code maxPoints} total cost.</li>
 *   <li>Respects the per-type cap: at most {@value #MAX_PER_TYPE} units for each {@link Unit#getUnitType()}.</li>
 *   <li>Places each generated unit onto a 3x21 grid (x in [0..2], y in [0..20]) without collisions.</li>
 *   <li>Prioritizes effectiveness primarily by {@code attack/cost} ratio and secondarily by {@code health/cost} ratio.</li>
 * </ul>
 *
 * <h2>High-level idea (algorithm)</h2>
 * This implementation uses a <b>pre-sorted greedy</b> strategy:
 * <ol>
 *   <li><b>Group templates by unit type</b> (future-proof: supports multiple templates per type).</li>
 *   <li><b>Build a priority-ordered set</b> of templates using the strict key:
 *     <ul>
 *       <li><b>Primary criterion:</b> {@code attack/cost} (dominant, strict).</li>
 *       <li><b>Secondary criterion:</b> {@code health/cost} (used only when {@code attack/cost} ties).</li>
 *       <li><b>Tie-breaking:</b> deterministic tie-breakers (used only when both ratios are exactly equal).</li>
 *     </ul>
 *   </li>
 *   <li>Iteratively pick the current best template from the set that still fits the remaining budget and
 *       respects per-type cap; assign a free coordinate and add a new {@link Unit}.</li>
 * </ol>
 *
 * <h2>Why pre-sorting + filtering?</h2>
 * A naive greedy implementation may re-scan all types/templates on every added unit (O(n*m)).
 * Here we keep a priority-ordered set and <b>monotonically</b> remove templates that no longer fit the remaining
 * points budget, so each template is removed at most once.
 *
 * <h2>Coordinate placement</h2>
 * Each created unit receives unique coordinates in a 3x21 grid:
 * <ul>
 *   <li>Random probing first (bounded attempts).</li>
 *   <li>Deterministic scan as a fallback, guaranteeing placement if any cell is free.</li>
 * </ul>
 * Additionally, the first chosen row {@code firstY} is avoided for some early random attempts to reduce the chance
 * of clustering many units on the same y-coordinate (a UI/visualization edge case).
 *
 * <h2>Complexity</h2>
 * Let:
 * <ul>
 *   <li>{@code m} be the number of unit types.</li>
 *   <li>{@code n} be the maximum number of units in the army.</li>
 *   <li>{@code T} be the number of templates (typically {@code T ~ m}).</li>
 * </ul>
 * The algorithm:
 * <ul>
 *   <li>Builds and sorts template metadata once: {@code O(T log T)}.</li>
 *   <li>Each template is removed from the candidate set at most once: total {@code O(T log T)}.</li>
 *   <li>Each added unit selects the best candidate from a {@code TreeSet}: {@code O(log T)} per unit.</li>
 * </ul>
 * Therefore total complexity is:
 * <ul>
 *   <li>{@code O(T log T + n log T)} which is faster than {@code O(n * m)} for typical inputs.</li>
 * </ul>
 * Coordinate selection is bounded by constants (3x21 grid and fixed attempt limits), so it does not change the
 * asymptotic complexity.
 *
 * <h2>Notes / assumptions</h2>
 * <ul>
 *   <li>This class treats the incoming {@code unitList} as a set of templates and creates new {@link Unit} objects
 *       copying template stats.</li>
 *   <li>Templates with non-positive cost are ignored.</li>
 *   <li>All units are placed in x=[0..2], y=[0..20].</li>
 *   <li>Console logs are produced to match the expected debug output in the runtime environment.</li>
 * </ul>
 */
public class GeneratePresetImpl implements GeneratePreset {

    /** Maximum number of units allowed per unit type. */
    private static final int MAX_PER_TYPE = 11;

    /** Grid width (x: 0..2). */
    private static final int GRID_W = 3;

    /** Grid height (y: 0..20). */
    private static final int GRID_H = 21;

    /** Max number of cells available for placement. */
    private static final int MAX_CELLS = GRID_W * GRID_H;

    /**
     * Internal wrapper for a template with stable ordering and fast removals.
     */
    private static final class TemplateEntry {
        final Unit template;
        final String type;
        final int cost;
        final int id; // guarantees total order (TreeSet must not consider distinct entries equal)

        TemplateEntry(Unit template, String type, int cost, int id) {
            this.template = template;
            this.type = type;
            this.cost = cost;
            this.id = id;
        }
    }

    /**
     * Generates the computer army preset under the points constraint.
     *
     * <p>The method greedily builds an army by repeatedly choosing the best next unit according to the strict task
     * priority (attack/cost first, health/cost second). Selection is implemented with a priority-ordered set and
     * monotonic filtering by remaining budget. Coordinates are assigned without collisions.</p>
     *
     * <p>Logs:
     * <ul>
     *   <li>{@code "Added i unit"} is printed for each unit in the final selected army.</li>
     *   <li>{@code "Used points: X"} is printed once at the end.</li>
     * </ul>
     * </p>
     *
     * @param unitList  list of unit templates; typically contains one template per unit type
     * @param maxPoints maximum total cost allowed for the resulting army
     * @return generated {@link Army} for the computer side (may be empty if no unit fits)
     */
    @Override
    public Army generate(List<Unit> unitList, int maxPoints) {
        Army army = new Army();

        if (unitList == null || unitList.isEmpty() || maxPoints <= 0) {
            army.setPoints(0);
            System.out.println("Used points: 0");
            return army;
        }

        // Group templates by type (robust even if in future there are multiple templates per type)
        Map<String, List<Unit>> byType = new HashMap<>();
        for (Unit t : unitList) {
            if (t == null) continue;
            if (t.getCost() <= 0) continue;
            byType.computeIfAbsent(t.getUnitType(), k -> new ArrayList<>()).add(t);
        }

        if (byType.isEmpty()) {
            army.setPoints(0);
            System.out.println("Used points: 0");
            return army;
        }

        // Build entries, a priority-ordered candidate set, and a list sorted by cost descending
        // for monotonic budget filtering.
        Map<String, List<TemplateEntry>> entriesByType = new HashMap<>();
        ArrayList<TemplateEntry> byCostDesc = new ArrayList<>();
        TreeSet<TemplateEntry> candidates = new TreeSet<>(this::compareByTaskPriority);

        int idSeq = 1;
        for (Map.Entry<String, List<Unit>> e : byType.entrySet()) {
            String type = e.getKey();
            for (Unit t : e.getValue()) {
                if (t == null) continue;
                int cost = t.getCost();
                if (cost <= 0 || cost > maxPoints) continue; // never fits -> skip early

                TemplateEntry te = new TemplateEntry(t, type, cost, idSeq++);
                candidates.add(te);
                byCostDesc.add(te);
                entriesByType.computeIfAbsent(type, k -> new ArrayList<>()).add(te);
            }
        }

        if (candidates.isEmpty()) {
            army.setPoints(0);
            System.out.println("Used points: 0");
            return army;
        }

        byCostDesc.sort((a, b) -> Integer.compare(b.cost, a.cost)); // descending cost

        Random rnd = new Random();
        boolean[][] occupied = new boolean[GRID_W][GRID_H];
        Map<String, Integer> countByType = new HashMap<>();

        int usedPoints = 0;
        Integer firstY = null;

        int costPtr = 0; // pointer into byCostDesc

        while (usedPoints < maxPoints && army.getUnits().size() < MAX_CELLS) {
            int remaining = maxPoints - usedPoints;
            if (remaining <= 0) break;

            // Monotonically remove templates that no longer fit the remaining budget.
            while (costPtr < byCostDesc.size() && byCostDesc.get(costPtr).cost > remaining) {
                candidates.remove(byCostDesc.get(costPtr));
                costPtr++;
            }

            if (candidates.isEmpty()) break;

            TemplateEntry best = candidates.first();
            if (best == null) break;

            int cnt = countByType.getOrDefault(best.type, 0);
            if (cnt >= MAX_PER_TYPE) {
                // Defensive: if cap reached, remove all templates of this type and continue.
                List<TemplateEntry> toRemove = entriesByType.get(best.type);
                if (toRemove != null) {
                    for (TemplateEntry te : toRemove) candidates.remove(te);
                }
                continue;
            }

            // Should not happen due to filtering, but keep defensive guard.
            if (best.cost > remaining) {
                candidates.remove(best);
                continue;
            }

            int[] xy = findAvailableCoordinates(occupied, rnd, firstY);
            if (xy == null) break;

            int x = xy[0];
            int y = xy[1];
            if (firstY == null) firstY = y;

            int nextIndex = cnt + 1;
            countByType.put(best.type, nextIndex);

            Unit t = best.template;
            Unit u = new Unit(
                    best.type + " " + nextIndex,
                    best.type,
                    t.getHealth(),
                    t.getBaseAttack(),
                    t.getCost(),
                    t.getAttackType(),
                    t.getAttackBonuses(),
                    t.getDefenceBonuses(),
                    x,
                    y
            );

            army.getUnits().add(u);
            usedPoints += best.cost;

            // If type cap reached, remove all templates of this type from candidates.
            if (nextIndex >= MAX_PER_TYPE) {
                List<TemplateEntry> toRemove = entriesByType.get(best.type);
                if (toRemove != null) {
                    for (TemplateEntry te : toRemove) candidates.remove(te);
                }
            }
        }

        army.setPoints(usedPoints);

        for (int i = 1; i <= army.getUnits().size(); i++) {
            System.out.println("Added " + i + " unit");
        }
        System.out.println("Used points: " + army.getPoints());

        return army;
    }

    /**
     * Strict task-priority comparison between two templates:
     * <ol>
     *   <li>maximize {@code attack/cost}</li>
     *   <li>then maximize {@code health/cost}</li>
     * </ol>
     *
     * <p>Ratio comparisons are done without {@code double} using cross-multiplication:
     * {@code aAtk/aCost > bAtk/bCost}  ⇔  {@code aAtk*bCost > bAtk*aCost}.</p>
     *
     * <p>If both ratios are exactly equal, we apply deterministic tie-breakers.
     * This does not violate the task priority, because it activates only when both primary and secondary
     * criteria are equal.</p>
     *
     * <p><b>Important:</b> This comparator guarantees a total order (last tie-break by {@code id}),
     * which is required for {@link TreeSet} to store all distinct templates.</p>
     */
    private int compareByTaskPriority(TemplateEntry ea, TemplateEntry eb) {
        if (ea == eb) return 0;
        if (ea == null) return 1;
        if (eb == null) return -1;

        Unit a = ea.template;
        Unit b = eb.template;

        int aCost = ea.cost;
        int bCost = eb.cost;

        // 1) attack/cost (DESC)
        long leftAtk = (long) a.getBaseAttack() * (long) bCost;
        long rightAtk = (long) b.getBaseAttack() * (long) aCost;
        if (leftAtk != rightAtk) return leftAtk > rightAtk ? -1 : 1;

        // 2) health/cost (DESC)
        long leftHp = (long) a.getHealth() * (long) bCost;
        long rightHp = (long) b.getHealth() * (long) aCost;
        if (leftHp != rightHp) return leftHp > rightHp ? -1 : 1;

        // 3) deterministic tie-breakers (stable, do not affect task priority)
        if (a.getBaseAttack() != b.getBaseAttack()) return a.getBaseAttack() > b.getBaseAttack() ? -1 : 1;
        if (a.getHealth() != b.getHealth()) return a.getHealth() > b.getHealth() ? -1 : 1;

        // Prefer cheaper if absolutely identical by ratios and raw stats.
        if (aCost != bCost) return Integer.compare(aCost, bCost);

        // Stable by type
        if (ea.type != null && eb.type != null && !ea.type.equals(eb.type)) {
            int c = ea.type.compareTo(eb.type);
            if (c != 0) return c;
        }

        // Final total-order tie-break: unique id
        return Integer.compare(ea.id, eb.id);
    }

    /**
     * Finds a free coordinate cell in the 3x21 preset placement grid.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Try up to 300 random samples.</li>
     *   <li>If sampling fails, do a deterministic scan to find the first free cell.</li>
     * </ol>
     *
     * <p>To avoid early clustering, if {@code firstY} is already selected, the method temporarily avoids choosing
     * that same y-coordinate for some of the early random attempts.</p>
     *
     * @param occupied grid of already used cells
     * @param rnd      random generator
     * @param firstY   y-coordinate of the first placed unit in this candidate (may be null)
     * @return int array {@code [x, y]} if a cell is found, otherwise {@code null}
     */
    private int[] findAvailableCoordinates(boolean[][] occupied, Random rnd, Integer firstY) {
        for (int attempt = 0; attempt < 300; attempt++) {
            int y = rnd.nextInt(GRID_H);
            int x = rnd.nextInt(GRID_W);

            // Avoid placing first few units on the same y to reduce UI edge cases
            if (firstY != null && attempt < 120 && y == firstY) continue;

            if (!occupied[x][y]) {
                occupied[x][y] = true;
                return new int[]{x, y};
            }
        }

        // Fallback deterministic scan
        if (firstY != null) {
            for (int y = 0; y < GRID_H; y++) {
                if (y == firstY) continue;
                for (int x = 0; x < GRID_W; x++) {
                    if (!occupied[x][y]) {
                        occupied[x][y] = true;
                        return new int[]{x, y};
                    }
                }
            }
        }

        for (int y = 0; y < GRID_H; y++) {
            for (int x = 0; x < GRID_W; x++) {
                if (!occupied[x][y]) {
                    occupied[x][y] = true;
                    return new int[]{x, y};
                }
            }
        }

        return null;
    }
}
