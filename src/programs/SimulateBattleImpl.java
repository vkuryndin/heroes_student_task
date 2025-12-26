package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.PrintBattleLog;
import com.battle.heroes.army.programs.SimulateBattle;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Simulates a turn-based battle between the player's army and the computer's army.
 *
 * <h2>Purpose</h2>
 * This class implements the {@link SimulateBattle} contract required by the game engine.
 * It runs the combat loop until the battle ends, printing battle logs after each attack and
 * round summaries after each round.
 *
 * <h2>Battle rules implemented</h2>
 * The simulation follows the specification from the task:
 * <ol>
 *   <li>The battle is processed in <b>rounds</b>.</li>
 *   <li>At the start of each round, units are ordered by <b>baseAttack descending</b> so the strongest act first.</li>
 *   <li>Turns <b>alternate</b> between player and computer queues; if one queue becomes empty, the other continues.</li>
 *   <li>After each attack attempt, the battle log is printed via {@link PrintBattleLog#printBattleLog(Unit, Unit)}.</li>
 *   <li>If a unit dies <b>before it has acted in the current round</b>, it must be removed immediately
 *       and the turn queue of the affected side must be recalculated.</li>
 *   <li>The battle ends when one army has no alive units remaining, or when one side has no alive units
 *       that are able to perform a meaningful move (see the "able to make a move" note below).</li>
 * </ol>
 *
 * <h2>"Able to make a move" interpretation</h2>
 * The task formulation mentions: "The simulation ends when one of the armies has no alive units capable of making a move."
 * The game's {@code Program.attack()} method returns:
 * <ul>
 *   <li>a target unit if an attack was performed, or</li>
 *   <li>{@code null} if a target was not found.</li>
 * </ul>
 * Some melee programs in the provided game code may return {@code this.unit} as a special value when the unit cannot
 * reach a target (path not found). The original specification does not mention returning self, so this implementation
 * treats {@code target == attacker} as {@code null} (i.e., "no target").
 *
 * <p>For the purpose of deciding whether an army is "capable of making a move", this implementation tracks if during
 * the whole round at least one unit from that side returned a non-null target (a meaningful action happened).</p>
 *
 * <h2>Logging</h2>
 * <ul>
 *   <li>After every attack attempt: {@code printBattleLog.printBattleLog(attacker, target)}.</li>
 *   <li>After every round: prints a blank line, round summary, alive unit counts, and another blank line.</li>
 *   <li>When battle ends: prints {@code "Battle is over!"}.</li>
 * </ul>
 *
 * <h2>Complexity</h2>
 * Let {@code N} be the total number of units across both armies.
 * <ul>
 *   <li>Building a priority queue for one army is {@code O(k log k)} where {@code k} is the number of eligible
 *       alive units not yet acted in the current round.</li>
 *   <li>Polling from the queue is {@code O(log k)} per unit action.</li>
 *   <li>In the worst case, a round may rebuild a queue multiple times (only when a not-yet-acted unit dies).</li>
 * </ul>
 * The dominant cost depends on the number of rebuilds and the cost of {@code Program.attack()} (defined by the game),
 * but the algorithm remains efficient for the expected problem sizes (armies are relatively small).
 *
 * <h2>Robustness</h2>
 * The game usually injects {@link PrintBattleLog}. To avoid {@link NullPointerException} and keep the program stable,
 * this implementation installs a safe fallback logger that prints to stdout if the logger was not injected.
 */
public class SimulateBattleImpl implements SimulateBattle {

    /**
     * Battle logger.
     *
     * <p>In the actual game, this field is usually injected by the engine.
     * For safety, if it is {@code null}, a fallback implementation is used.</p>
     */
    private PrintBattleLog printBattleLog;

    /**
     * Runs the battle simulation between two armies.
     *
     * <p>The method does not modify the armies' unit lists by removing elements; instead it relies on each unit's
     * {@link Unit#isAlive()} flag and rebuilds turn queues when required by the rules.</p>
     *
     * @param playerArmy   player's army instance (right side in UI)
     * @param computerArmy computer's army instance (left side in UI)
     * @throws InterruptedException propagated from {@code Program.attack()}, which may sleep for animation speed
     */
    @Override
    public void simulate(Army playerArmy, Army computerArmy) throws InterruptedException {
        if (playerArmy == null || computerArmy == null) return;

        // Safe default logger to avoid null-pointer failures if injection did not happen.
        if (printBattleLog == null) {
            printBattleLog = (att, trg) -> System.out.println(
                    "ATTACK: " + (att == null ? "null" : att.getName()) +
                            " -> " + (trg == null ? "null" : trg.getName())
            );
        }

        // If one side already has no alive units, the battle is considered finished.
        if (!hasAlive(playerArmy) || !hasAlive(computerArmy)) {
            System.out.println("Battle is over!");
            return;
        }

        int round = 0;

        while (hasAlive(playerArmy) && hasAlive(computerArmy)) {
            round++;

            // Tracks units that have already acted in the current round (both armies).
            // Use identity semantics to avoid any surprises if Unit ever overrides equals/hashCode.
            Set<Unit> actedThisRound = Collections.newSetFromMap(new IdentityHashMap<>());

            // Tracks whether each side managed to perform at least one meaningful action during this round.
            boolean playerHasMovesThisRound = false;
            boolean computerHasMovesThisRound = false;

            // Identity membership set to determine which side a killed unit belongs to.
            Set<Unit> playerSide = Collections.newSetFromMap(new IdentityHashMap<>());
            List<Unit> pUnits = playerArmy.getUnits();
            if (pUnits != null) {
                for (Unit u : pUnits) {
                    if (u != null) playerSide.add(u);
                }
            }

            // Build queues once per round; rebuild only the affected side when required.
            PriorityQueue<Unit> pQ = buildQueue(playerArmy, actedThisRound);
            PriorityQueue<Unit> cQ = buildQueue(computerArmy, actedThisRound);

            // Round processing: rebuild a queue only when a unit dies before it acted this round.
            while (!pQ.isEmpty() || !cQ.isEmpty()) {

                // Player turn.
                Unit pAttacker = pollNextValid(pQ, actedThisRound);
                if (pAttacker != null) {
                    Unit target = pAttacker.getProgram().attack();

                    // Some melee programs return self when they cannot reach a target.
                    // Per task contract, treat that as "no target".
                    if (target == pAttacker) target = null;

                    printBattleLog.printBattleLog(pAttacker, target);
                    actedThisRound.add(pAttacker);

                    if (target != null) {
                        playerHasMovesThisRound = true;

                        // If the target died and has not acted yet this round -> rebuild immediately (affected side only).
                        if (!target.isAlive() && !actedThisRound.contains(target)) {
                            if (playerSide.contains(target)) {
                                pQ = buildQueue(playerArmy, actedThisRound);
                            } else {
                                cQ = buildQueue(computerArmy, actedThisRound);
                            }
                        }
                    }
                }

                // Computer turn.
                Unit cAttacker = pollNextValid(cQ, actedThisRound);
                if (cAttacker != null) {
                    Unit target = cAttacker.getProgram().attack();
                    if (target == cAttacker) target = null;

                    printBattleLog.printBattleLog(cAttacker, target);
                    actedThisRound.add(cAttacker);

                    if (target != null) {
                        computerHasMovesThisRound = true;

                        if (!target.isAlive() && !actedThisRound.contains(target)) {
                            if (playerSide.contains(target)) {
                                pQ = buildQueue(playerArmy, actedThisRound);
                            } else {
                                cQ = buildQueue(computerArmy, actedThisRound);
                            }
                        }
                    }
                }
            }

            // Round summary logs (aligned with the default implementation semantics).
            System.out.println();
            System.out.println("Round " + round + " is over!");
            System.out.println("Player army has " + countAlive(playerArmy) + " units");
            System.out.println("Computer army has " + countAlive(computerArmy) + " units");
            System.out.println();

            // End condition: one side has no alive units.
            if (!hasAlive(playerArmy) || !hasAlive(computerArmy)) {
                System.out.println("Battle is over!");
                return;
            }

            // End condition: one side has no alive units capable of making a meaningful move.
            // We interpret "capable" as: at least one unit returned a non-null target during the round.
            if (!playerHasMovesThisRound || !computerHasMovesThisRound) {
                System.out.println("Battle is over!");
                return;
            }
        }

        System.out.println("Battle is over!");
    }

    /**
     * Builds a priority queue (max-heap by baseAttack) for units that:
     * <ul>
     *   <li>belong to the given army,</li>
     *   <li>are alive,</li>
     *   <li>have not acted in the current round.</li>
     * </ul>
     *
     * @param army          army to build the queue for
     * @param actedThisRound set of units that already acted in the current round
     * @return priority queue ordered by {@link Unit#getBaseAttack()} descending
     */
    private PriorityQueue<Unit> buildQueue(Army army, Set<Unit> actedThisRound) {
        PriorityQueue<Unit> q = new PriorityQueue<>(Comparator.comparingInt(Unit::getBaseAttack).reversed());
        if (army == null) return q;

        List<Unit> units = army.getUnits();
        if (units == null) return q;

        for (Unit u : units) {
            if (u == null) continue;
            if (!u.isAlive()) continue;
            if (actedThisRound.contains(u)) continue;
            q.add(u);
        }
        return q;
    }

    /**
     * Polls the next valid unit from the given queue that is still alive and has not acted this round.
     *
     * <p>This method is defensive: between queue construction and polling, a unit might have died due to
     * an opponent's action, so we re-check {@link Unit#isAlive()}.</p>
     *
     * @param q             priority queue of candidates
     * @param actedThisRound set of units that already acted in the current round
     * @return next valid unit or {@code null} if none exists
     */
    private Unit pollNextValid(PriorityQueue<Unit> q, Set<Unit> actedThisRound) {
        while (!q.isEmpty()) {
            Unit u = q.poll();
            if (u != null && u.isAlive() && !actedThisRound.contains(u)) return u;
        }
        return null;
    }

    /**
     * Checks whether the army has at least one alive unit.
     *
     * @param army army to check
     * @return {@code true} if at least one unit is alive; otherwise {@code false}
     */
    private boolean hasAlive(Army army) {
        if (army == null || army.getUnits() == null) return false;
        for (Unit u : army.getUnits()) {
            if (u != null && u.isAlive()) return true;
        }
        return false;
    }

    /**
     * Counts the number of alive units in an army.
     *
     * @param army army to count
     * @return number of alive units; 0 if army or list is null
     */
    private int countAlive(Army army) {
        if (army == null || army.getUnits() == null) return 0;
        int cnt = 0;
        for (Unit u : army.getUnits()) {
            if (u != null && u.isAlive()) cnt++;
        }
        return cnt;
    }
}
