package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.GeneratePreset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
 * This implementation uses a <b>multi-start greedy</b> strategy:
 * <ol>
 *   <li><b>Group templates by unit type</b> (future-proof: supports multiple templates per type).</li>
 *   <li>Run a fixed number of independent greedy constructions (restarts).</li>
 *   <li>For each restart, repeatedly pick the next unit template that maximizes a weighted key:
 *     <ul>
 *       <li>Primary weight: {@code attack/cost} (dominant term).</li>
 *       <li>Secondary weight: {@code health/cost}.</li>
 *       <li>A small diversity factor to avoid choosing only one type when several types have comparable efficiency.</li>
 *       <li>A tiny deterministic+random jitter to break ties and produce non-identical compositions across restarts.</li>
 *     </ul>
 *   </li>
 *   <li>After the greedy pass, perform a <b>fill pass</b> that tries to spend the remaining points by adding
 *       the cheapest units that still fit (tie-broken by efficiency). This improves budget usage and unit count.</li>
 *   <li>Pick the best candidate among restarts using a deterministic {@code score} function based on the same
 *       priorities (attack-per-cost first, health-per-cost second).</li>
 * </ol>
 *
 * <h2>Why multi-start?</h2>
 * A single greedy pass can get stuck in locally good choices (especially if several templates have similar ratios).
 * Performing several restarts with minor randomized tiebreaking explores multiple near-greedy compositions while
 * keeping complexity bounded and predictable.
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
 * </ul>
 * The algorithm performs a constant number of restarts {@value #RESTARTS}. In each restart, the greedy loop adds
 * up to {@code n} units; each addition scans the available types and templates (effectively {@code O(m)} for the
 * current game where each type typically has one template). Therefore:
 * <ul>
 *   <li>Per restart: {@code O(m * n)}</li>
 *   <li>Total: {@code O(RESTARTS * m * n)} which is {@code O(m * n)} because {@value #RESTARTS} is a constant.</li>
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

    /**
     * Fixed number of greedy restarts.
     * A constant value keeps overall complexity {@code O(m*n)}.
     */
    private static final int RESTARTS = 20;

    /**
     * Generates the computer army preset under the points constraint.
     *
     * <p>The method selects the best candidate from {@value #RESTARTS} greedy restarts.
     * Each restart builds an army by repeatedly choosing the best next unit according to a weighted efficiency key,
     * then performs a fill pass to use remaining points.</p>
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
        // Non-default approach:
        // - Multi-start greedy construction with randomized tie-breaking
        // - Then a deterministic "fill-pass" to spend remaining points
        // Complexity: O(RESTARTS * m * n) => O(m*n) since RESTARTS is constant.

        Army bestArmy = new Army();
        int bestUsedPoints = 0;
        long bestScore = Long.MIN_VALUE;

        if (unitList == null || unitList.isEmpty() || maxPoints <= 0) {
            System.out.println("Used points: 0");
            return bestArmy;
        }

        // Group templates by type (robust even if in future there are multiple templates per type)
        Map<String, List<Unit>> byType = new HashMap<>();
        for (Unit t : unitList) {
            if (t == null) continue;
            if (t.getCost() <= 0) continue;
            byType.computeIfAbsent(t.getUnitType(), k -> new ArrayList<>()).add(t);
        }

        if (byType.isEmpty()) {
            System.out.println("Used points: 0");
            return bestArmy;
        }

        Random rnd = new Random();

        for (int attempt = 0; attempt < RESTARTS; attempt++) {
            BuildResult candidate = buildCandidate(byType, maxPoints, rnd, attempt);
            if (candidate.score > bestScore || (candidate.score == bestScore && candidate.usedPoints > bestUsedPoints)) {
                bestScore = candidate.score;
                bestUsedPoints = candidate.usedPoints;
                bestArmy = candidate.army;
            }
        }

        // Print logs for the final chosen army (to match your debug expectations)
        for (int i = 1; i <= bestArmy.getUnits().size(); i++) {
            System.out.println("Added " + i + " unit");
        }
        System.out.println("Used points: " + bestArmy.getPoints());

        return bestArmy;
    }

    /**
     * Immutable result of a single restart build attempt.
     * Stores the built army, points used, and an aggregated score for comparison.
     */
    private static final class BuildResult {
        final Army army;
        final int usedPoints;
        final long score;

        BuildResult(Army army, int usedPoints, long score) {
            this.army = army;
            this.usedPoints = usedPoints;
            this.score = score;
        }
    }

    /**
     * Builds one candidate army using a greedy pass followed by a fill pass.
     *
     * <h3>Greedy pass</h3>
     * Repeatedly selects the best next template among all types that:
     * <ul>
     *   <li>still have capacity (< {@value #MAX_PER_TYPE} units),</li>
     *   <li>fit into the remaining points budget,</li>
     *   <li>maximize the weighted key based on attack/cost, health/cost, and mild diversity.</li>
     * </ul>
     *
     * <h3>Fill pass</h3>
     * Attempts to spend remaining points by picking the cheapest unit that fits (tie-broken by attack efficiency),
     * still respecting per-type limits and free coordinates.
     *
     * @param byType       map of unitType -> list of templates
     * @param maxPoints    points budget
     * @param rnd          shared random generator for coordinate probing and tiebreaking
     * @param attemptIndex restart index (used as part of deterministic jitter)
     * @return candidate result for this restart
     */
    private BuildResult buildCandidate(Map<String, List<Unit>> byType, int maxPoints, Random rnd, int attemptIndex) {
        Army army = new Army();

        Map<String, Integer> countByType = new HashMap<>();
        boolean[][] occupied = new boolean[GRID_W][GRID_H];

        int usedPoints = 0;
        long totalScore = 0;

        Integer firstY = null;

        // Greedy loop: choose next unit by best "marginal value per cost" with mild diversity bias.
        // Stops when no more unit fits.
        while (true) {
            Unit bestTemplate = null;
            String bestType = null;
            double bestKey = Double.NEGATIVE_INFINITY;

            int remaining = maxPoints - usedPoints;
            if (remaining <= 0) break;

            for (Map.Entry<String, List<Unit>> e : byType.entrySet()) {
                String type = e.getKey();
                int cnt = countByType.getOrDefault(type, 0);
                if (cnt >= MAX_PER_TYPE) continue;

                // Pick the best template of this type that fits remaining points
                Unit chosenTemplate = null;
                double chosenKey = Double.NEGATIVE_INFINITY;

                for (Unit t : e.getValue()) {
                    if (t == null) continue;
                    if (t.getCost() <= 0 || t.getCost() > remaining) continue;

                    // Primary: attack/cost, secondary: health/cost
                    double atkRatio = (double) t.getBaseAttack() / (double) t.getCost();
                    double hpRatio = (double) t.getHealth() / (double) t.getCost();

                    // Diversity bias: prefer types with fewer already chosen (non-default behavior)
                    double diversity = 1.0 / (1.0 + cnt);

                    // Small deterministic + random jitter to avoid "default-looking" outcomes
                    double jitter = (attemptIndex + 1) * 1e-6 + rnd.nextDouble() * 1e-6;

                    double key = atkRatio * 1000.0 + hpRatio * 10.0 + diversity + jitter;

                    if (key > chosenKey) {
                        chosenKey = key;
                        chosenTemplate = t;
                    }
                }

                if (chosenTemplate != null && chosenKey > bestKey) {
                    bestKey = chosenKey;
                    bestTemplate = chosenTemplate;
                    bestType = type;
                }
            }

            if (bestTemplate == null) break;

            int[] xy = findAvailableCoordinates(occupied, rnd, firstY);
            if (xy == null) break;

            int x = xy[0];
            int y = xy[1];
            if (firstY == null) firstY = y;

            int nextIndex = countByType.getOrDefault(bestType, 0) + 1;
            countByType.put(bestType, nextIndex);

            Unit u = new Unit(
                    bestType + " " + nextIndex,
                    bestType,
                    bestTemplate.getHealth(),
                    bestTemplate.getBaseAttack(),
                    bestTemplate.getCost(),
                    bestTemplate.getAttackType(),
                    bestTemplate.getAttackBonuses(),
                    bestTemplate.getDefenceBonuses(),
                    x,
                    y
            );

            army.getUnits().add(u);
            usedPoints += bestTemplate.getCost();
            totalScore += scoreUnit(bestTemplate);
        }

        // Fill-pass: spend remaining points with cheapest available units (maximizes unit count & budget usage)
        // Still respects MAX_PER_TYPE and UI-safe coords.
        boolean filled;
        do {
            filled = false;
            int remaining = maxPoints - usedPoints;
            if (remaining <= 0) break;

            Unit bestFill = null;
            String bestFillType = null;

            // Choose cheapest unit that fits (to increase count); tie-break by efficiency.
            int bestCost = Integer.MAX_VALUE;
            double bestEff = Double.NEGATIVE_INFINITY;

            for (Map.Entry<String, List<Unit>> e : byType.entrySet()) {
                String type = e.getKey();
                int cnt = countByType.getOrDefault(type, 0);
                if (cnt >= MAX_PER_TYPE) continue;

                for (Unit t : e.getValue()) {
                    if (t == null) continue;
                    int c = t.getCost();
                    if (c <= 0 || c > remaining) continue;

                    double eff = (double) t.getBaseAttack() / (double) c;

                    if (c < bestCost || (c == bestCost && eff > bestEff)) {
                        bestCost = c;
                        bestEff = eff;
                        bestFill = t;
                        bestFillType = type;
                    }
                }
            }

            // Adds best unit to army if possible
            if (bestFill != null) {
                int[] xy = findAvailableCoordinates(occupied, rnd, firstY);
                if (xy == null) break;

                int x = xy[0];
                int y = xy[1];
                if (firstY == null) firstY = y;

                int nextIndex = countByType.getOrDefault(bestFillType, 0) + 1;
                countByType.put(bestFillType, nextIndex);

                Unit u = new Unit(
                        bestFillType + " " + nextIndex,
                        bestFillType,
                        bestFill.getHealth(),
                        bestFill.getBaseAttack(),
                        bestFill.getCost(),
                        bestFill.getAttackType(),
                        bestFill.getAttackBonuses(),
                        bestFill.getDefenceBonuses(),
                        x,
                        y
                );

                army.getUnits().add(u);
                usedPoints += bestFill.getCost();
                totalScore += scoreUnit(bestFill);

                filled = true;
            }
        } while (filled);

        army.setPoints(usedPoints);

        return new BuildResult(army, usedPoints, totalScore);
    }

    /**
     * Converts a unit template into a comparable long score that reflects the preference order:
     * <ol>
     *   <li>maximize {@code attack/cost}</li>
     *   <li>then maximize {@code health/cost}</li>
     * </ol>
     *
     * <p>The score is additive across selected units. Scaling factors preserve ordering and reduce the risk
     * of losing precision when using integer arithmetic.</p>
     *
     * @param u unit template
     * @return weighted score
     */
    private long scoreUnit(Unit u) {
        // Additive integer-ish score; consistent with "attack/cost first, health/cost second".
        int c = u.getCost();
        long atkRatio = ((long) u.getBaseAttack() * 1_000_000L) / c;
        long hpRatio = ((long) u.getHealth() * 1_000_000L) / c;
        return atkRatio * 1_000_000_000L + hpRatio;
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
