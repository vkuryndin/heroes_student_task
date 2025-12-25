package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.Edge;
import com.battle.heroes.army.programs.UnitTargetPathFinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UnitTargetPathFinderImpl implements UnitTargetPathFinder {

    private static final int WIDTH = 27;
    private static final int HEIGHT = 21;

    // Fixed order -> deterministic paths (important for tests)
    private static final int[][] DIRECTIONS = new int[][]{
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {1, 1}, {-1, 1}, {1, -1}
    };

    @Override
    public List<Edge> getTargetPath(Unit attackUnit, Unit targetUnit, List<Unit> existingUnitList) {
        // Non-default approach: Bidirectional BFS (still shortest path for unit-cost moves).
        // Complexity: O(W*H).
        // Obstacles: alive units excluding attacker and target.
        // Target cell is always allowed, diagonals allowed (no corner-cut checks to match game behavior).

        if (attackUnit == null || targetUnit == null) return List.of();

        int sx = attackUnit.getxCoordinate();
        int sy = attackUnit.getyCoordinate();
        int tx = targetUnit.getxCoordinate();
        int ty = targetUnit.getyCoordinate();

        if (!inBounds(sx, sy) || !inBounds(tx, ty)) return List.of();

        if (sx == tx && sy == ty) {
            ArrayList<Edge> single = new ArrayList<>();
            single.add(new Edge(sx, sy));
            return single;
        }

        boolean[][] blocked = new boolean[WIDTH][HEIGHT];
        if (existingUnitList != null) {
            for (Unit u : existingUnitList) {
                if (u == null) continue;
                if (!u.isAlive()) continue;
                if (u == attackUnit || u == targetUnit) continue;
                int x = u.getxCoordinate();
                int y = u.getyCoordinate();
                if (inBounds(x, y)) blocked[x][y] = true;
            }
        }

        boolean[][] visS = new boolean[WIDTH][HEIGHT];
        boolean[][] visT = new boolean[WIDTH][HEIGHT];

        Edge[][] prevS = new Edge[WIDTH][HEIGHT]; // predecessor from start side
        Edge[][] prevT = new Edge[WIDTH][HEIGHT]; // predecessor from target side (points toward target)

        ArrayDeque<Edge> qS = new ArrayDeque<>();
        ArrayDeque<Edge> qT = new ArrayDeque<>();

        visS[sx][sy] = true;
        visT[tx][ty] = true;
        qS.addLast(new Edge(sx, sy));
        qT.addLast(new Edge(tx, ty));

        Edge meet = null;

        while (!qS.isEmpty() && !qT.isEmpty() && meet == null) {
            meet = expandLayer(qS, visS, prevS, visT, blocked, tx, ty);
            if (meet != null) break;

            meet = expandLayer(qT, visT, prevT, visS, blocked, tx, ty);
            if (meet != null) break;
        }

        if (meet == null) {
            System.out.println("Unit " + attackUnit.getName()
                    + " cannot find path to attack unit " + targetUnit.getName());
            return new ArrayList<>();
        }

        return buildPath(sx, sy, tx, ty, meet.getX(), meet.getY(), prevS, prevT, attackUnit, targetUnit);
    }

    private Edge expandLayer(
            ArrayDeque<Edge> q,
            boolean[][] visThis,
            Edge[][] prevThis,
            boolean[][] visOther,
            boolean[][] blocked,
            int tx,
            int ty
    ) {
        int layerSize = q.size();
        for (int i = 0; i < layerSize; i++) {
            Edge cur = q.removeFirst();
            int cx = cur.getX();
            int cy = cur.getY();

            for (int[] d : DIRECTIONS) {
                int nx = cx + d[0];
                int ny = cy + d[1];

                if (!inBounds(nx, ny)) continue;

                // Blocked cells are forbidden except the target cell itself
                if (blocked[nx][ny] && !(nx == tx && ny == ty)) continue;

                if (visThis[nx][ny]) continue;

                visThis[nx][ny] = true;
                prevThis[nx][ny] = new Edge(cx, cy);
                Edge next = new Edge(nx, ny);

                // Meeting point
                if (visOther[nx][ny]) {
                    return next;
                }

                q.addLast(next);
            }
        }
        return null;
    }

    private List<Edge> buildPath(
            int sx, int sy,
            int tx, int ty,
            int mx, int my,
            Edge[][] prevS,
            Edge[][] prevT,
            Unit attackUnit,
            Unit targetUnit
    ) {
        // Path = (start -> meet) + (meet -> target), meet included once.

        ArrayList<Edge> left = new ArrayList<>();
        int cx = mx;
        int cy = my;

        // from meet back to start using prevS
        while (!(cx == sx && cy == sy)) {
            left.add(new Edge(cx, cy));
            Edge p = prevS[cx][cy];
            if (p == null) {
                System.out.println("Unit " + attackUnit.getName()
                        + " cannot find path to attack unit " + targetUnit.getName());
                return new ArrayList<>();
            }
            cx = p.getX();
            cy = p.getY();
        }
        left.add(new Edge(sx, sy));
        Collections.reverse(left);

        ArrayList<Edge> right = new ArrayList<>();
        cx = mx;
        cy = my;

        // from meet to target using prevT (which points toward the target because BFS started from target)
        while (!(cx == tx && cy == ty)) {
            Edge p = prevT[cx][cy];
            if (p == null) {
                // It's possible meet was discovered from start side into a cell already visited by target side,
                // so prevT for meet should exist. If not, try returning at least left (fallback).
                System.out.println("Unit " + attackUnit.getName()
                        + " cannot find path to attack unit " + targetUnit.getName());
                return new ArrayList<>();
            }
            cx = p.getX();
            cy = p.getY();
            right.add(new Edge(cx, cy));
        }

        // merge (avoid duplicating meet)
        ArrayList<Edge> path = new ArrayList<>(left);
        path.addAll(right);
        return path;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }
}
