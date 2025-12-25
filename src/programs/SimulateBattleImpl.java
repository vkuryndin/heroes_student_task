package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.PrintBattleLog;
import com.battle.heroes.army.programs.SimulateBattle;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SimulateBattleImpl implements SimulateBattle {
    private PrintBattleLog printBattleLog; // Must be used after each attack

    @Override
    public void simulate(Army playerArmy, Army computerArmy) throws InterruptedException {
        // "No surprises" version aligned to the task rules:
        // - Round-based combat.
        // - Each round: units are ordered by baseAttack descending.
        // - Turns alternate between armies. If one army has no units left to act, the other continues.
        // - If a unit dies before it has acted in the current round, it must be removed immediately
        //   and turn order must be recalculated (we rebuild queues).
        // - After every attack attempt we call printBattleLog.printBattleLog(attacker, target).
        // - Battle ends ONLY when one side has no alive units remaining (same behavior as default).
        // - Prints "Round N is over!" logs like the default implementation.

        if (playerArmy == null || computerArmy == null) return;

        // If one of the armies is empty/already dead, nothing to simulate.
        if (!hasAlive(playerArmy) || !hasAlive(computerArmy)) {
            System.out.println("Battle is over!");
            return;
        }

        int round = 0;

        while (hasAlive(playerArmy) && hasAlive(computerArmy)) {
            round++;

            // Units that have already acted in the current round (both armies)
            HashSet<Unit> actedThisRound = new HashSet<>();

            // Rebuild loop: if a not-yet-acted unit dies -> rebuild queues immediately.
            while (true) {
                AttackBuckets pBuckets = buildBuckets(playerArmy, actedThisRound);
                AttackBuckets cBuckets = buildBuckets(computerArmy, actedThisRound);

                // If nobody left to act on both sides -> round ends.
                if (pBuckets.isEmpty() && cBuckets.isEmpty()) {
                    break;
                }

                boolean needRebuild = false;

                while (!pBuckets.isEmpty() || !cBuckets.isEmpty()) {
                    // Player turn
                    Unit p = pBuckets.pollNextAliveNotActed(actedThisRound);
                    if (p != null) {
                        Unit target = p.getProgram().attack();
                        log(p, target);

                        actedThisRound.add(p);

                        // If the target died and it has not acted yet in this round -> rebuild queues.
                        if (target != null && !target.isAlive() && !actedThisRound.contains(target)) {
                            needRebuild = true;
                        }
                    }

                    if (needRebuild) break;

                    // Computer turn
                    Unit c = cBuckets.pollNextAliveNotActed(actedThisRound);
                    if (c != null) {
                        Unit target = c.getProgram().attack();
                        log(c, target);

                        actedThisRound.add(c);

                        if (target != null && !target.isAlive() && !actedThisRound.contains(target)) {
                            needRebuild = true;
                        }
                    }

                    if (needRebuild) break;
                }

                if (!needRebuild) {
                    break; // round finished without needing recalculation
                }
                // else: rebuild queues and continue the same round
            }

            // Round log (like default)
            System.out.println();
            System.out.println("Round " + round + " is over!");
            System.out.println("Player army has " + countAlive(playerArmy) + " units");
            System.out.println("Computer army has " + countAlive(computerArmy) + " units");
            System.out.println();

            // Battle ends only when one army has no alive units.
            if (!hasAlive(playerArmy) || !hasAlive(computerArmy)) {
                System.out.println("Battle is over!");
                return;
            }
        }

        System.out.println("Battle is over!");
    }

    private static final class AttackBuckets {
        // baseAttack -> units with that attack value (descending order)
        private final TreeMap<Integer, ArrayDeque<Unit>> buckets =
                new TreeMap<>(Comparator.reverseOrder());

        void add(Unit u) {
            buckets.computeIfAbsent(u.getBaseAttack(), k -> new ArrayDeque<>()).addLast(u);
        }

        boolean isEmpty() {
            return buckets.isEmpty();
        }

        Unit pollNextAliveNotActed(HashSet<Unit> actedThisRound) {
            while (!buckets.isEmpty()) {
                Map.Entry<Integer, ArrayDeque<Unit>> e = buckets.firstEntry();
                ArrayDeque<Unit> q = e.getValue();

                while (!q.isEmpty()) {
                    Unit u = q.pollFirst();
                    if (u != null && u.isAlive() && !actedThisRound.contains(u)) {
                        return u;
                    }
                }

                buckets.pollFirstEntry();
            }
            return null;
        }
    }

    private AttackBuckets buildBuckets(Army army, HashSet<Unit> actedThisRound) {
        AttackBuckets b = new AttackBuckets();
        List<Unit> units = (army == null ? null : army.getUnits());
        if (units == null) return b;

        for (Unit u : units) {
            if (u == null) continue;
            if (!u.isAlive()) continue;
            if (actedThisRound.contains(u)) continue;
            b.add(u);
        }
        return b;
    }

    private boolean hasAlive(Army army) {
        if (army == null || army.getUnits() == null) return false;
        for (Unit u : army.getUnits()) {
            if (u != null && u.isAlive()) return true;
        }
        return false;
    }

    private int countAlive(Army army) {
        if (army == null || army.getUnits() == null) return 0;
        int cnt = 0;
        for (Unit u : army.getUnits()) {
            if (u != null && u.isAlive()) cnt++;
        }
        return cnt;
    }

    private void log(Unit attacker, Unit target) {
        // Required by the task. Keep safe against missing injection.
        if (printBattleLog != null) {
            printBattleLog.printBattleLog(attacker, target);
        } else {
            System.out.println("ATTACK: " + (attacker == null ? "null" : attacker.getName())
                    + " -> " + (target == null ? "null" : target.getName()));
        }
    }
}
