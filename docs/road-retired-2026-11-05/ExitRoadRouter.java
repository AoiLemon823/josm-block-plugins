package org.openstreetmap.josm.plugins.blocklayout;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Footprint;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Parcel;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Point;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Road;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.RoadResult;

/** Bounded orthogonal grid routing between explicitly selected access points. */
public final class ExitRoadRouter {
    private static final int MAX_CELLS=250_000;
    private ExitRoadRouter() { }
    private static Area polygon(List<Point> points) {
        Path2D.Double p=new Path2D.Double();p.moveTo(points.get(0).x(),points.get(0).y());
        for(int i=1;i<points.size();i++)p.lineTo(points.get(i).x(),points.get(i).y());
        p.closePath();return new Area(p);
    }
    private static Area stroke(Point a,Point b,double width) {
        Path2D.Double p=new Path2D.Double();p.moveTo(a.x(),a.y());p.lineTo(b.x(),b.y());
        return new Area(new BasicStroke((float)width,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND).createStrokedShape(p));
    }
    private static boolean valid(Point a,Point b,Area allowed,Area obstacles,double width,double clearance,boolean terminal) {
        Area corridor=stroke(a,b,terminal?0.01:width);Area outside=(Area)corridor.clone();Area permitted=(Area)allowed.clone();if(terminal)permitted.add(new Area(new BasicStroke(0.04f).createStrokedShape(allowed)));outside.subtract(permitted);
        if(!outside.isEmpty())return false;
        Area buffer=stroke(a,b,width+2*clearance);double radius=(width+2*clearance)/2;
        buffer.add(new Area(new java.awt.geom.Ellipse2D.Double(a.x()-radius,a.y()-radius,2*radius,2*radius)));
        buffer.add(new Area(new java.awt.geom.Ellipse2D.Double(b.x()-radius,b.y()-radius,2*radius,2*radius)));
        buffer.intersect(obstacles);return buffer.isEmpty();
    }
    public static RoadResult route(Parcel parcel,List<Footprint> buildings,List<Point> exits,double width,double clearance) {
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(clearance)||clearance<0)throw new IllegalArgumentException("道路宽度或建筑退距无效。");
        if(exits.size()<2||exits.size()>8)throw new IllegalArgumentException("请选择 2 至 8 个出口节点。");
        Area allowed=polygon(parcel.ring()),obstacles=new Area();for(Footprint f:buildings)obstacles.add(polygon(f.ring()));
        for(Point exit:exits){double nearest=Double.POSITIVE_INFINITY;List<Point> ring=parcel.ring();for(int i=0;i<ring.size();i++){Point a=ring.get(i),z=ring.get((i+1)%ring.size());nearest=Math.min(nearest,java.awt.geom.Line2D.ptSegDist(a.x(),a.y(),z.x(),z.y(),exit.x(),exit.y()));}if(nearest>0.05)throw new IllegalArgumentException("出口节点必须位于地块边界上，请先将节点加入地块边界。");}
        Rectangle2D b=allowed.getBounds2D();double step=Math.max(1,Math.min(3,width/2));
        int nx=(int)Math.ceil(b.getWidth()/step)+1,ny=(int)Math.ceil(b.getHeight()/step)+1;
        if(nx<=0||ny<=0||(long)nx*ny>MAX_CELLS)throw new IllegalArgumentException("地块过大，寻路网格超过上限，请缩小地块范围。");
        List<Road> roads=new ArrayList<>();int failed=0;
        for(int pair=0;pair+1<exits.size();pair++){
            Point start=exits.get(pair),end=exits.get(pair+1);int n=nx*ny;int[] previous=new int[n];Arrays.fill(previous,-2);ArrayDeque<Integer> queue=new ArrayDeque<>();
            for(int id=0;id<n;id++){Point p=grid(id,nx,b,step);if(allowed.contains(p.x(),p.y())&&Math.hypot(p.x()-start.x(),p.y()-start.y())<=step*2&&valid(start,p,allowed,obstacles,width,clearance,true)){previous[id]=-1;queue.add(id);}}
            int found=-1;int[] dx={1,-1,0,0},dy={0,0,1,-1};
            while(!queue.isEmpty()) {int id=queue.removeFirst();Point p=grid(id,nx,b,step);if(Math.hypot(p.x()-end.x(),p.y()-end.y())<=step*2&&valid(p,end,allowed,obstacles,width,clearance,true)){found=id;break;}
                int x=id%nx,y=id/nx;for(int d=0;d<4;d++){int xx=x+dx[d],yy=y+dy[d];if(xx<0||xx>=nx||yy<0||yy>=ny)continue;int next=yy*nx+xx;if(previous[next]!=-2)continue;Point q=grid(next,nx,b,step);if(!valid(p,q,allowed,obstacles,width,clearance,false))continue;previous[next]=id;queue.addLast(next);}
            }
            if(found<0){failed++;continue;}List<Point> line=new ArrayList<>();for(int id=found;id>=0;id=previous[id])line.add(grid(id,nx,b,step));Collections.reverse(line);line.add(0,start);line.add(end);
            List<Point> simple=new ArrayList<>();for(Point p:line){if(!simple.isEmpty()&&Math.hypot(p.x()-simple.get(simple.size()-1).x(),p.y()-simple.get(simple.size()-1).y())<1e-8)continue;while(simple.size()>=2){Point a=simple.get(simple.size()-2),c=simple.get(simple.size()-1);double cross=(c.x()-a.x())*(p.y()-c.y())-(c.y()-a.y())*(p.x()-c.x());if(Math.abs(cross)>1e-8)break;simple.remove(simple.size()-1);}simple.add(p);}roads.add(new Road(simple,width));
        }
        return new RoadResult(roads,failed,"出口寻路：连接 "+roads.size()+" 对，失败 "+failed+" 对；网格间距 "+step+" 米。请检查道路形态及外路拓扑。");
    }
    private static Point grid(int id,int nx,Rectangle2D b,double step){return new Point(b.getMinX()+(id%nx)*step,b.getMinY()+(id/nx)*step);}
}
