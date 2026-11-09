package org.openstreetmap.josm.plugins.blocklayout;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Footprint;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Parcel;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Point;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Road;

/** Linear-time row-aisle generator for axis-aligned factory footprints. */
public final class FactoryRoadGeometry {
    public record Result(List<Road> roads,int rejected,String message){public Result{roads=List.copyOf(roads);}}
    private record Box(Rectangle2D bounds,double centerY){}
    private FactoryRoadGeometry(){}

    public static Result layout(List<Parcel> parcels,List<Footprint> buildings,double width,double clearance,double angle){
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(clearance)||clearance<0||!Double.isFinite(angle))throw new IllegalArgumentException("工业道路参数无效。");
        if(parcels==null||parcels.isEmpty()||buildings==null||buildings.size()<2)return new Result(List.of(),0,"工业道路至少需要一个地块和两栋厂房。");
        Point origin=parcels.get(0).ring().get(0);double c=Math.cos(angle),s=Math.sin(angle);
        java.util.function.Function<Point,Point> local=p->{double x=p.x()-origin.x(),y=p.y()-origin.y();return new Point(x*c+y*s,-x*s+y*c);};
        java.util.function.Function<Point,Point> world=p->new Point(origin.x()+p.x()*c-p.y()*s,origin.y()+p.x()*s+p.y()*c);
        List<Road> roads=new ArrayList<>();int rejected=0,candidates=0;
        for(Parcel parcel:parcels){
            Area allowed=polygon(parcel.ring().stream().map(local).toList());Rectangle2D bounds=allowed.getBounds2D();
            List<Area> obstacles=new ArrayList<>();List<Box> boxes=new ArrayList<>();
            for(Footprint f:buildings){Area shape=polygon(f.ring().stream().map(local).toList());if(!intersects(shape,allowed))continue;obstacles.add(shape);Rectangle2D b=shape.getBounds2D();boxes.add(new Box(b,b.getCenterY()));}
            if(boxes.size()<2)continue;
            boxes.sort(Comparator.comparingDouble(Box::centerY));
            double medianHeight=boxes.stream().mapToDouble(b->b.bounds().getHeight()).sorted().skip((boxes.size()-1)/2).findFirst().orElse(1);
            double tolerance=Math.max(0.75,Math.min(4,medianHeight*0.3));List<List<Box>> rows=new ArrayList<>();
            for(Box box:boxes){if(rows.isEmpty()||box.centerY()-rows.get(rows.size()-1).stream().mapToDouble(Box::centerY).average().orElse(box.centerY())>tolerance)rows.add(new ArrayList<>());rows.get(rows.size()-1).add(box);}
            for(int ri=0;ri+1<rows.size();ri++){
                List<Box> upper=rows.get(ri),lower=rows.get(ri+1);
                double upperEdge=upper.stream().mapToDouble(b->b.bounds().getMaxY()).max().orElse(0),lowerEdge=lower.stream().mapToDouble(b->b.bounds().getMinY()).min().orElse(0);
                if(lowerEdge-upperEdge+1e-7<width+2*clearance){rejected++;continue;}
                double xMin=Math.max(upper.stream().mapToDouble(b->b.bounds().getMinX()).min().orElse(bounds.getMinX()),lower.stream().mapToDouble(b->b.bounds().getMinX()).min().orElse(bounds.getMinX()));
                double xMax=Math.min(upper.stream().mapToDouble(b->b.bounds().getMaxX()).max().orElse(bounds.getMaxX()),lower.stream().mapToDouble(b->b.bounds().getMaxX()).max().orElse(bounds.getMaxX()));
                double endInset=clearance+width/2;xMin=Math.max(bounds.getMinX()+endInset,xMin+endInset);xMax=Math.min(bounds.getMaxX()-endInset,xMax-endInset);
                if(xMax-xMin<Math.max(2*width,6)){rejected++;continue;}
                candidates++;List<Point> line=List.of(new Point(xMin,(upperEdge+lowerEdge)/2),new Point(xMax,(upperEdge+lowerEdge)/2));
                Area corridor=stroke(line,width);Area outside=(Area)corridor.clone();outside.subtract(allowed);if(!outside.isEmpty()){rejected++;continue;}
                Area buffer=stroke(line,width+2*clearance);for(Point endpoint:line){double r=width/2+clearance;buffer.add(new Area(new java.awt.geom.Ellipse2D.Double(endpoint.x()-r,endpoint.y()-r,2*r,2*r)));}
                boolean conflict=false;for(Area obstacle:obstacles)if(intersects(buffer,obstacle)){conflict=true;break;}
                if(conflict){rejected++;continue;}roads.add(new Road(line.stream().map(world).toList(),width));
            }
        }
        String message=roads.isEmpty()?"没有找到满足宽度、厂房净距和地块边界的工业通道。":"生成 "+roads.size()+" 条工业 service 通道；候选 "+candidates+"，拒绝 "+rejected+"。";
        return new Result(roads,rejected,message);
    }
    private static Area polygon(List<Point> p){Path2D.Double path=new Path2D.Double();path.moveTo(p.get(0).x(),p.get(0).y());for(int i=1;i<p.size();i++)path.lineTo(p.get(i).x(),p.get(i).y());path.closePath();return new Area(path);}
    private static Area stroke(List<Point> points,double width){Path2D.Double path=new Path2D.Double();path.moveTo(points.get(0).x(),points.get(0).y());path.lineTo(points.get(1).x(),points.get(1).y());return new Area(new BasicStroke((float)width,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND).createStrokedShape(path));}
    private static boolean intersects(Area a,Area b){Area overlap=(Area)a.clone();overlap.intersect(b);return !overlap.isEmpty();}
}
