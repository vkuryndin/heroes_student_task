package programs;

import com.battle.heroes.army.Unit;
import com.battle.heroes.army.programs.Edge;
import com.battle.heroes.army.programs.UnitTargetPathFinder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes the shortest path on the battlefield grid from an attacker to a target.
 *
 * <h2>Purpose</h2>
 * This class implements {@link UnitTargetPathFinder} required by the game engine.
 * Given:
 * <ul>
 *   <li>an attacking unit (start cell),</li>
 *   <li>a target unit (goal cell),</li>
 *   <li>and a list of existing units on the field (obstacles),</li>
 * </ul>
 * it returns a path as a list of {@link Edge} cells from the attacker to the target.
 *
 * <h2>Grid model</h2>
 * The battlefield is treated as a fixed-size grid:
 * <ul>
 *   <li>Width: {@value #WIDTH} (x in {@code [0..26]})</li>
 *   <li>Height: {@value #HEIGHT} (y in {@code [0..20]})</li>
 * </ul>
 *
 * <h2>Movement rules</h2>
 * <ul>
 *   <li>Moves are allowed in 8 directions (4 orthogonal + 4 diagonal).</li>
 *   <li>Each move has a uniform cost (1 step), therefore, BFS yields the shortest path by number of steps.</li>
 *   <li>Alive units (except the attacker and the target) are treated as blocked cells (obstacles).</li>
 *   <li>The target cell is always allowed even if it is "occupied" by the target unit.</li>
 *   <li>No "corner-cutting" checks are applied for diagonals (i.e., diagonal move is allowed even if
 *       orthogonal neighbors are blocked). This is aligned with the expected game behavior in many tasks.</li>
 * </ul>
 *
 * <h2>Algorithm</h2>
 * This implementation uses <b>Bidirectional BFS</b>:
 * <ul>
 *   <li>One BFS expands from the attacker start cell.</li>
 *   <li>The other BFS expands from the target cell.</li>
 *   <li>When the explored regions meet, the shortest path can be reconstructed by combining predecessors
 *       from both searches.</li>
 * </ul>
 *
 * <h2>Why bidirectional BFS?</h2>
 * On a grid with uniform edge weights, BFS is optimal for shortest paths.
 * Bidirectional BFS often explores fewer cells than single-source BFS in practice, because each frontier
 * grows roughly like a circle, and meeting in the middle reduces total expansions.
 *
 * <h2>Determinism</h2>
 * The direction order is fixed in {@link #DIRECTIONS}. This makes the resulting path deterministic when multiple
 * shortest paths exist, which is helpful for repeatable tests.
 *
 * <h2>Complexity</h2>
 * Let {@code V = WIDTH * HEIGHT} be the number of grid cells.
 * <ul>
 *   <li>Worst-case time: {@code O(V)} (each cell visited at most once per side, constants are small).</li>
 *   <li>Worst-case memory: {@code O(V)} for visited flags and predecessor tables.</li>
 * </ul>
 *
 * <h2>Edge cases and logging</h2>
 * <ul>
 *   <li>If {@code attackUnit} or {@code targetUnit} is {@code null}, returns an empty list.</li>
 *   <li>If either unit is out of bounds, returns an empty list.</li>
 *   <li>If start equals target, returns a single-cell path containing the start/target cell.</li>
 *   <li>If no path exists, prints a message and returns an empty list.</li>
 * </ul>
 */
public class UnitTargetPathFinderImpl implements UnitTargetPathFinder {

    /** Battlefield width (x: 0..26). */
    private static final int WIDTH = 27;

    /** Battlefield height (y: 0..20). */
    private static final int HEIGHT = 21;

    /**
     * Fixed direction order -> deterministic paths (important for tests).
     *
     * <p>Order matters when multiple shortest paths exist. Using a stable order ensures that repeated runs
     * produce the same path for the same input state.</p>
     */
    private static final int[][] DIRECTIONS = new int[][]{
            {-1, 0}, {1, 0}, {0, -1}, {0, 1},
            {-1, -1}, {1, 1}, {-1, 1}, {1, -1}
    };

    /**
     * Finds the shortest path from {@code attackUnit} to {@code targetUnit} on the grid.
     *
     * <p>Alive units from {@code existingUnitList} are treated as obstacles, except for the attacker and the target.
     * The resulting path includes the start cell and the target cell.</p>
     *
     * @param attackUnit        attacker unit (path start)
     * @param targetUnit        target unit (path goal)
     * @param existingUnitList  all units currently on the field (used to mark obstacles)
     * @return list of {@link Edge} cells representing a shortest path; empty list if unreachable
     */
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

        // Trivial case: already on the target cell.
        if (sx == tx && sy == ty) {
            ArrayList<Edge> single = new ArrayList<>();
            single.add(new Edge(sx, sy));
            return single;
        }

        // Build obstacle grid: mark cells occupied by alive units (excluding attacker/target).
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

        // Visited flags for both BFS fronts.
        boolean[][] visS = new boolean[WIDTH][HEIGHT];
        boolean[][] visT = new boolean[WIDTH][HEIGHT];

        // Predecessors for path reconstruction:
        // - prevS[nx][ny] is the previous cell on the path from Start to (nx,ny)
        // - prevT[nx][ny] is the previous cell on the path from Target expansion back toward Target
        Edge[][] prevS = new Edge[WIDTH][HEIGHT]; // predecessor from start side
        Edge[][] prevT = new Edge[WIDTH][HEIGHT]; // predecessor from target side (points toward target)

        // Queues for both BFS fronts.
        ArrayDeque<Edge> qS = new ArrayDeque<>();
        ArrayDeque<Edge> qT = new ArrayDeque<>();

        // Initialize both searches.
        visS[sx][sy] = true;
        visT[tx][ty] = true;
        qS.addLast(new Edge(sx, sy));
        qT.addLast(new Edge(tx, ty));

        Edge meet = null;

        // Expand alternately (layer-by-layer) until one frontier meets the other or queues are exhausted.
        while (!qS.isEmpty() && !qT.isEmpty() && meet == null) {
            meet = expandLayer(qS, visS, prevS, visT, blocked, tx, ty);
            if (meet != null) break;

            meet = expandLayer(qT, visT, prevT, visS, blocked, tx, ty);
            if (meet != null) break;
        }

        // If no meeting point was found, the target is unreachable.
        if (meet == null) {
            System.out.println("Unit " + attackUnit.getName()
                    + " cannot find path to attack unit " + targetUnit.getName());
            return new ArrayList<>();
        }

        // Reconstruct shortest path by stitching together predecessor chains from both sides.
        return buildPath(sx, sy, tx, ty, meet.getX(), meet.getY(), prevS, prevT, attackUnit, targetUnit);
    }

    /**
     * Expands exactly one BFS "layer" (all nodes currently in the queue) for one side of the bidirectional search.
     *
     * <p>This method:
     * <ul>
     *   <li>Processes the current queue size as one layer (ensures correct BFS distance progression).</li>
     *   <li>For each cell, explores up to 8 neighbor directions.</li>
     *   <li>Skips out-of-bounds cells and blocked cells (except the target goal cell).</li>
     *   <li>Marks visited cells and stores predecessors for path reconstruction.</li>
     *   <li>Returns the meeting cell if it is already visited by the other side.</li>
     * </ul>
     *
     * @param q        BFS queue for this side
     * @param visThis  visited grid for this side
     * @param prevThis predecessor grid for this side
     * @param visOther visited grid for the opposite side
     * @param blocked  obstacle grid
     * @param tx       target x (goal cell allowed even if blocked)
     * @param ty       target y (goal cell allowed even if blocked)
     * @return meeting {@link Edge} if frontiers meet; otherwise {@code null}
     */
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

                // Blocked cells are forbidden except the target cell itself.
                if (blocked[nx][ny] && !(nx == tx && ny == ty)) continue;

                if (visThis[nx][ny]) continue;

                visThis[nx][ny] = true;
                prevThis[nx][ny] = new Edge(cx, cy);
                Edge next = new Edge(nx, ny);

                // Meeting point: the other side has already visited this cell.
                if (visOther[nx][ny]) {
                    return next;
                }

                q.addLast(next);
            }
        }
        return null;
    }

    /**
     * Reconstructs the full path as:
     * <pre>
     * (start -> meet) + (meet -> target)
     * </pre>
     * where {@code meet} is included only once.
     *
     * <p>Reconstruction details:</p>
     * <ul>
     *   <li>Walk backwards from {@code meet} to {@code start} using {@code prevS}, then reverse.</li>
     *   <li>Walk from {@code meet} to {@code target} using {@code prevT} (because that BFS started at target,
     *       {@code prevT} points "toward" the target).</li>
     * </ul>
     *
     * <p>If reconstruction fails (missing predecessor), an empty path is returned and a message is printed.
     * This should be rare and typically indicates inconsistent meeting state.</p>
     *
     * @param sx attacker start x
     * @param sy attacker start y
     * @param tx target x
     * @param ty target y
     * @param mx meeting x
     * @param my meeting y
     * @param prevS predecessors from start-side BFS
     * @param prevT predecessors from target-side BFS
     * @param attackUnit attacker (used for logging)
     * @param targetUnit target (used for logging)
     * @return full path as list of {@link Edge}
     */
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

        // From meet back to start using prevS (then reverse).
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

        // From meet to target using prevT (points toward target because BFS started from target).
        while (!(cx == tx && cy == ty)) {
            Edge p = prevT[cx][cy];
            if (p == null) {
                // It is possible that the meeting node was discovered from the start side into a cell already
                // visited by the target side. Typically prevT for the meeting cell should exist; if not, fail safe.
                System.out.println("Unit " + attackUnit.getName()
                        + " cannot find path to attack unit " + targetUnit.getName());
                return new ArrayList<>();
            }
            cx = p.getX();
            cy = p.getY();
            right.add(new Edge(cx, cy));
        }

        // Merge (avoid duplicating the meeting cell).
        ArrayList<Edge> path = new ArrayList<>(left);
        path.addAll(right);
        return path;
    }

    /**
     * Checks whether coordinates are inside the battlefield grid.
     *
     * @param x x-coordinate
     * @param y y-coordinate
     * @return {@code true} if the cell is inside the grid, otherwise {@code false}
     */
    private boolean inBounds(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }
}
