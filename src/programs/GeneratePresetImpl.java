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

    @Override
    public Army generate(List<Unit> unitList, int maxPoints) {
        // Generate computer preset under constraints:
        // - total points <= maxPoints
        // - max 11 units per unit type
        // - prioritize (baseAttack / cost), then (health / cost)
        // - coordinates are randomly assigned across x=0..2, y=0..20 without duplicates (UI-safe)
        // - print progress logs like the default implementation
        System.err.println(">>> GeneratePresetImpl.generate() called");
        System.err.flush();

        Army army = new Army();
        List<Unit> templates = new ArrayList<>(unitList == null ? List.of() : unitList);

        if (templates.isEmpty() || maxPoints <= 0) {
            System.out.println("Used points: 0");
            return army;
        }

        templates.sort((a, b) -> {
            double aAtk = a.getCost() == 0 ? Double.POSITIVE_INFINITY : (double) a.getBaseAttack() / a.getCost();
            double bAtk = b.getCost() == 0 ? Double.POSITIVE_INFINITY : (double) b.getBaseAttack() / b.getCost();
            int cmp = Double.compare(bAtk, aAtk);
            if (cmp != 0) return cmp;

            double aHp = a.getCost() == 0 ? Double.POSITIVE_INFINITY : (double) a.getHealth() / a.getCost();
            double bHp = b.getCost() == 0 ? Double.POSITIVE_INFINITY : (double) b.getHealth() / b.getCost();
            cmp = Double.compare(bHp, aHp);
            if (cmp != 0) return cmp;

            return b.getUnitType().compareTo(a.getUnitType());
        });

        Map<String, Integer> countByType = new HashMap<>();
        boolean[][] occupied = new boolean[3][21]; // x=0..2, y=0..20
        Random rnd = new Random();

        int usedPoints = 0;

        while (true) {
            Unit chosen = null;

            // Pick the best template that still fits per-type and remaining points
            for (Unit t : templates) {
                if (t == null) continue;

                int cnt = countByType.getOrDefault(t.getUnitType(), 0);
                if (cnt >= 11) continue;

                if (usedPoints + t.getCost() > maxPoints) continue;

                chosen = t;
                break;
            }

            if (chosen == null) break;

            int[] xy = findAvailableCoordinates(occupied, rnd);
            if (xy == null) {
                // No free cells left in 3x21 grid
                break;
            }

            int x = xy[0];
            int y = xy[1];

            int nextIndex = countByType.getOrDefault(chosen.getUnitType(), 0) + 1;
            countByType.put(chosen.getUnitType(), nextIndex);

            // Keep the default naming style: "Type N"
            String name = chosen.getUnitType() + " " + nextIndex;

            Unit u = new Unit(
                    name,
                    chosen.getUnitType(),
                    chosen.getHealth(),
                    chosen.getBaseAttack(),
                    chosen.getCost(),
                    chosen.getAttackType(),
                    chosen.getAttackBonuses(),
                    chosen.getDefenceBonuses(),
                    x,
                    y
            );

            army.getUnits().add(u);
            usedPoints += chosen.getCost();

            System.out.println("Added " + army.getUnits().size() + " unit");
        }

        army.setPoints(usedPoints);
        System.out.println("Used points: " + usedPoints);

        return army;
    }

    private int[] findAvailableCoordinates(boolean[][] occupied, Random rnd) {
        // Try random cells first (fast average case)
        for (int attempt = 0; attempt < 200; attempt++) {
            int y = rnd.nextInt(21); // 0..20
            int x = rnd.nextInt(3);  // 0..2
            if (!occupied[x][y]) {
                occupied[x][y] = true;
                return new int[]{x, y};
            }
        }

        // Fallback: deterministic scan
        for (int y = 0; y < 21; y++) {
            for (int x = 0; x < 3; x++) {
                if (!occupied[x][y]) {
                    occupied[x][y] = true;
                    return new int[]{x, y};
                }
            }
        }

        return null;
    }
}
