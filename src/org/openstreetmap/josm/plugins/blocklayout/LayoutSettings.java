package org.openstreetmap.josm.plugins.blocklayout;

import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Settings;

/** Persistent-independent UI values shared only inside the layout plugin. */
public record LayoutSettings(double width, double length, double height, double endSetback,
        double sideClearance, double rowSpacing, boolean sunSpacing, double sunFactor,
        boolean staggered, String buildingTag) {
    public Settings geometry() { return new Settings(width,length,height,endSetback,sideClearance,rowSpacing,sunSpacing,sunFactor,staggered); }
    public void validate() {
        LayoutGeometry.validate(geometry());
        if (!java.util.Set.of("apartments","residential","yes").contains(buildingTag)) throw new IllegalArgumentException("建筑类型无效。");
    }
}
