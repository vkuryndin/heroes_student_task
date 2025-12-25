package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.Edge;
import com.battle.heroes.army.programs.UnitTargetPathFinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class UnitTargetPathFinderImpl implements UnitTargetPathFinder {

    // Field size from the task description
    private static final int WIDTH = 27;   // x: 0..26
    private static final int HEIGHT = 21;  // y: 0..20

    // Use the same neighbor order as in the default implementation to reduce behavior differences
    private static final int[][] DIRECTIONS = new int[][]{
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {1, 1}, {-1, 1}, {1, -1}
    };

    @Override
    public List<Edge> getTargetPath(Unit attackUnit, Unit targetUnit, List<Unit> existingUnitList) {
        // BFS on a grid with 8-direction moves (all moves cost 1),
        // obstacles = alive units excluding attacker and target,
        // target cell is always allowed.

        if (attackUnit == null || targetUnit == null) {
            return List.of();
        }

        int sx = attackUnit.getxCoordinate();
        int sy = attackUnit.getyCoordinate();
        int tx = targetUnit.getxCoordinate();
        int ty = targetUnit.getyCoordinate();

        if (!inBounds(sx, sy) || !inBounds(tx, ty)) {
            return List.of();
        }

        // Trivial case
        if (sx == tx && sy == ty) {
            return List.of(new Edge(sx, sy));
        }

        // Mark blocked cells
        boolean[][] blocked = new boolean[WIDTH][HEIGHT];
        if (existingUnitList != null) {
            for (Unit u : existingUnitList) {
                if (u == null) continue;
                if (!u.isAlive()) continue;
                if (u == attackUnit || u == targetUnit) continue;

                int x = u.getxCoordinate();
                int y = u.getyCoordinate();
                if (inBounds(x, y)) {
                    blocked[x][y] = true;
                }
            }
        }

        // Distance: -1 means unvisited
        int[][] dist = new int[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            Arrays.fill(dist[x], -1);
        }

        // Predecessor pointers for path restore (same idea as default Edge[][] prev)
        Edge[][] prev = new Edge[WIDTH][HEIGHT];

        ArrayDeque<Edge> q = new ArrayDeque<>();
        dist[sx][sy] = 0;
        q.addLast(new Edge(sx, sy));

        while (!q.isEmpty()) {
            Edge cur = q.removeFirst();
            int cx = cur.getX();
            int cy = cur.getY();

            // Early exit when we reach the target
            if (cx == tx && cy == ty) {
                break;
            }

            for (int[] d : DIRECTIONS) {
                int nx = cx + d[0];
                int ny = cy + d[1];

                if (!inBounds(nx, ny)) continue;

                // Blocked cells are not allowed, except the target cell
                if (blocked[nx][ny] && !(nx == tx && ny == ty)) continue;

                if (dist[nx][ny] != -1) continue;

                dist[nx][ny] = dist[cx][cy] + 1;
                prev[nx][ny] = new Edge(cx, cy);
                q.addLast(new Edge(nx, ny));
            }
        }

        if (dist[tx][ty] == -1) {
            System.out.println("Unit " + attackUnit.getName()
                    + " cannot find path to attack unit " + targetUnit.getName());
            return new ArrayList<>();
        }

        // Restore path from target to start
        ArrayList<Edge> path = new ArrayList<>();
        int cx = tx;
        int cy = ty;

        while (!(cx == sx && cy == sy)) {
            path.add(new Edge(cx, cy));
            Edge p = prev[cx][cy];

            // Safety fallback (should not happen if dist[tx][ty] != -1)
            if (p == null) {
                System.out.println("Unit " + attackUnit.getName()
                        + " cannot find path to attack unit " + targetUnit.getName());
                return new ArrayList<>();
            }

            cx = p.getX();
            cy = p.getY();
        }

        path.add(new Edge(sx, sy));
        Collections.reverse(path);
        return path;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }
}
