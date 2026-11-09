package org.openstreetmap.josm.plugins.blockfill;
import org.openstreetmap.josm.plugins.blockfill.BlockGeometry.*;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.data.Preferences;
import java.util.*;
public final class BlockFillModeTest {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
    private static Way road(String highway,String... additional){Node a=new Node(new LatLon(0,0)),b=new Node(new LatLon(0,0.001));Way w=new Way();w.setNodes(List.of(a,b));Map<String,String> tags=new HashMap<>();tags.put("highway",highway);for(int i=0;i<additional.length;i+=2)tags.put(additional[i],additional[i+1]);w.setKeys(tags);return w;}
    public static void main(String[] args){
        Config.setPreferencesInstance(new Preferences());
        check(BlockFillMode.eligible(road("residential"),false),"residential road accepted");
        check(!BlockFillMode.eligible(road("service"),false),"service road excluded by default");check(BlockFillMode.eligible(road("service"),true),"service road available as opt-in");
        check(!BlockFillMode.eligible(road("footway"),true),"footway excluded");check(!BlockFillMode.eligible(road("primary","bridge","yes"),false),"bridge excluded");
        check(!BlockFillMode.eligible(road("primary","tunnel","building_passage"),false),"tunnel excluded");check(!BlockFillMode.eligible(road("primary","layer","1"),false),"nonzero layer excluded");
        check(BlockFillMode.eligible(road("primary","layer","0"),false),"ground-level layer accepted");check(!BlockFillMode.eligible(road("residential","area","yes"),false),"area-tagged line excluded");
        check(BlockFillMode.withinLocalProjection(new LatLon(39.95,116.4),new LatLon(39.9,116.3)),"nearby viewport roads remain inside local projection");
        check(!BlockFillMode.withinLocalProjection(new LatLon(39.95,120),new LatLon(39.9,116.3)),"remote road segments cannot trip a city-scale projection failure");
        check(BlockFillMode.inBounds(new LatLon(39.9,116.4),39.8,116.2,40.0,116.6),"viewport extent coordinate order");
        DataSet data=new DataSet();Node a=new Node(new LatLon(25,110)),b=new Node(new LatLon(25,110.001)),c=new Node(new LatLon(25.001,110.001)),d=new Node(new LatLon(25.001,110));data.addPrimitive(a);data.addPrimitive(b);data.addPrimitive(c);data.addPrimitive(d);
        Way w1=new Way();w1.setNodes(List.of(a,b));w1.put("highway","residential");data.addPrimitive(w1);Way w2=new Way();w2.setNodes(List.of(b,c));w2.put("highway","residential");data.addPrimitive(w2);Way w3=new Way();w3.setNodes(List.of(c,d));w3.put("highway","residential");data.addPrimitive(w3);Way w4=new Way();w4.setNodes(List.of(d,a));w4.put("highway","residential");data.addPrimitive(w4);
        List<Segment> segments=new ArrayList<>();for(Way w:data.getWays())for(int i=1;i<w.getNodesCount();i++){Node x=w.getNode(i-1),y=w.getNode(i);segments.add(new Segment(new Vertex(x.getUniqueId(),x.lat(),x.lon()),new Vertex(y.getUniqueId(),y.lat(),y.lon())));}
        check(new BlockGeometry(segments).faces().isEmpty(),"disconnected highway endpoint IDs never form a shared-node block");
        System.out.println("PASS: highway filtering, bridge/tunnel/layer exclusions, area exclusion, and shared-node topology");
    }
}
