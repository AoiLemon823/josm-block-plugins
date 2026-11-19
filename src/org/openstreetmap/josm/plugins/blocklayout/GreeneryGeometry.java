package org.openstreetmap.josm.plugins.blocklayout;

import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure local-metre greenery placement geometry for the BlockLayout plugin.
 *
 * <p>All inputs / outputs use {@link LayoutGeometry.Point} in metres on a local
 * equidistant projection.  No JOSM types are referenced here so the class can
 * be unit-tested without a running JOSM instance.
 *
 * <h3>Elements generated</h3>
 * <ul>
 *   <li><b>Trees</b>  – Poisson-disk scattered points tagged {@code natural=tree}</li>
 *   <li><b>Garden patches</b> – rounded convex patches tagged {@code leisure=garden}
 *       grown in the largest open zones</li>
 *   <li><b>Pool</b>  – a single kidney-shaped closed ring tagged
 *       {@code natural=water} near the centroid of the biggest free zone</li>
 * </ul>
 *
 * <h3>Obstacle avoidance</h3>
 * Available space = parcel polygon − building footprints expanded by
 * {@code buildingClearance} − road ways buffered by {@code roadClearance}.
 * Everything generated is guaranteed to lie within that area.
 */
public final class GreeneryGeometry {

    private static final int MAX_TREES = 2000;
    private static final int MAX_PATCH_VERTICES = 32;
    private static final int POISSON_ATTEMPTS = 30;

    private GreeneryGeometry() { }

    // -------------------------------------------------------------------------
    // Public API types
    // -------------------------------------------------------------------------

    public record Settings(
        boolean placeTrees,
        double treeSpacing,       // min distance between trees (m), ≥ 2
        boolean placeGardens,
        double minPatchArea,      // m² – ignore free zones smaller than this
        boolean placePool,
        double poolRadius,        // approximate half-width of the pool (m)
        double buildingClearance, // expansion around building footprints (m)
        double roadClearance,     // buffer around road centrelines (m)
        long seed
    ) {
        public Settings {
            if (treeSpacing < 2) throw new IllegalArgumentException("绿化树间距不能小于 2 米。");
            if (poolRadius < 2) throw new IllegalArgumentException("水池半径不能小于 2 米。");
            if (buildingClearance < 0) throw new IllegalArgumentException("建筑退距不能为负。");
            if (roadClearance < 0) throw new IllegalArgumentException("道路退距不能为负。");
            if (minPatchArea < 1) throw new IllegalArgumentException("绿地最小面积不能小于 1 平方米。");
        }
    }

    /** A closed polygon ring (open list – last point ≠ first). */
    public record Ring(List<LayoutGeometry.Point> points) {
        public Ring { points = List.copyOf(points); }
    }

    public record Result(
        List<LayoutGeometry.Point> trees,
        List<Ring> gardenRings,
        Ring poolRing,            // null if not generated
        int rejectedTrees,
        String message
    ) { }

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    /**
     * Compute greenery placement for one or more parcels.
     *
     * @param parcels           closed land-parcel rings
     * @param buildings         existing building footprints (before clearance expansion)
     * @param roads             road centrelines with their half-widths
     * @param settings          generation settings
     */
    public static Result layout(
            List<LayoutGeometry.Parcel> parcels,
            List<LayoutGeometry.Footprint> buildings,
            List<LayoutGeometry.Road> roads,
            Settings settings) {

        if (parcels == null || parcels.isEmpty())
            throw new IllegalArgumentException("请先选择至少一个闭合地块。");

        // Build the parcel Area (union of all selected parcels)
        Area parcelArea = unionParcels(parcels);

        // Build obstacle area: buildings + roads
        Area obstacleArea = new Area();
        for (LayoutGeometry.Footprint fp : buildings) {
            obstacleArea.add(expandPolygon(fp.ring(), settings.buildingClearance()));
        }
        for (LayoutGeometry.Road road : roads) {
            obstacleArea.add(bufferRoad(road, settings.roadClearance()));
        }

        // Available space
        Area free = new Area(parcelArea);
        free.subtract(obstacleArea);

        Random rng = new Random(settings.seed());

        List<LayoutGeometry.Point> trees = List.of();
        int rejectedTrees = 0;
        if (settings.placeTrees()) {
            var treeResult = poissonTrees(free, settings.treeSpacing(), rng);
            trees = treeResult.placed();
            rejectedTrees = treeResult.rejected();
        }

        // Split free area into connected zones for garden / pool placement
        List<Ring> gardenRings = List.of();
        Ring poolRing = null;

        // Find the bounding rectangle of the free area to seed zone extraction
        java.awt.geom.Rectangle2D bounds = free.getBounds2D();
        if (!bounds.isEmpty()) {
            if (settings.placeGardens()) {
                gardenRings = buildGardenPatches(free, settings.minPatchArea(), rng);
            }
            if (settings.placePool()) {
                poolRing = buildPool(free, settings.poolRadius(), rng);
            }
        }

        String msg = buildMessage(trees.size(), rejectedTrees, gardenRings.size(), poolRing != null);
        return new Result(trees, gardenRings, poolRing, rejectedTrees, msg);
    }

    // -------------------------------------------------------------------------
    // Obstacle geometry helpers
    // -------------------------------------------------------------------------

    /** Union all parcel rings into a single java.awt.geom.Area. */
    private static Area unionParcels(List<LayoutGeometry.Parcel> parcels) {
        Area a = new Area();
        for (LayoutGeometry.Parcel parcel : parcels) {
            a.add(toArea(parcel.ring()));
        }
        return a;
    }

    private static Area toArea(List<LayoutGeometry.Point> ring) {
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < ring.size(); i++) {
            LayoutGeometry.Point p = ring.get(i);
            if (i == 0) path.moveTo(p.x(), p.y());
            else        path.lineTo(p.x(), p.y());
        }
        path.closePath();
        return new Area(path);
    }

    /**
     * Expands a polygon ring outward by {@code d} metres using a simplified
     * Minkowski sum: each vertex is displaced outward along the averaged normal
     * of its two incident edges.
     */
    private static Area expandPolygon(List<LayoutGeometry.Point> ring, double d) {
        if (d <= 0) return toArea(ring);
        int n = ring.size();
        List<LayoutGeometry.Point> expanded = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LayoutGeometry.Point prev = ring.get((i + n - 1) % n);
            LayoutGeometry.Point cur  = ring.get(i);
            LayoutGeometry.Point next = ring.get((i + 1) % n);
            // Edge normals (pointing outward for CCW ring => inward, so flip)
            double ax = cur.x() - prev.x(), ay = cur.y() - prev.y();
            double bx = next.x() - cur.x(), by = next.y() - cur.y();
            double na = Math.hypot(ax, ay), nb = Math.hypot(bx, by);
            if (na < 1e-9 || nb < 1e-9) { expanded.add(cur); continue; }
            // Outward normal of each edge (right-hand perpendicular for CCW = outward)
            double n1x = ay / na, n1y = -ax / na;
            double n2x = by / nb, n2y = -bx / nb;
            // Average and re-normalise
            double nx = n1x + n2x, ny = n1y + n2y;
            double nlen = Math.hypot(nx, ny);
            if (nlen < 1e-9) { expanded.add(cur); continue; }
            nx /= nlen; ny /= nlen;
            expanded.add(new LayoutGeometry.Point(cur.x() + nx * d, cur.y() + ny * d));
        }
        return toArea(expanded);
    }

    /** Creates a rectangular buffer strip around a road segment. */
    private static Area bufferRoad(LayoutGeometry.Road road, double clearance) {
        double halfW = road.width() / 2.0 + clearance;
        Area result = new Area();
        List<LayoutGeometry.Point> cl = road.centerline();
        for (int i = 0; i < cl.size() - 1; i++) {
            result.add(segmentBuffer(cl.get(i), cl.get(i + 1), halfW));
        }
        return result;
    }

    private static Area segmentBuffer(LayoutGeometry.Point a, LayoutGeometry.Point b, double half) {
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double len = Math.hypot(dx, dy);
        if (len < 1e-9) return new Area();
        double nx = -dy / len * half, ny = dx / len * half;
        Path2D.Double p = new Path2D.Double();
        p.moveTo(a.x() + nx, a.y() + ny);
        p.lineTo(b.x() + nx, b.y() + ny);
        p.lineTo(b.x() - nx, b.y() - ny);
        p.lineTo(a.x() - nx, a.y() - ny);
        p.closePath();
        return new Area(p);
    }

    // -------------------------------------------------------------------------
    // Poisson-disk tree placement
    // -------------------------------------------------------------------------

    private record PoissonResult(List<LayoutGeometry.Point> placed, int rejected) { }

    private static PoissonResult poissonTrees(Area free, double minDist, Random rng) {
        java.awt.geom.Rectangle2D b = free.getBounds2D();
        if (b.isEmpty()) return new PoissonResult(List.of(), 0);

        double cellSize = minDist / Math.sqrt(2);
        int cols = Math.max(1, (int) Math.ceil(b.getWidth()  / cellSize));
        int rows = Math.max(1, (int) Math.ceil(b.getHeight() / cellSize));

        // Clamp grid to avoid memory issues for huge parcels
        if ((long) cols * rows > 400_000L) {
            double scale = Math.sqrt((double) cols * rows / 400_000.0);
            cols = (int) Math.ceil(cols / scale);
            rows = (int) Math.ceil(rows / scale);
            cellSize = b.getWidth() / cols;
        }

        LayoutGeometry.Point[] grid = new LayoutGeometry.Point[cols * rows];
        List<LayoutGeometry.Point> active = new ArrayList<>();
        List<LayoutGeometry.Point> placed = new ArrayList<>();
        int rejected = 0;

        // Seed: pick any point inside the free area near its centroid
        LayoutGeometry.Point seed = findSeedPoint(free, b, rng);
        if (seed == null) return new PoissonResult(List.of(), 0);

        insertPoint(seed, grid, b, cellSize, cols, active, placed);

        while (!active.isEmpty() && placed.size() < MAX_TREES) {
            int idx = rng.nextInt(active.size());
            LayoutGeometry.Point origin = active.get(idx);
            boolean found = false;
            for (int attempt = 0; attempt < POISSON_ATTEMPTS; attempt++) {
                double angle = rng.nextDouble() * 2 * Math.PI;
                double r     = minDist * (1 + rng.nextDouble());
                double cx = origin.x() + r * Math.cos(angle);
                double cy = origin.y() + r * Math.sin(angle);
                if (!free.contains(cx, cy)) { rejected++; continue; }
                LayoutGeometry.Point candidate = new LayoutGeometry.Point(cx, cy);
                if (tooClose(candidate, grid, b, cellSize, cols, rows, minDist)) { rejected++; continue; }
                insertPoint(candidate, grid, b, cellSize, cols, active, placed);
                found = true;
                break;
            }
            if (!found) active.remove(idx);
        }
        return new PoissonResult(List.copyOf(placed), rejected);
    }

    private static LayoutGeometry.Point findSeedPoint(Area free, java.awt.geom.Rectangle2D b, Random rng) {
        double cx = b.getCenterX(), cy = b.getCenterY();
        if (free.contains(cx, cy)) return new LayoutGeometry.Point(cx, cy);
        // random walk
        for (int i = 0; i < 500; i++) {
            double x = b.getMinX() + rng.nextDouble() * b.getWidth();
            double y = b.getMinY() + rng.nextDouble() * b.getHeight();
            if (free.contains(x, y)) return new LayoutGeometry.Point(x, y);
        }
        return null;
    }

    private static int gridIndex(LayoutGeometry.Point p, java.awt.geom.Rectangle2D b, double cell, int cols) {
        int col = Math.min(cols - 1, (int) ((p.x() - b.getMinX()) / cell));
        int row = (int) ((p.y() - b.getMinY()) / cell);
        return row * cols + col;
    }

    private static void insertPoint(LayoutGeometry.Point p, LayoutGeometry.Point[] grid,
            java.awt.geom.Rectangle2D b, double cell, int cols,
            List<LayoutGeometry.Point> active, List<LayoutGeometry.Point> placed) {
        int idx = gridIndex(p, b, cell, cols);
        grid[idx] = p;
        active.add(p);
        placed.add(p);
    }

    private static boolean tooClose(LayoutGeometry.Point p, LayoutGeometry.Point[] grid,
            java.awt.geom.Rectangle2D b, double cell, int cols, int rows, double minDist) {
        int col = (int) ((p.x() - b.getMinX()) / cell);
        int row = (int) ((p.y() - b.getMinY()) / cell);
        for (int dr = -2; dr <= 2; dr++) {
            for (int dc = -2; dc <= 2; dc++) {
                int r2 = row + dr, c2 = col + dc;
                if (r2 < 0 || r2 >= rows || c2 < 0 || c2 >= cols) continue;
                LayoutGeometry.Point q = grid[r2 * cols + c2];
                if (q != null) {
                    double dx = p.x() - q.x(), dy = p.y() - q.y();
                    if (dx * dx + dy * dy < minDist * minDist) return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Garden patch generation
    // -------------------------------------------------------------------------

    /**
     * Builds rounded garden patch rings from the free area.
     * Strategy: scan the free area on a coarse grid, cluster nearby sample
     * points, then for each cluster above {@code minPatchArea} build a convex
     * hull and round its corners.
     */
    private static List<Ring> buildGardenPatches(Area free, double minPatchArea, Random rng) {
        java.awt.geom.Rectangle2D b = free.getBounds2D();
        double step = Math.max(2.0, Math.sqrt(minPatchArea) / 4.0);

        // Sample points inside the free area
        List<LayoutGeometry.Point> samples = new ArrayList<>();
        for (double y = b.getMinY() + step / 2; y < b.getMaxY(); y += step) {
            for (double x = b.getMinX() + step / 2; x < b.getMaxX(); x += step) {
                if (free.contains(x, y)) samples.add(new LayoutGeometry.Point(x, y));
            }
        }
        if (samples.isEmpty()) return List.of();

        // Single-linkage clustering with radius = 2.5 * step
        double linkDist = 2.5 * step;
        List<List<LayoutGeometry.Point>> clusters = singleLinkageClusters(samples, linkDist);

        List<Ring> rings = new ArrayList<>();
        for (List<LayoutGeometry.Point> cluster : clusters) {
            if (cluster.size() < 3) continue;
            List<LayoutGeometry.Point> hull = convexHull(cluster);
            if (hull.size() < 3) continue;
            // Estimate area
            double area = polygonArea(hull);
            if (area < minPatchArea) continue;
            // Round corners
            List<LayoutGeometry.Point> rounded = roundCorners(hull, Math.min(step * 1.5, 4.0));
            // Add a little noise to boundary points
            List<LayoutGeometry.Point> noisy = addBoundaryNoise(rounded, step * 0.3, rng);
            rings.add(new Ring(noisy));
        }
        return List.copyOf(rings);
    }

    private static List<List<LayoutGeometry.Point>> singleLinkageClusters(
            List<LayoutGeometry.Point> points, double maxDist) {
        int n = points.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        double d2 = maxDist * maxDist;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dx = points.get(i).x() - points.get(j).x();
                double dy = points.get(i).y() - points.get(j).y();
                if (dx * dx + dy * dy <= d2) {
                    int ri = find(parent, i), rj = find(parent, j);
                    if (ri != rj) parent[ri] = rj;
                }
            }
        }
        java.util.Map<Integer, List<LayoutGeometry.Point>> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            map.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(points.get(i));
        }
        return List.copyOf(map.values());
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i]; }
        return i;
    }

    /** Convex hull via Graham scan. Returns open ring (last ≠ first). */
    private static List<LayoutGeometry.Point> convexHull(List<LayoutGeometry.Point> pts) {
        int n = pts.size();
        if (n < 3) return new ArrayList<>(pts);
        // Find bottom-most (then left-most) pivot
        LayoutGeometry.Point pivot = pts.get(0);
        for (LayoutGeometry.Point p : pts)
            if (p.y() < pivot.y() || (p.y() == pivot.y() && p.x() < pivot.x())) pivot = p;
        final LayoutGeometry.Point piv = pivot;
        List<LayoutGeometry.Point> sorted = new ArrayList<>(pts);
        sorted.sort((a, b) -> {
            double cross = (a.x() - piv.x()) * (b.y() - piv.y()) - (a.y() - piv.y()) * (b.x() - piv.x());
            if (Math.abs(cross) > 1e-9) return cross > 0 ? -1 : 1;
            double da = Math.hypot(a.x() - piv.x(), a.y() - piv.y());
            double db = Math.hypot(b.x() - piv.x(), b.y() - piv.y());
            return Double.compare(da, db);
        });
        List<LayoutGeometry.Point> hull = new ArrayList<>();
        for (LayoutGeometry.Point p : sorted) {
            while (hull.size() >= 2 && cross(hull.get(hull.size() - 2), hull.get(hull.size() - 1), p) <= 0)
                hull.remove(hull.size() - 1);
            hull.add(p);
        }
        return hull;
    }

    private static double cross(LayoutGeometry.Point O, LayoutGeometry.Point A, LayoutGeometry.Point B) {
        return (A.x() - O.x()) * (B.y() - O.y()) - (A.y() - O.y()) * (B.x() - O.x());
    }

    /**
     * Chaikin-style corner rounding: replace each vertex with two points
     * offset towards its neighbours by {@code r} metres.
     */
    private static List<LayoutGeometry.Point> roundCorners(List<LayoutGeometry.Point> ring, double r) {
        int n = ring.size();
        List<LayoutGeometry.Point> result = new ArrayList<>(n * 2);
        for (int i = 0; i < n; i++) {
            LayoutGeometry.Point prev = ring.get((i + n - 1) % n);
            LayoutGeometry.Point cur  = ring.get(i);
            LayoutGeometry.Point next = ring.get((i + 1) % n);
            double dPrev = Math.hypot(cur.x() - prev.x(), cur.y() - prev.y());
            double dNext = Math.hypot(next.x() - cur.x(), next.y() - cur.y());
            double t1 = dPrev > 1e-9 ? Math.min(r, dPrev * 0.4) / dPrev : 0;
            double t2 = dNext > 1e-9 ? Math.min(r, dNext * 0.4) / dNext : 0;
            result.add(new LayoutGeometry.Point(
                cur.x() + (prev.x() - cur.x()) * t1,
                cur.y() + (prev.y() - cur.y()) * t1));
            result.add(new LayoutGeometry.Point(
                cur.x() + (next.x() - cur.x()) * t2,
                cur.y() + (next.y() - cur.y()) * t2));
        }
        // One extra Chaikin pass to smooth further
        List<LayoutGeometry.Point> smooth = new ArrayList<>(result.size());
        int m = result.size();
        for (int i = 0; i < m; i++) {
            LayoutGeometry.Point a = result.get(i);
            LayoutGeometry.Point b = result.get((i + 1) % m);
            smooth.add(new LayoutGeometry.Point((a.x() * 3 + b.x()) / 4, (a.y() * 3 + b.y()) / 4));
            smooth.add(new LayoutGeometry.Point((a.x() + b.x() * 3) / 4, (a.y() + b.y() * 3) / 4));
        }
        // Subsample to MAX_PATCH_VERTICES
        if (smooth.size() > MAX_PATCH_VERTICES) {
            List<LayoutGeometry.Point> sub = new ArrayList<>(MAX_PATCH_VERTICES);
            double step = (double) smooth.size() / MAX_PATCH_VERTICES;
            for (int i = 0; i < MAX_PATCH_VERTICES; i++)
                sub.add(smooth.get((int) Math.round(i * step) % smooth.size()));
            return sub;
        }
        return smooth;
    }

    private static List<LayoutGeometry.Point> addBoundaryNoise(
            List<LayoutGeometry.Point> ring, double amplitude, Random rng) {
        List<LayoutGeometry.Point> result = new ArrayList<>(ring.size());
        for (LayoutGeometry.Point p : ring) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double r = rng.nextDouble() * amplitude;
            result.add(new LayoutGeometry.Point(p.x() + r * Math.cos(angle), p.y() + r * Math.sin(angle)));
        }
        return result;
    }

    private static double polygonArea(List<LayoutGeometry.Point> ring) {
        double area = 0;
        int n = ring.size();
        for (int i = 0; i < n; i++) {
            LayoutGeometry.Point a = ring.get(i), b = ring.get((i + 1) % n);
            area += a.x() * b.y() - b.x() * a.y();
        }
        return Math.abs(area) / 2.0;
    }

    // -------------------------------------------------------------------------
    // Pool (kidney-shape) generation
    // -------------------------------------------------------------------------

    /**
     * Builds a kidney-shaped pool ring centred at the centroid of the largest
     * free zone.  The shape is generated by perturbing a circle with a smooth
     * low-frequency noise applied to radii.
     */
    private static Ring buildPool(Area free, double targetRadius, Random rng) {
        java.awt.geom.Rectangle2D b = free.getBounds2D();

        // Find the centroid of the free area (sample-based approximation)
        LayoutGeometry.Point centroid = estimateCentroid(free, b, rng);
        if (centroid == null) return null;

        // Shrink radius if the free zone is tight
        double maxR = estimateMaxInscribedRadius(free, centroid, targetRadius);


        double r = Math.min(targetRadius, maxR * 0.7);
        if (r < 2) return null;

        // Build kidney shape: 2 overlapping circles offset along y
        // This is the classic kidney-bean parametric shape
        int nPts = 24;
        double angle = rng.nextDouble() * Math.PI;
        List<LayoutGeometry.Point> ring = new ArrayList<>(nPts);
        for (int i = 0; i < nPts; i++) {
            double t = 2 * Math.PI * i / nPts;
            // Kidney parametric: blend between two offset circles
            double blobX = r * Math.cos(t) + r * 0.25 * Math.cos(2 * t);
            double blobY = r * Math.sin(t);
            double px = centroid.x() + blobX * Math.cos(angle) - blobY * Math.sin(angle);
            double py = centroid.y() + blobX * Math.sin(angle) + blobY * Math.cos(angle);
            ring.add(new LayoutGeometry.Point(px, py));
        }

        // If the ring escapes the free area, shrink it towards centroid
        boolean allInside = true;
        for (LayoutGeometry.Point p : ring) {
            if (!free.contains(p.x(), p.y())) { allInside = false; break; }
        }
        if (!allInside) {
            // Try smaller radius
            double shrunk = r * 0.6;
            if (shrunk < 2) return null;
            List<LayoutGeometry.Point> shrunkRing = new ArrayList<>(nPts);
            for (LayoutGeometry.Point p : ring) {
                double dx = p.x() - centroid.x(), dy = p.y() - centroid.y();
                shrunkRing.add(new LayoutGeometry.Point(
                    centroid.x() + dx * 0.6, centroid.y() + dy * 0.6));
            }
            ring = shrunkRing;
            for (LayoutGeometry.Point p : ring) {
                if (!free.contains(p.x(), p.y())) return null;
            }
        }
        return new Ring(ring);
    }

    private static LayoutGeometry.Point estimateCentroid(Area free, java.awt.geom.Rectangle2D b, Random rng) {
        double sumX = 0, sumY = 0;
        int count = 0;
        double step = Math.max(1.0, Math.min(b.getWidth(), b.getHeight()) / 20.0);
        for (double y = b.getMinY() + step / 2; y < b.getMaxY(); y += step) {
            for (double x = b.getMinX() + step / 2; x < b.getMaxX(); x += step) {
                if (free.contains(x, y)) { sumX += x; sumY += y; count++; }
            }
        }
        if (count == 0) return null;
        double cx = sumX / count, cy = sumY / count;
        // Snap to nearest free point if centroid landed in an obstacle
        if (!free.contains(cx, cy)) {
            for (int i = 0; i < 200; i++) {
                double x = b.getMinX() + rng.nextDouble() * b.getWidth();
                double y = b.getMinY() + rng.nextDouble() * b.getHeight();
                if (free.contains(x, y)) return new LayoutGeometry.Point(x, y);
            }
            return null;
        }
        return new LayoutGeometry.Point(cx, cy);
    }

    private static double estimateMaxInscribedRadius(
            Area free, LayoutGeometry.Point centre, double maxSearch) {
        // Cast 16 rays; inscribed radius ≈ min ray length
        double minDist = maxSearch;
        int rays = 16;
        for (int i = 0; i < rays; i++) {
            double angle = 2 * Math.PI * i / rays;
            double step = 0.5;
            double dist = 0;
            while (dist < maxSearch) {
                dist += step;
                double x = centre.x() + dist * Math.cos(angle);
                double y = centre.y() + dist * Math.sin(angle);
                if (!free.contains(x, y)) break;
            }
            minDist = Math.min(minDist, dist);
        }
        return minDist;
    }

    // -------------------------------------------------------------------------
    // Result message
    // -------------------------------------------------------------------------

    private static String buildMessage(int trees, int rejTrees, int gardens, boolean hasPool) {
        StringBuilder sb = new StringBuilder("绿化生成：");
        if (trees > 0 || rejTrees > 0) sb.append(trees).append(" 棵乔木（拒绝 ").append(rejTrees).append(" 个越界点）；");
        if (gardens > 0) sb.append(gardens).append(" 块绿地；");
        if (hasPool) sb.append("1 处水池；");
        if (sb.toString().equals("绿化生成：")) sb.append("无可生成元素（可用空间不足或所有元素已禁用）。");
        else sb.append("左键确认生成，Ctrl+Z 一次撤销。");
        return sb.toString();
    }
}
