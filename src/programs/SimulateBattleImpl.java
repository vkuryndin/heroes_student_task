package programs;

import com.battle.heroes.army.Army;
import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.PrintBattleLog;
import com.battle.heroes.army.programs.SimulateBattle;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

public class SimulateBattleImpl implements SimulateBattle {

    // В игре обычно инжектится. Но чтобы "ничего не падало", делаем безопасный дефолт.
    private PrintBattleLog printBattleLog;

    @Override
    public void simulate(Army playerArmy, Army computerArmy) throws InterruptedException {
        if (playerArmy == null || computerArmy == null) return;

        if (printBattleLog == null) {
            printBattleLog = (att, trg) -> System.out.println(
                    "ATTACK: " + (att == null ? "null" : att.getName()) +
                            " -> " + (trg == null ? "null" : trg.getName())
            );
        }

        if (!hasAlive(playerArmy) || !hasAlive(computerArmy)) {
            System.out.println("Battle is over!");
            return;
        }

        int round = 0;

        while (hasAlive(playerArmy) && hasAlive(computerArmy)) {
            round++;

            // кто уже походил в текущем раунде
            HashSet<Unit> actedThisRound = new HashSet<>();

            // чтобы строго выполнить ТЗ "способных сделать ход":
            // считаем, что армия "способна", если хоть один её юнит в раунде нашёл цель (attack вернул не-null).
            boolean playerHasMovesThisRound = false;
            boolean computerHasMovesThisRound = false;

            // РАУНД: пересчёт очередей делаем только когда умер юнит, который ещё не ходил в этом раунде
            while (true) {
                PriorityQueue<Unit> pQ = buildQueue(playerArmy, actedThisRound);
                PriorityQueue<Unit> cQ = buildQueue(computerArmy, actedThisRound);

                if (pQ.isEmpty() && cQ.isEmpty()) {
                    break; // раунд завершён
                }

                boolean needRebuild = false;

                while (!pQ.isEmpty() || !cQ.isEmpty()) {
                    // ход игрока
                    if (!pQ.isEmpty()) {
                        Unit attacker = pollNextValid(pQ, actedThisRound);
                        if (attacker != null) {
                            Unit target = attacker.getProgram().attack();

                            // В некоторых программах (у милишников) при "не нашёл путь" возвращают самого себя.
                            // По ТЗ attack() должен вернуть цель или null => трактуем self как "цели нет".
                            if (target == attacker) target = null;

                            printBattleLog.printBattleLog(attacker, target);
                            actedThisRound.add(attacker);

                            if (target != null) {
                                playerHasMovesThisRound = true;

                                // если цель умерла и она ещё не ходила в этом раунде -> пересчитываем очереди немедленно
                                if (!target.isAlive() && !actedThisRound.contains(target)) {
                                    needRebuild = true;
                                    break;
                                }
                            }
                        }
                    }

                    // ход компьютера
                    if (!cQ.isEmpty()) {
                        Unit attacker = pollNextValid(cQ, actedThisRound);
                        if (attacker != null) {
                            Unit target = attacker.getProgram().attack();
                            if (target == attacker) target = null;

                            printBattleLog.printBattleLog(attacker, target);
                            actedThisRound.add(attacker);

                            if (target != null) {
                                computerHasMovesThisRound = true;

                                if (!target.isAlive() && !actedThisRound.contains(target)) {
                                    needRebuild = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (!needRebuild) {
                    break; // раунд доигран без необходимости пересчёта очередей
                }
                // иначе продолжаем тот же раунд с пересобранными очередями
            }

            // Логи раунда (как в дефолтной версии по смыслу)
            System.out.println();
            System.out.println("Round " + round + " is over!");
            System.out.println("Player army has " + countAlive(playerArmy) + " units");
            System.out.println("Computer army has " + countAlive(computerArmy) + " units");
            System.out.println();

            // завершение по смерти
            if (!hasAlive(playerArmy) || !hasAlive(computerArmy)) {
                System.out.println("Battle is over!");
                return;
            }

            // завершение по ТЗ: если одна из армий не имеет живых юнитов, способных сделать ход
            // (то есть за весь раунд ни один юнит этой стороны не нашёл цель)
            if (!playerHasMovesThisRound || !computerHasMovesThisRound) {
                System.out.println("Battle is over!");
                return;
            }
        }

        System.out.println("Battle is over!");
    }

    private PriorityQueue<Unit> buildQueue(Army army, HashSet<Unit> actedThisRound) {
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

    private Unit pollNextValid(PriorityQueue<Unit> q, HashSet<Unit> actedThisRound) {
        while (!q.isEmpty()) {
            Unit u = q.poll();
            if (u != null && u.isAlive() && !actedThisRound.contains(u)) return u;
        }
        return null;
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
}
