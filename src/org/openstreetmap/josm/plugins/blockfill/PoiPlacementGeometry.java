package org.openstreetmap.josm.plugins.blockfill;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 在闭合建筑多边形内部生成 POI 散点。
 *
 * <p>输入：建筑外轮廓的本地坐标环（米制），输出：内部随机点列表。
 * 使用 Poisson 盘采样保证点间距不过密，同时能生成足够多的候选点。
 * 不依赖任何 JOSM 类，可单独单元测试。
 */
public final class PoiPlacementGeometry {

    private static final int MAX_ATTEMPTS = 30;
    private static final int MAX_POINTS   = 200;

    private PoiPlacementGeometry() { }

    public record Point(double x, double y) { }

    /**
     * 在多边形内部生成 {@code count} 个分布均匀的随机点。
     *
     * @param ring  建筑外轮廓点列表（开放环，最后一点 ≠ 第一点）
     * @param count 需要的点数
     * @param seed  随机种子
     * @return 实际生成的点，数量 ≤ count（空间不足时会少于 count）
     */
    public static List<Point> scatter(List<Point> ring, int count, long seed) {
        if (ring == null || ring.size() < 3 || count <= 0) return List.of();

        Area area = toArea(ring);
        java.awt.geom.Rectangle2D bounds = area.getBounds2D();
        if (bounds.isEmpty()) return List.of();

        double w = bounds.getWidth();
        double h = bounds.getHeight();
        if (w < 0.5 || h < 0.5) return List.of(); // 建筑太小

        // 最小点间距：按面积和数量估算，保证不过密也不全排在角落
        double estArea = w * h * 0.6; // 粗估有效面积
        double minDist = Math.max(1.0, Math.sqrt(estArea / (count * 4.0)));

        Random rng = new Random(seed);
        List<Point> placed = new ArrayList<>(count);

        // 先用泊松盘尝试，不够再补均匀随机
        placed.addAll(poisson(area, bounds, minDist, count, rng));

        // 如果泊松结果不足（建筑形状复杂），补均匀随机候选点
        if (placed.size() < count) {
            int deficit = count - placed.size();
            for (int attempt = 0; attempt < deficit * 20 && placed.size() < count; attempt++) {
                double x = bounds.getMinX() + rng.nextDouble() * w;
                double y = bounds.getMinY() + rng.nextDouble() * h;
                if (area.contains(x, y)) placed.add(new Point(x, y));
            }
        }

        // 只返回前 count 个
        return List.copyOf(placed.subList(0, Math.min(placed.size(), count)));
    }

    // ── Poisson 盘采样 ──────────────────────────────────────────────────────

    private static List<Point> poisson(Area area, java.awt.geom.Rectangle2D b,
                                        double minDist, int maxCount, Random rng) {
        double cell = minDist / Math.sqrt(2);
        int cols = Math.max(1, (int) Math.ceil(b.getWidth()  / cell));
        int rows = Math.max(1, (int) Math.ceil(b.getHeight() / cell));
        if ((long) cols * rows > 100_000L) return List.of(); // 防止超大建筑耗内存

        Point[] grid = new Point[cols * rows];
        List<Point> active  = new ArrayList<>();
        List<Point> placed  = new ArrayList<>();

        // 种子点：先找一个面内点
        Point seed = findSeedPoint(area, b, rng);
        if (seed == null) return List.of();
        addPoint(seed, grid, b, cell, cols, active, placed);

        while (!active.isEmpty() && placed.size() < maxCount && placed.size() < MAX_POINTS) {
            int idx = rng.nextInt(active.size());
            Point origin = active.get(idx);
            boolean found = false;
            for (int k = 0; k < MAX_ATTEMPTS; k++) {
                double angle = rng.nextDouble() * 2 * Math.PI;
                double r     = minDist * (1 + rng.nextDouble());
                double cx = origin.x() + r * Math.cos(angle);
                double cy = origin.y() + r * Math.sin(angle);
                if (!area.contains(cx, cy)) continue;
                Point candidate = new Point(cx, cy);
                if (!tooClose(candidate, grid, b, cell, cols, rows, minDist)) {
                    addPoint(candidate, grid, b, cell, cols, active, placed);
                    found = true;
                    break;
                }
            }
            if (!found) active.remove(idx);
        }
        return placed;
    }

    private static Point findSeedPoint(Area area, java.awt.geom.Rectangle2D b, Random rng) {
        double cx = b.getCenterX(), cy = b.getCenterY();
        if (area.contains(cx, cy)) return new Point(cx, cy);
        for (int i = 0; i < 200; i++) {
            double x = b.getMinX() + rng.nextDouble() * b.getWidth();
            double y = b.getMinY() + rng.nextDouble() * b.getHeight();
            if (area.contains(x, y)) return new Point(x, y);
        }
        return null;
    }

    private static void addPoint(Point p, Point[] grid, java.awt.geom.Rectangle2D b,
                                   double cell, int cols,
                                   List<Point> active, List<Point> placed) {
        int col = Math.min(cols - 1, (int) ((p.x() - b.getMinX()) / cell));
        int row = (int) ((p.y() - b.getMinY()) / cell);
        grid[row * cols + col] = p;
        active.add(p);
        placed.add(p);
    }

    private static boolean tooClose(Point p, Point[] grid, java.awt.geom.Rectangle2D b,
                                      double cell, int cols, int rows, double minDist) {
        int col = (int) ((p.x() - b.getMinX()) / cell);
        int row = (int) ((p.y() - b.getMinY()) / cell);
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                int r2 = row + dr, c2 = col + dc;
                if (r2 < 0 || r2 >= rows || c2 < 0 || c2 >= cols) continue;
                Point q = grid[r2 * cols + c2];
                if (q == null) continue;
                double dx = p.x() - q.x(), dy = p.y() - q.y();
                if (dx * dx + dy * dy < minDist * minDist) return true;
            }
        }
        return false;
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    private static Area toArea(List<Point> ring) {
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < ring.size(); i++) {
            Point p = ring.get(i);
            if (i == 0) path.moveTo(p.x(), p.y()); else path.lineTo(p.x(), p.y());
        }
        path.closePath();
        return new Area(path);
    }
}
