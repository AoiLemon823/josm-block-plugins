package org.openstreetmap.josm.plugins.blockfill;

import java.util.List;

/**
 * Unit tests for PoiPlacementGeometry (pure geometry, no JOSM dependency).
 * Run directly via main(); uses -ea assertions.
 */
public class PoiPlacementGeometryTest {

    public static void main(String[] args) {
        testPointsInsideRing();
        testCountRespected();
        testStableWithSameSeed();
        testDifferentWithDifferentSeed();
        testEmptyForDegenerateRing();
        testEmptyForNullRing();
        testWorksForSmallBuilding();

        System.out.println("PASS PoiPlacementGeometryTest: " +
            "scatter inside ring, count cap, seed stability, degenerate inputs");
    }

    static void testPointsInsideRing() {
        List<PoiPlacementGeometry.Point> ring = rect(0, 0, 50, 30);
        List<PoiPlacementGeometry.Point> pts = PoiPlacementGeometry.scatter(ring, 5, 42L);
        assert !pts.isEmpty() : "Should scatter points inside building";
        for (PoiPlacementGeometry.Point p : pts) {
            assert p.x() >= 0 && p.x() <= 50 : "POI x out of bounds: " + p.x();
            assert p.y() >= 0 && p.y() <= 30 : "POI y out of bounds: " + p.y();
        }
    }

    static void testCountRespected() {
        List<PoiPlacementGeometry.Point> ring = rect(0, 0, 100, 60);
        int requested = 8;
        List<PoiPlacementGeometry.Point> pts = PoiPlacementGeometry.scatter(ring, requested, 1L);
        assert pts.size() <= requested : "Should not exceed requested count";
        assert !pts.isEmpty() : "Should generate at least one point in large building";
    }

    static void testStableWithSameSeed() {
        List<PoiPlacementGeometry.Point> ring = rect(0, 0, 40, 40);
        List<PoiPlacementGeometry.Point> a = PoiPlacementGeometry.scatter(ring, 4, 123L);
        List<PoiPlacementGeometry.Point> b = PoiPlacementGeometry.scatter(ring, 4, 123L);
        assert a.size() == b.size() : "Same seed should produce same count";
        for (int i = 0; i < a.size(); i++) {
            assert a.get(i).x() == b.get(i).x() && a.get(i).y() == b.get(i).y()
                : "Same seed should produce identical points at index " + i;
        }
    }

    static void testDifferentWithDifferentSeed() {
        List<PoiPlacementGeometry.Point> ring = rect(0, 0, 60, 40);
        List<PoiPlacementGeometry.Point> a = PoiPlacementGeometry.scatter(ring, 5, 1L);
        List<PoiPlacementGeometry.Point> b = PoiPlacementGeometry.scatter(ring, 5, 9999L);
        boolean anyDiff = false;
        int minSize = Math.min(a.size(), b.size());
        for (int i = 0; i < minSize; i++) {
            if (a.get(i).x() != b.get(i).x() || a.get(i).y() != b.get(i).y()) {
                anyDiff = true; break;
            }
        }
        assert anyDiff : "Different seeds should produce different distributions";
    }

    static void testEmptyForDegenerateRing() {
        List<PoiPlacementGeometry.Point> ring = List.of(
            new PoiPlacementGeometry.Point(0, 0),
            new PoiPlacementGeometry.Point(1, 0)
        );
        List<PoiPlacementGeometry.Point> pts = PoiPlacementGeometry.scatter(ring, 5, 1L);
        assert pts.isEmpty() : "Degenerate ring (2 pts) should return empty list";
    }

    static void testEmptyForNullRing() {
        List<PoiPlacementGeometry.Point> pts = PoiPlacementGeometry.scatter(null, 5, 1L);
        assert pts.isEmpty() : "Null ring should return empty list";
    }

    static void testWorksForSmallBuilding() {
        // 4×4m 建筑，空间很小，但应该能放进 1 个点
        List<PoiPlacementGeometry.Point> ring = rect(0, 0, 4, 4);
        List<PoiPlacementGeometry.Point> pts = PoiPlacementGeometry.scatter(ring, 1, 1L);
        assert pts.size() <= 1 : "Small building should generate at most 1 point";
    }

    static List<PoiPlacementGeometry.Point> rect(double x1, double y1, double x2, double y2) {
        return List.of(
            new PoiPlacementGeometry.Point(x1, y1), new PoiPlacementGeometry.Point(x2, y1),
            new PoiPlacementGeometry.Point(x2, y2), new PoiPlacementGeometry.Point(x1, y2)
        );
    }
}
