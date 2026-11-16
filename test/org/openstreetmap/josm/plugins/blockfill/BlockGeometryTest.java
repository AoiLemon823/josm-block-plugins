package org.openstreetmap.josm.plugins.blockfill;

import java.util.*;
import org.openstreetmap.josm.plugins.blockfill.BlockGeometry.*;

public final class BlockGeometryTest {
    private static long next=1;
    private static Vertex v(double x,double y){return new Vertex(next++,x,y);}
    private static List<Segment> ring(double x1,double y1,double x2,double y2){Vertex a=v(x1,y1),b=v(x2,y1),c=v(x2,y2),d=v(x1,y2);return List.of(new Segment(a,b),new Segment(b,c),new Segment(c,d),new Segment(d,a));}
    private static BlockGeometry create(List<Segment> s){return new BlockGeometry(s);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        List<Segment> square=ring(0,0,100,100);BlockGeometry geometry=create(square);
        check(geometry.faces().size()==1,"square face count");check(geometry.at(50,50,0).valid(),"square click");
        check(geometry.at(50,50,5).valid(),"inset preview");check(!geometry.at(1,1,5).valid(),"inset band must reject click");
        check(!geometry.at(50,50,60).valid(),"collapsed inset must reject");
        List<Segment> withTail=new ArrayList<>(square);withTail.add(new Segment(square.get(0).a(),v(-10,0)));
        BlockGeometry tail=create(withTail);check(tail.ignoredBridgeCount==1,"dead-end roads are removed as graph bridges");check(tail.at(50,50,0).valid(),"dead-end must not block block");
        Vertex a=v(0,0),b=v(100,0),c=v(100,100),d=v(0,100),center=v(50,50);
        List<Segment> crossed=List.of(new Segment(a,b),new Segment(b,c),new Segment(c,d),new Segment(d,a),new Segment(a,center));
        check(create(crossed).faces().size()==1,"interior dangling spur should not split the closed block");
        // A disconnected inner loop isn't part of the road graph and cannot be inferred as a hole.
        List<Segment> hole=new ArrayList<>(ring(-20,-20,120,120));hole.addAll(ring(20,20,80,80));
        check(!create(hole).at(5,5,0).valid(),"inner road loop is not a valid clickable road face");
        check(!create(hole).at(50,50,0).valid(),"nested disjoint road loops are flagged as unsupported rings");
        // Adjacent blocks with a shared edge must produce distinct faces.
        Vertex p=v(0,0),q=v(50,0),r=v(100,0),s=v(100,100),t=v(50,100),u=v(0,100);
        List<Segment> blocks=List.of(new Segment(p,q),new Segment(q,r),new Segment(r,s),new Segment(s,t),new Segment(t,u),new Segment(u,p),new Segment(q,t));
        BlockGeometry adjacent=create(blocks);check(adjacent.faces().size()==2,"adjacent blocks share and reuse a boundary");
        check(adjacent.at(20,50,0).valid()&&adjacent.at(80,50,0).valid(),"both adjacent blocks selectable");
        check(!adjacent.at(50,50,0).valid(),"shared boundary is not an interior");
        // Chamfer: only convex corners sitting on a highway intersection are cut.
        check(adjacent.isIntersection(q.id())&&!adjacent.isIntersection(p.id()),"junction degree decides which corners are cut");
        Candidate plain=adjacent.at(20,50,0,0),cut=adjacent.at(20,50,0,6);
        check(plain.valid()&&cut.valid(),"chamfered block remains clickable");
        check(cut.points().size()==plain.points().size()+2,"the two junction corners become four cut points");
        check(hasPoint(cut,44,0)&&hasPoint(cut,50,6),"cut points walk six metres along both road edges");
        check(!hasPoint(cut,50,0),"the original intersection corner is replaced");
        check(Math.abs(cut.area()-(plain.area()-36))<1e-6,"chamfer removes both corner triangles");
        check(!adjacent.at(49,0.5,0,6).valid(),"click in the cut-off corner band is rejected");
        check(adjacent.at(20,50,0,90).valid(),"oversized chamfer is clamped to the adjacent road length");
        check(adjacent.at(80,50,0,6).valid()&&adjacent.at(80,50,0,6).points().size()==6,"both blocks at the junction are cut");
        check(adjacent.at(20,50,5,6).valid(),"inset and chamfer combine");
        check(geometry.at(50,50,0,10).points().size()==geometry.at(50,50,0).points().size(),"a plain bend without an intersection is unchanged");
        check(tail.at(50,50,0,10).points().size()==tail.at(50,50,0).points().size(),"a pruned dead-end spur is not a highway intersection");
        System.out.println("PASS: closed face, dead-end pruning, inset guards, inner-ring rejection, adjacent faces, shared-edge click, junction chamfer");
    }
    private static boolean hasPoint(Candidate candidate,double x,double y){for(Point p:candidate.points())if(Math.abs(p.x()-x)<1e-6&&Math.abs(p.y()-y)<1e-6)return true;return false;}
}
