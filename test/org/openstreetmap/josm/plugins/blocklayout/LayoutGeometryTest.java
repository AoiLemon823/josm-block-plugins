package org.openstreetmap.josm.plugins.blocklayout;

import java.util.List;
import org.openstreetmap.josm.plugins.blocklayout.LayoutGeometry.*;

public final class LayoutGeometryTest {
    private static Parcel box(double x1,double y1,double x2,double y2){return new Parcel(List.of(new Point(x1,y1),new Point(x2,y1),new Point(x2,y2),new Point(x1,y2),new Point(x1,y1)));}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Settings settings(boolean sun,boolean stagger){return new Settings(10,30,18,5,10,20,sun,2,stagger);}
    public static void main(String[] args){
        Parcel parcel=box(0,0,120,100);
        Result base=LayoutGeometry.layout(List.of(parcel),List.of(),settings(false,false));
        check(base.buildings().size()==9,"rectangle: expected 3x3 buildings, got "+base.buildings().size());
        Rect first=base.buildings().get(0), second=base.buildings().get(1), nextRow=base.buildings().get(3);
        check(Math.abs(first.width()-30)<1e-9 && Math.abs(first.height()-10)<1e-9,"dimensions/orientation");
        check(Math.abs((second.minX()-first.maxX())-10)<1e-9,"side clearance");
        check(Math.abs((nextRow.minY()-first.maxY())-20)<1e-9,"minimum row spacing");
        check(Math.abs(base.effectiveRowSpacing()-20)<1e-9,"sun-off row spacing");
        Result sun=LayoutGeometry.layout(List.of(parcel),List.of(),settings(true,false));
        check(Math.abs(sun.effectiveRowSpacing()-36)<1e-9,"sun spacing formula");
        check(sun.buildings().size()==6,"sun spacing should reduce rows");
        Result stagger=LayoutGeometry.layout(List.of(parcel),List.of(),settings(false,true));
        check(stagger.buildings().size()==8,"staggered count and edge rejection");
        check(stagger.buildings().get(3).minX()-stagger.buildings().get(0).minX()==20,"alternate-row half-pitch offset");

        Parcel concave=new Parcel(List.of(new Point(0,0),new Point(100,0),new Point(100,30),new Point(30,30),new Point(30,100),new Point(0,100),new Point(0,0)));
        Result concaveResult=LayoutGeometry.layout(List.of(concave),List.of(),new Settings(10,20,12,0,0,10,false,0,false));
        check(!concaveResult.buildings().isEmpty(),"concave parcel should retain valid placements");
        for(Rect r:concaveResult.buildings()) check(r.maxY()<=30+1e-7 || r.maxX()<=30+1e-7,"rectangle crosses concave notch");

        Result tooSmall=LayoutGeometry.layout(List.of(box(0,0,5,5)),List.of(),settings(false,false));
        check(tooSmall.buildings().isEmpty() && tooSmall.message().contains("没有找到"),"too-small parcel friendly empty result");
        Result obstacle=LayoutGeometry.layout(List.of(box(0,0,50,30)),List.of(box(0,0,50,30)),new Settings(10,30,12,5,0,10,false,0,false));
        check(obstacle.buildings().isEmpty(),"existing building footprint blocks candidates");
        Result twoParcels=LayoutGeometry.layout(List.of(box(0,0,50,30),box(200,0,250,30)),List.of(),new Settings(10,30,12,0,0,10,false,0,false));
        check(twoParcels.buildings().size()==4,"separate parcels stay independently packed");
        for(Rect r:twoParcels.buildings()) check(r.maxX()<100 || r.minX()>100,"no building crosses inter-parcel gap");
        Result tooMany=LayoutGeometry.layout(List.of(box(0,0,1000,1000)),List.of(),new Settings(1,1,3,0,0,0,false,0,false));
        check(tooMany.buildings().isEmpty() && tooMany.message().contains("500"),"building count cap");
        Footprint chamfer=footprint(List.of(new Point(0,5),new Point(5,0),new Point(35,0),new Point(40,5),new Point(40,15),new Point(35,20),new Point(5,20),new Point(0,15),new Point(0,5)));
        FootprintResult tiled=LayoutGeometry.layoutFootprints(List.of(box(0,0,100,50)),List.of(),chamfer,5,new Settings(10,40,24,0,5,10,false,0,false));
        check(tiled.buildings().size()==4,"sample footprint count");
        for(Footprint f:tiled.buildings()){
            check(Math.abs(area(f)-750)<1e-6,"chamfered sample area preserved");
            for(Point p:f.ring())check(p.x()<=100+1e-7&&p.y()<=50+1e-7,"sample footprint remains in parcel bounds");
        }
        check(Math.abs(tiled.buildings().get(0).ring().get(1).x()-tiled.buildings().get(0).ring().get(0).x())==5,"sample orientation and chamfer retained");
        Footprint rotated=rotate(chamfer,Math.toRadians(30));
        FootprintResult rotatedResult=LayoutGeometry.layoutFootprints(List.of(box(-30,-30,150,150)),List.of(),rotated,5,new Settings(10,40,24,0,5,10,false,0,false));
        check(!rotatedResult.buildings().isEmpty(),"rotated sample placements exist");
        Point ra=rotated.ring().get(1),rb=rotated.ring().get(2),oa=rotatedResult.buildings().get(0).ring().get(1),ob=rotatedResult.buildings().get(0).ring().get(2);
        double sourceAngle=Math.atan2(rb.y()-ra.y(),rb.x()-ra.x()),outputAngle=Math.atan2(ob.y()-oa.y(),ob.x()-oa.x());
        check(Math.abs(Math.sin(sourceAngle-outputAngle))<1e-8,"sample long axis orientation retained");
        FootprintResult oriented=LayoutGeometry.layoutOriented(List.of(box(-20,-20,120,100)),List.of(),new Settings(10,30,18,0,10,20,false,0,false),Math.toRadians(30));
        check(!oriented.buildings().isEmpty(),"edge-oriented rectangular layout exists");
        Point oa1=oriented.buildings().get(0).ring().get(0),oa2=oriented.buildings().get(0).ring().get(1);
        check(Math.abs(Math.sin(Math.atan2(oa2.y()-oa1.y(),oa2.x()-oa1.x())-Math.toRadians(30)))<1e-8,"rectangle long axis parallel to selected parcel edge");
        FootprintResult concaveSamples=LayoutGeometry.layoutFootprints(List.of(concave),List.of(),footprint(List.of(new Point(0,0),new Point(20,0),new Point(20,10),new Point(0,10),new Point(0,0))),0,new Settings(10,20,12,0,0,10,false,0,false));
        check(!concaveSamples.buildings().isEmpty(),"sample layout works in concave parcel");
        Footprint obstacleSample=footprint(List.of(new Point(0,0),new Point(20,0),new Point(20,10),new Point(0,10),new Point(0,0)));
        FootprintResult blocked=LayoutGeometry.layoutFootprints(List.of(box(0,0,40,20)),List.of(obstacleSample),obstacleSample,0,new Settings(10,20,12,0,0,10,false,0,false));
        for(Footprint f:blocked.buildings())check(!overlaps(f,obstacleSample),"existing sample building is avoided");
        FootprintResult staggeredSamples=LayoutGeometry.layoutFootprints(List.of(box(0,0,100,50)),List.of(),footprint(List.of(new Point(0,0),new Point(20,0),new Point(20,10),new Point(0,10),new Point(0,0))),0,new Settings(10,20,12,0,0,10,false,0,true));
        check(staggeredSamples.buildings().size()==14,"staggered sample layout count");
        FactoryGeometry.Settings factorySettings=new FactoryGeometry.Settings(12,20,30,50,6,1,2,5,6,15,42,50,0);
        FactoryGeometry.Result factories=FactoryGeometry.layout(List.of(box(0,0,220,150)),List.of(),factorySettings,0);
        check(!factories.buildings().isEmpty(),"industrial parcel should receive variable-size factories");
        check(factories.buildings().stream().map(f->area(f)).distinct().count()>1,"factory footprints should vary in size");
        FactoryGeometry.Result factoriesAgain=FactoryGeometry.layout(List.of(box(0,0,220,150)),List.of(),factorySettings,0);
        check(factories.buildings().equals(factoriesAgain.buildings())&&factories.levels().equals(factoriesAgain.levels()),"factory seed should make footprints and levels deterministic");
        check(factories.levels().stream().allMatch(level->level>=1&&level<=2),"factory level range must honor settings");
        check(factories.levels().stream().map(level->6*level).distinct().allMatch(h->h==6||h==12),"factory total heights follow floor-height times randomized levels");
        check(factories.levels().stream().distinct().count()>1,"factory levels should vary with configured range");
        FactoryGeometry.Result factoryRotated=FactoryGeometry.layout(List.of(box(-50,-50,150,150)),List.of(),factorySettings,Math.toRadians(30));
        check(!factoryRotated.buildings().isEmpty(),"factory layout should follow selected direction");
        List<Footprint> industrialRows=List.of(footprint(List.of(new Point(15,15),new Point(65,15),new Point(65,30),new Point(15,30))),footprint(List.of(new Point(15,65),new Point(65,65),new Point(65,80),new Point(15,80))));
        FactoryRoadGeometry.Result aisle=FactoryRoadGeometry.layout(List.of(box(0,0,100,100)),industrialRows,6,3,0);
        check(aisle.roads().size()==1,"industrial row gap should produce a service aisle");
        check(Math.abs(aisle.roads().get(0).centerline().get(0).y()-47.5)<1e-7,"factory aisle lies midway between rows");
        FactoryRoadGeometry.Result tightAisle=FactoryRoadGeometry.layout(List.of(box(0,0,100,100)),industrialRows,20,5,0);
        check(tightAisle.roads().isEmpty()&&tightAisle.rejected()>0,"industrial aisle rejects insufficient clear width");
        FactoryGeometry.Settings groupedSettings=new FactoryGeometry.Settings(12,20,30,60,6,1,2,5,8,15,42,100,3);
        FactoryGeometry.Result grouped=FactoryGeometry.layout(List.of(box(0,0,600,160)),List.of(),groupedSettings,0);
        check(grouped.buildings().equals(FactoryGeometry.layout(List.of(box(0,0,600,160)),List.of(),groupedSettings,0).buildings()),"grouped factory seed must be deterministic");
        boolean identicalPair=false;
        for(int i=1;i<grouped.buildings().size();i++){
            List<Point> a=grouped.buildings().get(i-1).ring(),b=grouped.buildings().get(i).ring();
            if(Math.abs(a.get(0).y()-b.get(0).y())<1e-7&&Math.abs((a.get(1).x()-a.get(0).x())-(b.get(1).x()-b.get(0).x()))<1e-7){identicalPair=true;check(a.get(1).x()-a.get(0).x()>=48-1e-7,"grouped factories should favor longer footprints");check(Math.abs(b.get(0).x()-a.get(1).x()-8)<1e-7,"grouped factories retain configured gap");}
        }
        check(identicalPair,"grouped layout should contain adjacent identical factories");
        for(int i=0;i<grouped.buildings().size();i++)for(int j=i+1;j<grouped.buildings().size();j++)check(!overlaps(grouped.buildings().get(i),grouped.buildings().get(j)),"grouped factories must not overlap");
        FootprintResult capSamples=LayoutGeometry.layoutFootprints(List.of(box(0,0,1000,1000)),List.of(),footprint(List.of(new Point(0,0),new Point(1,0),new Point(1,1),new Point(0,1),new Point(0,0))),0,new Settings(1,1,3,0,0,0,false,0,false));
        check(capSamples.buildings().isEmpty()&&capSamples.message().contains("500"),"sample count cap");
        for(int edge=0;edge<4;edge++){List<Point> pts=List.of(new Point(0,0),new Point(100,0),new Point(100,120),new Point(0,120));Point a=pts.get(edge),b=pts.get((edge+1)%4);FootprintResult er=LayoutGeometry.layoutShops(List.of(new Parcel(List.of(a,b,pts.get((edge+2)%4),pts.get((edge+3)%4),a))),List.of(),6,8,4,30);if(er.buildings().isEmpty())throw new AssertionError("edge "+edge+" rejected: "+er.message());}
        ShopResult shops30=LayoutGeometry.layoutShopPolygons(List.of(box(0,0,100,120)),List.of(),List.of(),6,8,4,30);
        check(shops30.polygons().size()==4,"four shop runs around rectangular parcel");
        for(int i=0;i<shops30.polygons().size();i++){Footprint f=footprint(shops30.polygons().get(i).outer());for(int j=i+1;j<shops30.polygons().size();j++)check(!overlaps(f,footprint(shops30.polygons().get(j).outer())),"shop runs must not overlap at corners");}
        Footprint shop=footprint(shops30.polygons().get(0).outer());check(area(shop)>0,"30-degree chamfer has positive area");
        double minX=shop.ring().stream().mapToDouble(Point::x).min().orElseThrow(),maxX=shop.ring().stream().mapToDouble(Point::x).max().orElseThrow();
        double minY=shop.ring().stream().mapToDouble(Point::y).min().orElseThrow(),maxY=shop.ring().stream().mapToDouble(Point::y).max().orElseThrow();
        check(Math.abs(minY-6)<1e-7,"shop facade must respect street setback");check(Math.abs(maxY-minY-8)<1e-7,"shop footprint depth must remain complete");
        check(shop.ring().size()==8,"shop should remain a full chamfered polygon, not a sharp triangular strip");
        Parcel overlapParcel=new Parcel(List.of(new Point(0,0),new Point(100,0),new Point(100,40),new Point(100,80),new Point(0,80),new Point(0,40)));
        ShopResult overlappingBands=LayoutGeometry.layoutShopPolygons(List.of(overlapParcel),List.of(),List.of(),0,60,4,30);
        for(int i=0;i<overlappingBands.polygons().size();i++)for(int j=i+1;j<overlappingBands.polygons().size();j++){java.awt.geom.Area a=filledArea(overlappingBands.polygons().get(i)),b=filledArea(overlappingBands.polygons().get(j));a.intersect(b);check(a.isEmpty(),"overlapping storefront bands must not duplicate filled area");}
        ShopResult shops45=LayoutGeometry.layoutShopPolygons(List.of(box(0,0,100,120)),List.of(),List.of(),6,8,5,45);
        check(shops45.polygons().size()==4,"45-degree chamfer option");
        ShopResult concaveShops=LayoutGeometry.layoutShopPolygons(List.of(concave),List.of(),List.of(),3,6,4,45);
        for(ShopPolygon p:concaveShops.polygons())check(insideConcave(footprint(p.outer()),concave),"shop segment remains inside concave parcel");
        Footprint blocker=footprint(List.of(new Point(30,6),new Point(70,6),new Point(70,14),new Point(30,14),new Point(30,6)));
        ShopResult blockedShops=LayoutGeometry.layoutShopPolygons(List.of(box(0,0,100,120)),List.of(blocker),List.of(),6,8,4,30);
        check(blockedShops.polygons().size()>=4&&blockedShops.polygons().stream().mapToDouble(p->area(footprint(p.outer()))).sum()<shops30.polygons().stream().mapToDouble(p->area(footprint(p.outer()))).sum(),"existing building footprint is subtracted from storefront geometry");
        expectInvalid(new Settings(Double.NaN,1,1,0,0,0,false,0,false));
        expectInvalid(new Settings(1,1,1,-1,0,0,false,0,false));
        expectInvalid(new Settings(1,1,1,0,0,0,true,Double.POSITIVE_INFINITY,false));
        expectInvalid(new Settings(1,1,1,0,0,0,true,101,false));
        System.out.println("PASS LayoutGeometryTest: dimensions, clearances, sun spacing, stagger, concavity, parcel isolation, obstacles, size cap, invalid values");
    }
    private static java.awt.geom.Area filledArea(ShopPolygon polygon){java.awt.geom.Area area=polygonArea(footprint(polygon.outer()));for(List<Point> hole:polygon.holes())area.subtract(polygonArea(footprint(hole)));return area;}
    private static Footprint footprint(List<Point> points){return new Footprint(points);}
    private static Footprint rotate(Footprint f,double angle){double c=Math.cos(angle),s=Math.sin(angle);return new Footprint(f.ring().stream().map(p->new Point(p.x()*c-p.y()*s,p.x()*s+p.y()*c)).toList());}
    private static boolean insideConcave(Footprint f,Parcel p){for(Point q:f.ring())if(!(q.x()<=30+1e-7||q.y()<=30+1e-7))return false;return true;}
    private static boolean overlaps(Footprint a,Footprint b){java.awt.geom.Area area=polygonArea(a);area.intersect(polygonArea(b));return !area.isEmpty();}
    private static java.awt.geom.Area polygonArea(Footprint f){java.awt.geom.Path2D.Double p=new java.awt.geom.Path2D.Double();for(int i=0;i<f.ring().size();i++){Point q=f.ring().get(i);if(i==0)p.moveTo(q.x(),q.y());else p.lineTo(q.x(),q.y());}p.closePath();return new java.awt.geom.Area(p);}
    private static double area(Footprint f){double a=0;List<Point> p=f.ring();for(int i=0;i<p.size();i++){Point x=p.get(i),y=p.get((i+1)%p.size());a+=x.x()*y.y()-y.x()*x.y();}return Math.abs(a)/2;}
    private static void expectInvalid(Settings s){try{LayoutGeometry.layout(List.of(box(0,0,20,20)),List.of(),s);throw new AssertionError("Expected invalid settings rejection");}catch(IllegalArgumentException expected){}}
}
