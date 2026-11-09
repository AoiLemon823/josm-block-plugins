package org.openstreetmap.josm.plugins.blocklayout;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Footprint;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Parcel;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.Point;

/** Deterministic variable-size factory footprints in a selected industrial parcel. */
public final class FactoryGeometry {
    public record Settings(double minWidth,double maxWidth,double minLength,double maxLength,
            double floorHeight,int minLevels,int maxLevels,double parcelSetback,double buildingGap,double rowGap,long seed,int maxBuildings,int maxGroupSize) {
        public Settings(double minWidth,double maxWidth,double minLength,double maxLength,double ignoredHeight,double parcelSetback,double buildingGap,double rowGap,long seed,int maxBuildings){this(minWidth,maxWidth,minLength,maxLength,6,1,2,parcelSetback,buildingGap,rowGap,seed,maxBuildings,0);}
        public void validate(){
            positive(minWidth,"厂房最小短边");positive(maxWidth,"厂房最大短边");positive(minLength,"厂房最小长边");positive(maxLength,"厂房最大长边");
            if(maxWidth<minWidth||maxLength<minLength)throw new IllegalArgumentException("厂房尺寸范围无效。");
            positive(floorHeight,"厂房标准层高");if(minLevels<1||maxLevels<minLevels||maxLevels>12)throw new IllegalArgumentException("厂房层数范围无效。");nonNegative(parcelSetback,"厂房地块退界");nonNegative(buildingGap,"厂房同排间隔");nonNegative(rowGap,"厂房排间参考间隔");
            if(maxBuildings<1||maxBuildings>500)throw new IllegalArgumentException("厂房数量上限应为1至500。");
            if(maxGroupSize<0||maxGroupSize>10)throw new IllegalArgumentException("联排最大栋数应为0至10。");
        }
        private static void positive(double v,String n){if(!Double.isFinite(v)||v<=0)throw new IllegalArgumentException(n+"必须是有限正数。");}
        private static void nonNegative(double v,String n){if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException(n+"必须是有限非负数。");}
    }
    public record Result(List<Footprint> buildings,List<Integer> levels,int rejected,String message){public Result{buildings=List.copyOf(buildings);levels=List.copyOf(levels);}}
    public record HeightSettings(double floorHeight,int minLevels,int maxLevels){public double height(int levels){return floorHeight*levels;}}
    private FactoryGeometry(){}
    public static Result layout(List<Parcel> parcels,List<Footprint> existing,Settings s,double angle){
        s.validate();if(parcels==null||parcels.isEmpty())throw new IllegalArgumentException("请先选择工业用地地块。");if(!Double.isFinite(angle))throw new IllegalArgumentException("厂房排列方向无效。");
        Point origin=parcels.get(0).ring().get(0);double c=Math.cos(angle),sn=Math.sin(angle);
        java.util.function.Function<Point,Point> local=p->{double x=p.x()-origin.x(),y=p.y()-origin.y();return new Point(x*c+y*sn,-x*sn+y*c);};
        java.util.function.Function<Point,Point> world=p->new Point(origin.x()+p.x()*c-p.y()*sn,origin.y()+p.x()*sn+p.y()*c);
        List<Area> obstacles=new ArrayList<>();if(existing!=null)for(Footprint f:existing)obstacles.add(area(f.ring().stream().map(local).toList()));
        List<Footprint> output=new ArrayList<>();List<Integer> levels=new ArrayList<>();Random random=new Random(s.seed());int rejected=0;
        for(Parcel parcel:parcels){
            Area allowed=area(parcel.ring().stream().map(local).toList());Rectangle2D b=allowed.getBounds2D();double y=b.getMinY()+s.parcelSetback();
            while(y+s.minWidth()<=b.getMaxY()-s.parcelSetback()&&output.size()<s.maxBuildings()){
                double depth=between(random,s.minWidth(),s.maxWidth());double x=b.getMinX()+s.parcelSetback();boolean placedRow=false;
                while(x+s.minLength()<=b.getMaxX()-s.parcelSetback()&&output.size()<s.maxBuildings()){
                    int group=s.maxGroupSize()>=2?1+random.nextInt(s.maxGroupSize()):1;
                    double length=group>1?between(random,Math.max(s.minLength(),s.maxLength()*0.8),s.maxLength()):between(random,s.minLength(),s.maxLength());
                    int placed=0;
                    for(int member=0;member<group&&output.size()<s.maxBuildings();member++){
                        List<Point> ring=List.of(new Point(x,y),new Point(x+length,y),new Point(x+length,y+depth),new Point(x,y+depth));Area candidate=area(ring);
                        boolean ok=contains(allowed,candidate);for(Area obstacle:obstacles)if(intersects(candidate,obstacle)){ok=false;break;}for(Footprint prior:output)if(intersects(candidate,area(prior.ring().stream().map(local).toList()))){ok=false;break;}
                        if(!ok){rejected++;break;}
                        output.add(new Footprint(ring.stream().map(world).toList()));levels.add(s.minLevels()+random.nextInt(s.maxLevels()-s.minLevels()+1));placedRow=true;placed++;x+=length+s.buildingGap();
                    }
                    if(placed==0)x+=Math.max(s.buildingGap(),2);
                }
                y+=depth+s.rowGap();if(!placedRow)y+=Math.max(s.buildingGap(),2);
            }
        }
        String msg=output.isEmpty()?"没有找到可放入工业地块且满足退界、间隔的厂房。":"可生成 "+output.size()+" 栋可变尺度厂房；跳过 "+rejected+" 个候选位置。";
        return new Result(output,levels,rejected,msg);
    }
    private static double between(Random r,double a,double b){return a+(b-a)*r.nextDouble();}
    private static Area area(List<Point> p){java.awt.geom.Path2D.Double path=new java.awt.geom.Path2D.Double();path.moveTo(p.get(0).x(),p.get(0).y());for(int i=1;i<p.size();i++)path.lineTo(p.get(i).x(),p.get(i).y());path.closePath();return new Area(path);}
    private static boolean contains(Area outer,Area inner){Area a=(Area)inner.clone();a.subtract(outer);return a.isEmpty();}
    private static boolean intersects(Area a,Area b){Area x=(Area)a.clone();x.intersect(b);return !x.isEmpty();}
}
