package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.PrintBattleLog;
import com.battle.heroes.army.programs.SimulateBattle;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;

public class SimulateBattleImpl implements SimulateBattle {
    private PrintBattleLog printBattleLog; // Provided by the game; use after each attack

    @Override
    public void simulate(Army playerArmy, Army computerArmy) throws InterruptedException {
        // Battle rules (aligned with the default implementation intent):
        // - Each round: build two turn queues (player/computer), ordered by baseAttack descending
        // - Alternate turns: player then computer (if queue not empty)
        // - Units that died before their turn in the current round must not act (queues are recalculated)
        // - Stop if one army has no alive units, OR if a full round produced no effective actions
        //   (to avoid infinite loops when nobody can reach/attack anyone).

        if (playerArmy == null || computerArmy == null) return;

        while (hasAlive(playerArmy) && hasAlive(computerArmy)) {
            HashSet<Unit> actedThisRound = new HashSet<>();
            boolean anyEffectiveActionThisRound = false;

            // The default code rebuilds queues when a unit dies before its turn.
            // We emulate this behavior with a "rebuild" flag.
            boolean needRebuild;
            // Simulates combat round until rebuild not needed
            do {
                needRebuild = false;

                PriorityQueue<Unit> playerQ = new PriorityQueue<>(Comparator.comparingInt(Unit::getBaseAttack).reversed());
                PriorityQueue<Unit> computerQ = new PriorityQueue<>(Comparator.comparingInt(Unit::getBaseAttack).reversed());

                // Add alive units that have not acted in this round yet.
                for (Unit u : playerArmy.getUnits()) {
                    if (u != null && u.isAlive() && !actedThisRound.contains(u)) {
                        playerQ.add(u);
                    }
                }
                for (Unit u : computerArmy.getUnits()) {
                    if (u != null && u.isAlive() && !actedThisRound.contains(u)) {
                        computerQ.add(u);
                    }
                }

                while (!playerQ.isEmpty() || !computerQ.isEmpty()) {
                    // Player turn
                    if (!playerQ.isEmpty()) {
                        Unit attacker = playerQ.poll();
                        if (attacker != null && attacker.isAlive() && !actedThisRound.contains(attacker)) {
                            Unit target = unitAttack(attacker);

                            // Melee programs may return attacker itself when path is not found.
                            if (target != null && target != attacker) {
                                anyEffectiveActionThisRound = true;
                            }

                            // If target died and hasn't acted yet this round, rebuild queues.
                            if (target != null && !target.isAlive() && !actedThisRound.contains(target)) {
                                needRebuild = true;
                            }

                            actedThisRound.add(attacker);
                            if (needRebuild) break;
                        }
                    }

                    // Computer turn
                    if (!computerQ.isEmpty()) {
                        Unit attacker = computerQ.poll();
                        if (attacker != null && attacker.isAlive() && !actedThisRound.contains(attacker)) {
                            Unit target = unitAttack(attacker);

                            if (target != null && target != attacker) {
                                anyEffectiveActionThisRound = true;
                            }

                            if (target != null && !target.isAlive() && !actedThisRound.contains(target)) {
                                needRebuild = true;
                            }

                            actedThisRound.add(attacker);
                            if (needRebuild) break;
                        }
                    }

                    // If one side has no alive units, we can stop immediately.
                    if (!hasAlive(playerArmy) || !hasAlive(computerArmy)) {
                        return;
                    }
                }
            } while (needRebuild);

            // If nobody could perform a real attack during the entire round, stop to avoid infinite battle.
            if (!anyEffectiveActionThisRound) {
                return;
            }
        }
    }

    private Unit unitAttack(Unit attacker) throws InterruptedException {
        Unit target = attacker.getProgram().attack();

        if (printBattleLog != null) {
            printBattleLog.printBattleLog(attacker, target);
        } else {
            // Fallback: should not happen in the actual game, but keeps runtime safe in tests/debug
            System.out.println(
                    "ATTACK: " + (attacker == null ? "null" : attacker.getName()) +
                            " -> " + (target == null ? "null" : target.getName())
            );
        }

        return target;
    }

    /**
     * Checks if army has at least one alive unit
     */
    private boolean hasAlive(Army army) {
        if (army == null || army.getUnits() == null) return false;
        for (Unit u : army.getUnits()) {
            if (u != null && u.isAlive()) return true;
        }
        return false;
    }
}
