package org.openstreetmap.josm.plugins.blocklayout;

import java.util.List;

/**
 * Unit tests for GreeneryGeometry (pure geometry, no JOSM dependency).
 * Run directly via main(); uses -ea assertions.
 */
public class GreeneryGeometryTest {

    public static void main(String[] args) {
        testPoissonTreesInsideParcel();
        testPoissonRespectsMinDistance();
        testNoTreesWhenObstacleCoversEverything();
        testGardenPatchesGeneratedForLargeParcel();
        testPoolGeneratedForLargeParcel();
        testPoolSkippedForTinyParcel();
        testResultMessageNotEmpty();

        System.out.println("PASS GreeneryGeometryTest: " +
            "tree placement, min-distance, obstacle masking, garden patches, pool");
    }

    // ── 散点乔木 ──────────────────────────────────────────────────────────────

    static void testPoissonTreesInsideParcel() {
        List<LayoutGeometry.Parcel> parcels = List.of(box(0, 0, 100, 100));
        GreeneryGeometry.Settings s = settings(true, 8, false, 9999, false, 5, 2, 2, 42L);
        GreeneryGeometry.Result r = GreeneryGeometry.layout(parcels, List.of(), List.of(), s);
        assert !r.trees().isEmpty() : "Should generate trees in a large open parcel";
        for (LayoutGeometry.Point p : r.trees()) {
            assert p.x() >= -1 && p.x() <= 101 : "Tree x out of bounds: " + p.x();
            assert p.y() >= -1 && p.y() <= 101 : "Tree y out of bounds: " + p.y();
        }
    }

    static void testPoissonRespectsMinDistance() {
        List<LayoutGeometry.Parcel> parcels = List.of(box(0, 0, 60, 60));
        double minDist = 10.0;
        GreeneryGeometry.Settings s = settings(true, minDist, false, 9999, false, 5, 2, 2, 7L);
        GreeneryGeometry.Result r = GreeneryGeometry.layout(parcels, List.of(), List.of(), s);
        List<LayoutGeometry.Point> trees = r.trees();
        for (int i = 0; i < trees.size(); i++) {
            for (int j = i + 1; j < trees.size(); j++) {
                double dx = trees.get(i).x() - trees.get(j).x();
                double dy = trees.get(i).y() - trees.get(j).y();
                double dist = Math.sqrt(dx * dx + dy * dy);
                assert dist >= minDist - 0.5 :
                    String.format("Trees too close: %.2fm < %.2fm", dist, minDist);
            }
        }
    }

    static void testNoTreesWhenObstacleCoversEverything() {
        List<LayoutGeometry.Parcel> parcels = List.of(box(0, 0, 20, 20));
        List<LayoutGeometry.Footprint> buildings = List.of(footprint(0, 0, 20, 20));
        GreeneryGeometry.Settings s = settings(true, 4, false, 9999, false, 5, 10, 2, 1L);
        GreeneryGeometry.Result r = GreeneryGeometry.layout(parcels, buildings, List.of(), s);
        assert r.trees().isEmpty() : "Should generate no trees when building covers parcel";
    }

    // ── 绿地草坪 ──────────────────────────────────────────────────────────────

    static void testGardenPatchesGeneratedForLargeParcel() {
        List<LayoutGeometry.Parcel> parcels = List.of(box(0, 0, 80, 80));
        GreeneryGeometry.Settings s = settings(false, 5, true, 20, false, 5, 2, 2, 99L);
        GreeneryGeometry.Result r = GreeneryGeometry.layout(parcels, List.of(), List.of(), s);
        assert !r.gardenRings().isEmpty() : "Should generate garden patches for large parcel";
        for (GreeneryGeometry.Ring ring : r.gardenRings()) {
            assert ring.points().size() >= 3 : "Garden ring must have >= 3 points";
        }
    }

    // ── 水池 ──────────────────────────────────────────────────────────────────

    static void testPoolGeneratedForLargeParcel() {
        List<LayoutGeometry.Parcel> parcels = List.of(box(0, 0, 60, 60));
        GreeneryGeometry.Settings s = settings(false, 5, false, 9999, true, 6, 2, 2, 55L);
        GreeneryGeometry.Result r = GreeneryGeometry.layout(parcels, List.of(), List.of(), s);
        assert r.poolRing() != null : "Should generate a pool in a large open parcel";
        assert r.poolRing().points().size() >= 4 : "Pool ring must have >= 4 points";
    }

    static void testPoolSkippedForTinyParcel() {
        List<LayoutGeometry.Parcel> parcels = List.of(box(0, 0, 5, 5));
        GreeneryGeometry.Settings s = settings(false, 5, false, 9999, true, 6, 0, 0, 1L);
        GreeneryGeometry.Result r = GreeneryGeometry.layout(parcels, List.of(), List.of(), s);
        assert r.poolRing() == null : "Should skip pool when parcel is too small";
    }

    // ── 结果消息 ──────────────────────────────────────────────────────────────

    static void testResultMessageNotEmpty() {
        List<LayoutGeometry.Parcel> parcels = List.of(box(0, 0, 100, 100));
        GreeneryGeometry.Settings s = settings(true, 8, true, 20, true, 5, 2, 2, 11L);
        GreeneryGeometry.Result r = GreeneryGeometry.layout(parcels, List.of(), List.of(), s);
        assert r.message() != null && !r.message().isEmpty() : "Result message should not be empty";
    }

    // ── 工厂方法 ──────────────────────────────────────────────────────────────

    static LayoutGeometry.Parcel box(double x1, double y1, double x2, double y2) {
        return new LayoutGeometry.Parcel(List.of(
            new LayoutGeometry.Point(x1, y1), new LayoutGeometry.Point(x2, y1),
            new LayoutGeometry.Point(x2, y2), new LayoutGeometry.Point(x1, y2)
        ));
    }

    static LayoutGeometry.Footprint footprint(double x1, double y1, double x2, double y2) {
        return new LayoutGeometry.Footprint(List.of(
            new LayoutGeometry.Point(x1, y1), new LayoutGeometry.Point(x2, y1),
            new LayoutGeometry.Point(x2, y2), new LayoutGeometry.Point(x1, y2)
        ));
    }

    static GreeneryGeometry.Settings settings(
            boolean trees, double treeSpacing,
            boolean gardens, double minPatch,
            boolean pool, double poolR,
            double bldClear, double roadClear, long seed) {
        return new GreeneryGeometry.Settings(
            trees, treeSpacing, gardens, minPatch, pool, poolR, bldClear, roadClear, seed);
    }
}
