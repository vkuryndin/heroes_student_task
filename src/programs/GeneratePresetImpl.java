package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.GeneratePreset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GeneratePresetImpl implements GeneratePreset {

    private static final int MAX_PER_TYPE = 11;
    private static final int GRID_W = 3;   // x: 0..2
    private static final int GRID_H = 21;  // y: 0..20

    // Constant number of attempts -> keeps O(m*n) when m=types, n=max units.
    private static final int RESTARTS = 20;

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

    private long scoreUnit(Unit u) {
        // Additive integer-ish score; consistent with "attack/cost first, health/cost second".
        int c = u.getCost();
        long atkRatio = ((long) u.getBaseAttack() * 1_000_000L) / c;
        long hpRatio = ((long) u.getHealth() * 1_000_000L) / c;
        return atkRatio * 1_000_000_000L + hpRatio;
    }

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
