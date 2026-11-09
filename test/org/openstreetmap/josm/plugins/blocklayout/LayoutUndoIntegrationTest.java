package org.openstreetmap.josm.plugins.blocklayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;

/** Integration test against the actual JOSM DataSet, AddCommand, SequenceCommand and undo queue. */
public final class LayoutUndoIntegrationTest {
    public static void main(String[] args) {
        java.io.File home=new java.io.File("build-layout/undo-home");home.mkdirs();System.setProperty("josm.home",home.getAbsolutePath());
        org.openstreetmap.josm.data.preferences.JosmBaseDirectories dirs=org.openstreetmap.josm.data.preferences.JosmBaseDirectories.getInstance();
        org.openstreetmap.josm.spi.preferences.Config.setBaseDirectoriesProvider(dirs);
        org.openstreetmap.josm.data.Preferences prefs=new org.openstreetmap.josm.data.Preferences();prefs.enableSaveOnPut(false);
        org.openstreetmap.josm.spi.preferences.Config.setPreferencesInstance(prefs);
        DataSet ds=new DataSet();List<Command> commands=new ArrayList<>();List<Node> nodes=new ArrayList<>();
        nodes.add(new Node(new LatLon(30.0,104.0)));nodes.add(new Node(new LatLon(30.0,104.0001)));
        nodes.add(new Node(new LatLon(30.0001,104.0001)));nodes.add(new Node(new LatLon(30.0001,104.0)));
        nodes.add(nodes.get(0));
        Way source=new Way();source.setNodes(new ArrayList<>(nodes));source.put("building","apartments");
        LayoutSettings settings=new LayoutSettings(12,48,18,6,12,24,false,1.5,false,"apartments");
        List<LayoutGeometry.Point> sampleRing=source.getNodes().stream().map(n->new LayoutGeometry.Point(n.lon(),n.lat())).toList();
        Way building=BlockLayoutMode.toBuildingWay(sampleRing,p->new LatLon(p.y(),p.x()),settings);
        for(Node n:building.getNodes().subList(0,building.getNodesCount()-1))commands.add(new AddCommand(ds,n));
        commands.add(new AddCommand(ds,building));
        if(!building.isClosed() || !"apartments".equals(building.get("building")))throw new AssertionError("Generated building way is malformed");
        if(!"18".equals(building.get("height"))||!"6".equals(building.get("building:levels")))throw new AssertionError("Generated tags must match sample-mode settings");
        if(building.getNodes().get(0).getCoor().distance(building.getNodes().get(1).getCoor())==0)throw new AssertionError("Sample footprint coordinates must be retained");
        java.util.function.Function<LayoutGeometry.Point,LatLon> inverse=p->new LatLon(30+p.y()/100000,104+p.x()/100000);
        List<LayoutGeometry.Point> ring=List.of(new LayoutGeometry.Point(0,0),new LayoutGeometry.Point(20,0),new LayoutGeometry.Point(20,12),new LayoutGeometry.Point(0,12));
        LayoutGeometry.ShopPolygon polygon=new LayoutGeometry.ShopPolygon(ring,List.of(List.of(new LayoutGeometry.Point(5,2),new LayoutGeometry.Point(8,2),new LayoutGeometry.Point(8,5),new LayoutGeometry.Point(5,5))));
        Relation retail=BlockLayoutMode.toShopRelation(polygon,inverse,ds,5,commands);commands.add(new AddCommand(ds,retail));
        if(!retail.isMultipolygon()||retail.getMembersCount()!=2||!"retail".equals(retail.get("building")))throw new AssertionError("Shop difference must be represented by outer and inner multipolygon rings");
        LayoutGeometry.Road road=new LayoutGeometry.Road(List.of(new LayoutGeometry.Point(1,1),new LayoutGeometry.Point(9,1)),5);
        java.util.Map<String,Node> generatedNodes=new java.util.LinkedHashMap<>();
        Way service=BlockLayoutMode.toRoadWay(road,inverse,ds,commands,generatedNodes);commands.add(new AddCommand(ds,service));
        LayoutGeometry.Road connector=new LayoutGeometry.Road(List.of(new LayoutGeometry.Point(9,1),new LayoutGeometry.Point(9,9)),5);
        Way connected=BlockLayoutMode.toRoadWay(connector,inverse,ds,commands,generatedNodes);commands.add(new AddCommand(ds,connected));
        if(service.lastNode()!=connected.firstNode())throw new AssertionError("Generated road junctions must reuse the same node");
        java.util.Set<Node> roadNodes=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());roadNodes.addAll(service.getNodes());roadNodes.addAll(connected.getNodes());
        if(roadNodes.size()!=3)throw new AssertionError("Connected service ways should use three distinct node objects total");
        if(generatedNodes.size()!=3)throw new AssertionError("Generated junction cache should contain three nodes");
        if(!"service".equals(service.get("highway"))||!"driveway".equals(service.get("service")))throw new AssertionError("Service road tags");
        int baseNodes=ds.getNodes().size(),baseWays=ds.getWays().size();
        UndoRedoHandler handler=UndoRedoHandler.getInstance();handler.clean();
        handler.add(new SequenceCommand("layout integration",commands));
        if(ds.getNodes().size()<=baseNodes || ds.getWays().size()<baseWays+4 || ds.getRelations().size()!=1)throw new AssertionError("SequenceCommand should add building, service way, shop rings and relation");
        if(handler.getUndoCommands().size()!=1)throw new AssertionError("Expected one undo entry");
        handler.undo();
        if(ds.getNodes().size()!=baseNodes || ds.getWays().size()!=baseWays || !ds.getRelations().isEmpty())throw new AssertionError("One undo must remove generated nodes, ways and relation");
        handler.clean();
        System.out.println("PASS LayoutUndoIntegrationTest: actual JOSM SequenceCommand adds and one undo fully removes generated geometry");
    }
}
