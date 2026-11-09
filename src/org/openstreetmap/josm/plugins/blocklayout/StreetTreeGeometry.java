package org.openstreetmap.josm.plugins.blocklayout;

import java.util.ArrayList;
import java.util.List;

/** Pure local-metre street tree placement geometry for JOSM block-layout plugin. */
public final class StreetTreeGeometry {
    private static final double EPS = 1e-7;
    private static final double MIN_SPACING_M = 4.0;
    private static final double DEFAULT_SPACING_M = 6.0;
    private static final int MAX_TREES = 2000;

    private StreetTreeGeometry() { }

    public record Settings(
        double spacing,
        double offset,
        boolean doubleSide
    ) {
        public Settings {
            if (!Double.isFinite(spacing) || spacing < MIN_SPACING_M) 
                throw new IllegalArgumentException("行道树间距必须至少 " + MIN_SPACING_M + " 米。");
            if (!Double.isFinite(offset) || offset < 0) 
                throw new IllegalArgumentException("行道树距道路中心偏移必须为非负值。");
            if (offset > 100) 
                throw new IllegalArgumentException("行道树偏移不能超过 100 米。");
        }
    }

    public record Result(
        List<LayoutGeometry.Point> trees,
        int rejected,
        String message
    ) {
        public Result { trees = List.copyOf(trees); }
    }

    /**
     * Places street trees along roads with service highway tag inside parcels.
     * 
     * @param parcels       Selected land use parcels
     * @param serviceRoads  Roads with highway=service inside parcels
     * @param settings      Spacing and offset settings
     * @return              Tree placement result
     */
    public static Result layoutServiceRoadTrees(
            List<LayoutGeometry.Parcel> parcels,
            List<LayoutGeometry.Road> serviceRoads,
            Settings settings) {
        if (parcels == null || parcels.isEmpty()) 
            throw new IllegalArgumentException("请先选择至少一个闭合地块。");
        if (serviceRoads == null || serviceRoads.isEmpty()) 
            return new Result(List.of(), 0, "地块内没有发现 service 道路。");

        List<java.awt.geom.Area> parcelAreas = new ArrayList<>();
        for (LayoutGeometry.Parcel parcel : parcels) {
            java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
            List<LayoutGeometry.Point> ring = parcel.ring();
            if (ring.isEmpty()) continue;
            path.moveTo(ring.get(0).x(), ring.get(0).y());
            for (int i = 1; i < ring.size(); i++) {
                path.lineTo(ring.get(i).x(), ring.get(i).y());
            }
            path.closePath();
            parcelAreas.add(new java.awt.geom.Area(path));
        }

        List<LayoutGeometry.Point> placed = new ArrayList<>();
        int rejected = 0;

        for (LayoutGeometry.Road road : serviceRoads) {
            List<LayoutGeometry.Point> centerline = road.centerline();
            if (centerline.size() < 2) continue;

            double halfWidth = road.width() / 2.0;
            double offset = halfWidth + settings.offset();

            // Generate points along the centerline
            for (int segIdx = 0; segIdx < centerline.size() - 1; segIdx++) {
                LayoutGeometry.Point a = centerline.get(segIdx);
                LayoutGeometry.Point b = centerline.get(segIdx + 1);
                double dx = b.x() - a.x();
                double dy = b.y() - a.y();
                double length = Math.hypot(dx, dy);
                if (length < EPS) continue;

                // Unit direction vector
                double ux = dx / length;
                double uy = dy / length;

                // Normal vectors (perpendicular)
                double nx = -uy;
                double ny = ux;

                // Number of trees along this segment
                int count = (int) Math.floor(length / settings.spacing());
                for (int i = 0; i <= count; i++) {
                    double t = i * settings.spacing();
                    if (t > length + EPS) break;

                    double cx = a.x() + t * ux;
                    double cy = a.y() + t * uy;

                    // Place on the right side
                    LayoutGeometry.Point right = new LayoutGeometry.Point(
                        cx + nx * offset, 
                        cy + ny * offset
                    );
                    if (isInsideParcels(right, parcelAreas)) {
                        if (placed.size() >= MAX_TREES) {
                            return new Result(placed, rejected, 
                                "行道树数量超过 " + MAX_TREES + " 棵上限；请减小选择范围或增大间距。");
                        }
                        placed.add(right);
                    } else {
                        rejected++;
                    }

                    // Place on the left side if double-sided
                    if (settings.doubleSide()) {
                        LayoutGeometry.Point left = new LayoutGeometry.Point(
                            cx - nx * offset, 
                            cy - ny * offset
                        );
                        if (isInsideParcels(left, parcelAreas)) {
                            if (placed.size() >= MAX_TREES) {
                                return new Result(placed, rejected, 
                                    "行道树数量超过 " + MAX_TREES + " 棵上限；请减小选择范围或增大间距。");
                            }
                            placed.add(left);
                        } else {
                            rejected++;
                        }
                    }
                }
            }
        }

        String message = placed.isEmpty() 
            ? "没有找到可完整放入地块的行道树位置。" 
            : String.format("可生成 %d 棵行道树；拒绝 %d 个越界位置。", placed.size(), rejected);
        return new Result(placed, rejected, message);
    }

    /**
     * Places street trees along a selected road with a road-type highway tag.
     * 
     * @param road      Selected road with centerline
     * @param settings  Spacing and offset settings
     * @return          Tree placement result
     */
    public static Result layoutRoadTrees(
            LayoutGeometry.Road road,
            Settings settings) {
        if (road == null || road.centerline().isEmpty()) 
            throw new IllegalArgumentException("请先选择一条有效道路。");

        List<LayoutGeometry.Point> centerline = road.centerline();
        if (centerline.size() < 2) 
            return new Result(List.of(), 0, "所选道路无有效线段。");

        double halfWidth = road.width() / 2.0;
        double offset = halfWidth + settings.offset();

        List<LayoutGeometry.Point> placed = new ArrayList<>();
        int rejected = 0;

        for (int segIdx = 0; segIdx < centerline.size() - 1; segIdx++) {
            LayoutGeometry.Point a = centerline.get(segIdx);
            LayoutGeometry.Point b = centerline.get(segIdx + 1);
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double length = Math.hypot(dx, dy);
            if (length < EPS) continue;

            double ux = dx / length;
            double uy = dy / length;
            double nx = -uy;
            double ny = ux;

            int count = (int) Math.floor(length / settings.spacing());
            for (int i = 0; i <= count; i++) {
                double t = i * settings.spacing();
                if (t > length + EPS) break;

                double cx = a.x() + t * ux;
                double cy = a.y() + t * uy;

                LayoutGeometry.Point right = new LayoutGeometry.Point(
                    cx + nx * offset, 
                    cy + ny * offset
                );
                if (placed.size() >= MAX_TREES) {
                    return new Result(placed, rejected, 
                        "行道树数量超过 " + MAX_TREES + " 棵上限；请减小道路范围或增大间距。");
                }
                placed.add(right);

                if (settings.doubleSide()) {
                    LayoutGeometry.Point left = new LayoutGeometry.Point(
                        cx - nx * offset, 
                        cy - ny * offset
                    );
                    if (placed.size() >= MAX_TREES) {
                        return new Result(placed, rejected, 
                            "行道树数量超过 " + MAX_TREES + " 棵上限；请减小道路范围或增大间距。");
                    }
                    placed.add(left);
                }
            }
        }

        String message = String.format("可生成 %d 棵行道树。", placed.size());
        return new Result(placed, rejected, message);
    }

    private static boolean isInsideParcels(
            LayoutGeometry.Point point, 
            List<java.awt.geom.Area> parcelAreas) {
        for (java.awt.geom.Area area : parcelAreas) {
            if (area.contains(point.x(), point.y())) {
                return true;
            }
        }
        return false;
    }
}
