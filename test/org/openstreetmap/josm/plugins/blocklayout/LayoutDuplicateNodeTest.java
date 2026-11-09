package org.openstreetmap.josm.plugins.blocklayout;

import java.util.ArrayList;
import java.util.List;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;

/**
 * Regression test for the duplicated closing node defect.
 *
 * JOSM marks a way as closed by repeating its first node at the end of the node list.
 * The plugin used to read such rings verbatim and then close them a second time, so every
 * generated building ended up with a trailing duplicate node.
 */
public final class LayoutDuplicateNodeTest {
    public static void main(String[] args) {
        java.io.File home=new java.io.File("build-layout/dupnode-home");home.mkdirs();System.setProperty("josm.home",home.getAbsolutePath());
        org.openstreetmap.josm.data.preferences.JosmBaseDirectories dirs=org.openstreetmap.josm.data.preferences.JosmBaseDirectories.getInstance();
        org.openstreetmap.josm.spi.preferences.Config.setBaseDirectoriesProvider(dirs);
        org.openstreetmap.josm.data.Preferences prefs=new org.openstreetmap.josm.data.Preferences();prefs.enableSaveOnPut(false);
        org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(prefs);

        // --- 1. Geometry layer: a sample ring carrying a closure duplicate must not leak it ---
        List<LayoutGeometry.Point> duplicated = List.of(
                new LayoutGeometry.Point(0, 0),
                new LayoutGeometry.Point(48, 0),
                new LayoutGeometry.Point(48, 12),
                new LayoutGeometry.Point(0, 12),
                new LayoutGeometry.Point(0, 0));
        List<LayoutGeometry.Parcel> parcels = List.of(new LayoutGeometry.Parcel(List.of(
                new LayoutGeometry.Point(-200, -200),
                new LayoutGeometry.Point(600, -200),
                new LayoutGeometry.Point(600, 400),
                new LayoutGeometry.Point(-200, 400))));

        LayoutGeometry.Settings settings = new LayoutGeometry.Settings(12, 48, 18, 6, 12, 24, false, 1.5, false);
        LayoutGeometry.FootprintResult result = LayoutGeometry.layoutFootprints(
                parcels, List.of(), new LayoutGeometry.Footprint(duplicated), 12, settings);
        if (result.buildings().isEmpty()) throw new AssertionError("Sample layout should place at least one building");
        for (LayoutGeometry.Footprint footprint : result.buildings()) {
            assertNoClosureDuplicate(footprint.ring(), "generated footprint");
        }

        // --- 2. Command layer: closing must happen exactly once ---
        LayoutSettings panelSettings = new LayoutSettings(12, 48, 18, 6, 12, 24, false, 1.5, false, "apartments");

        // A ring that is already closed (duplicate present) must not gain a second duplicate.
        Way fromClosed = BlockLayoutMode.toBuildingWay(duplicated, p -> new LatLon(30 + p.y() / 100000, 104 + p.x() / 100000), panelSettings);
        assertSingleClosure(fromClosed, "toBuildingWay on an already-closed ring");

        // A ring without a duplicate must still end up closed, with exactly one closing node.
        List<LayoutGeometry.Point> open = List.of(
                new LayoutGeometry.Point(0, 0),
                new LayoutGeometry.Point(48, 0),
                new LayoutGeometry.Point(48, 12),
                new LayoutGeometry.Point(0, 12));
        Way fromOpen = BlockLayoutMode.toBuildingWay(open, p -> new LatLon(30 + p.y() / 100000, 104 + p.x() / 100000), panelSettings);
        assertSingleClosure(fromOpen, "toBuildingWay on an open ring");
        if (!fromOpen.isClosed()) throw new AssertionError("Generated building way must be closed");

        // Shop rings go through a different factory; it must behave the same way.
        Way shop = BlockLayoutMode.toShopWay(duplicated, p -> new LatLon(30 + p.y() / 100000, 104 + p.x() / 100000), 5);
        assertSingleClosure(shop, "toShopWay on an already-closed ring");

        Way shopOpen = BlockLayoutMode.toShopWay(open, p -> new LatLon(30 + p.y() / 100000, 104 + p.x() / 100000), 5);
        assertSingleClosure(shopOpen, "toShopWay on an open ring");

        // --- 3. A way built from a duplicate-carrying ring must report exactly one closing node ---
        // JOSM only treats a way as closed when the first and last node are the very same object.
        Node shared = new Node(new LatLon(30.0, 104.0));
        List<Node> nodes = new ArrayList<>();
        nodes.add(shared);
        nodes.add(new Node(new LatLon(30.0, 104.0001)));
        nodes.add(new Node(new LatLon(30.0001, 104.0001)));
        nodes.add(new Node(new LatLon(30.0001, 104.0)));
        nodes.add(shared);
        Way manual = new Way();
        manual.setNodes(new ArrayList<>(nodes));
        if (manual.getNodesCount() != 5) throw new AssertionError("Fixture should carry five nodes");
        if (!manual.isClosed()) throw new AssertionError("Fixture should be recognised as closed");

        // The defect this plugin had: reading that ring and closing it again produced two
        // trailing copies of the first node.
        List<LayoutGeometry.Point> reread = new ArrayList<>();
        for (Node node : manual.getNodes()) reread.add(new LayoutGeometry.Point(node.getCoor().lon(), node.getCoor().lat()));
        Way rereadWay = BlockLayoutMode.toBuildingWay(reread, p -> new LatLon(30 + p.y() / 100000, 104 + p.x() / 100000), panelSettings);
        assertSingleClosure(rereadWay, "toBuildingWay on a ring reread from a closed JOSM way");

        System.out.println("PASS LayoutDuplicateNodeTest: closing node is applied exactly once; no duplicate closing node leaks into output");
    }

    private static void assertNoClosureDuplicate(List<LayoutGeometry.Point> ring, String what) {
        if (ring.size() < 3) throw new AssertionError(what + " must have at least three vertices");
        LayoutGeometry.Point first = ring.get(0);
        LayoutGeometry.Point last = ring.get(ring.size() - 1);
        if (Math.abs(first.x() - last.x()) < 1e-9 && Math.abs(first.y() - last.y()) < 1e-9) {
            throw new AssertionError(what + " must not repeat its first vertex at the end");
        }
    }

    private static void assertSingleClosure(Way way, String what) {
        List<Node> wayNodes = way.getNodes();
        if (wayNodes.size() < 4) throw new AssertionError(what + " needs at least four nodes");
        Node first = wayNodes.get(0);
        Node last = wayNodes.get(wayNodes.size() - 1);
        if (first != last && !first.getCoor().equals(last.getCoor())) {
            throw new AssertionError(what + " must repeat its first node at the end");
        }
        // The defect: the first node appeared twice in a row at the tail.
        if (wayNodes.size() >= 2) {
            Node secondToLast = wayNodes.get(wayNodes.size() - 2);
            Node thirdToLast = wayNodes.get(wayNodes.size() - 3);
            if (secondToLast == thirdToLast) {
                throw new AssertionError(what + " contains a duplicated closing node");
            }
        }
        long distinctCoordinates = wayNodes.stream().map(Node::getCoor).distinct().count();
        if (distinctCoordinates != wayNodes.size() - 1) {
            throw new AssertionError(what + " should repeat exactly one node, found " + (wayNodes.size() - distinctCoordinates) + " repeats");
        }
    }
}
