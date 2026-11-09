package org.openstreetmap.josm.plugins.blocklayout;

import java.awt.BasicStroke;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Pure local-metre layout geometry; independent from JOSM and the BlockFill plugin. */
public final class LayoutGeometry {
    public static final int MAX_BUILDINGS = 500;
    private static final long MAX_CANDIDATES = 1_000_000;
    private static final double EPS = 1e-7;

    private LayoutGeometry() { }

    public record Point(double x, double y) { }
    public record Rect(double minX, double minY, double maxX, double maxY) {
        public double width() { return maxX - minX; }
        public double height() { return maxY - minY; }
        public double area() { return width() * height(); }
        public List<Point> corners() { return List.of(new Point(minX,minY),new Point(maxX,minY),new Point(maxX,maxY),new Point(minX,maxY)); }
    }
    public record Parcel(List<Point> ring) {
        public Parcel { ring = List.copyOf(ring); }
    }
    public record Settings(double shortSide, double longSide, double height,
            double endSetback, double sideClearance, double minimumRowSpacing,
            boolean sunSpacing, double sunFactor, boolean staggered) { }
    public record Result(List<Rect> buildings, int rejected, double effectiveRowSpacing, String message) {
        public Result { buildings = List.copyOf(buildings); }
    }
    public record Footprint(List<Point> ring) {
        public Footprint { ring=List.copyOf(ring); }
    }
    public record FootprintResult(List<Footprint> buildings,int rejected,double effectiveRowSpacing,String message) {
        public FootprintResult { buildings=List.copyOf(buildings); }
    }
    public record Road(List<Point> centerline,double width){public Road{centerline=List.copyOf(centerline);}}
    public record RoadResult(List<Road> roads,int rejected,String message){public RoadResult{roads=List.copyOf(roads);}}
    public record ShopPolygon(List<Point> outer,List<List<Point>> holes){public ShopPolygon{outer=List.copyOf(outer);holes=holes.stream().map(List::copyOf).toList();}}
    public record ShopResult(List<ShopPolygon> polygons,int rejected,String message){public ShopResult{polygons=List.copyOf(polygons);}}

    public static double effectiveRowSpacing(Settings s) {
        validate(s);
        return Math.max(s.minimumRowSpacing(), s.sunSpacing() ? s.height() * s.sunFactor() : 0.0);
    }

    public static Result layout(List<Parcel> parcels, List<Parcel> existingBuildings, Settings s) {
        validate(s);
        if (parcels == null || parcels.isEmpty()) throw new IllegalArgumentException("请先选择至少一个闭合地块。");
        double rowGap = effectiveRowSpacing(s);
        List<Area> allowed = new ArrayList<>();
        for (Parcel parcel : parcels) allowed.add(polygonArea(parcel));
        List<Area> obstacles = new ArrayList<>();
        if (existingBuildings != null) for (Parcel p : existingBuildings) obstacles.add(polygonArea(p));
        List<Rect> placed = new ArrayList<>();
        int rejected = 0;
        long scanned=0;
        for (Area parcel : allowed) {
            Rectangle2D bounds = parcel.getBounds2D();
            double rowPitch = s.shortSide() + rowGap;
            double columnPitch = s.longSide() + s.sideClearance();
            long rowCount = safeCount(bounds.getHeight(), s.endSetback(), s.shortSide(), rowPitch);
            long colCount = safeCount(bounds.getWidth(), s.endSetback(), s.longSide(), columnPitch);
            for (long row=0; row<rowCount; row++) {
                double y = bounds.getMinY() + s.endSetback() + row * rowPitch;
                double stagger = s.staggered() && (row % 2 == 1) ? columnPitch / 2.0 : 0.0;
                for (long col=0; col<colCount; col++) {
                    if(++scanned>MAX_CANDIDATES) return new Result(List.of(),rejected,rowGap,"候选网格超过 100 万个位置；请缩小地块或增大间距后重试。");
                    double x = bounds.getMinX() + s.endSetback() + col * columnPitch + stagger;
                    Rect r = new Rect(x,y,x+s.longSide(),y+s.shortSide());
                    if (!containsRectangle(parcel,r)) { rejected++; continue; }
                    Area candidate = rectArea(r);
                    boolean overlap=false;
                    for (Area obstacle:obstacles) if (intersects(candidate, obstacle)) { overlap=true; break; }
                    if (!overlap) for (Rect prior:placed) {
                        if (r.minX() < prior.maxX()-EPS && r.maxX() > prior.minX()+EPS
                                && r.minY() < prior.maxY()-EPS && r.maxY() > prior.minY()+EPS) { overlap=true; break; }
                    }
                    if (overlap) { rejected++; continue; }
                    placed.add(r);
                    if (placed.size() > MAX_BUILDINGS) return new Result(List.of(),rejected,rowGap,"布局超过 500 栋上限；请缩小地块或增大间距。");
                }
            }
        }
        String message = placed.isEmpty() ? "没有找到可完整放入地块且避开既有建筑的位置。" : "可放置 " + placed.size() + " 栋；拒绝 " + rejected + " 个越界或冲突位置。";
        return new Result(placed,rejected,rowGap,message);
    }

    public static FootprintResult layoutOriented(List<Parcel> parcels,List<Parcel> existingBuildings,Settings settings,double angleRadians){
        if(!Double.isFinite(angleRadians))throw new IllegalArgumentException("排列方向无效。");
        Point origin=parcels.get(0).ring().get(0);double c=Math.cos(angleRadians),s=Math.sin(angleRadians);
        java.util.function.Function<Point,Point> local=p->{double x=p.x()-origin.x(),y=p.y()-origin.y();return new Point(x*c+y*s,-x*s+y*c);};
        java.util.function.Function<Point,Point> world=p->new Point(origin.x()+p.x()*c-p.y()*s,origin.y()+p.x()*s+p.y()*c);
        List<Parcel> lp=parcels.stream().map(p->new Parcel(p.ring().stream().map(local).toList())).toList();
        List<Parcel> lo=existingBuildings.stream().map(p->new Parcel(p.ring().stream().map(local).toList())).toList();
        Result result=layout(lp,lo,settings);
        List<Footprint> footprints=result.buildings().stream().map(r->new Footprint(r.corners().stream().map(world).toList())).toList();
        return new FootprintResult(footprints,result.rejected(),result.effectiveRowSpacing(),result.message());
    }

    /** Finds continuous service corridors between adjacent building rows in each parcel. */
    public static RoadResult layoutServiceRoads(List<Parcel> parcels,List<Footprint> buildings,double roadWidth,double buildingClearance){
        finitePositive(roadWidth,"service道路宽度");finiteNonNegative(buildingClearance,"道路到建筑退距");
        if(parcels==null||parcels.isEmpty()||buildings==null||buildings.size()<2)throw new IllegalArgumentException("请选中包含至少两栋住宅建筑的闭合地块。");
        List<Road> roads=new ArrayList<>();int rejected=0, considered=0, inParcelCount=0;double angle=dominantAngle(buildings);
        double c=Math.cos(angle),s=Math.sin(angle);Point origin=parcels.get(0).ring().get(0);
        java.util.function.Function<Point,Point> local=p->{double x=p.x()-origin.x(),y=p.y()-origin.y();return new Point(x*c+y*s,-x*s+y*c);};
        java.util.function.Function<Point,Point> world=p->new Point(origin.x()+p.x()*c-p.y()*s,origin.y()+p.x()*s+p.y()*c);
        for(Parcel parcel:parcels){
            Area allowed=pathArea(normalizedRing(parcel.ring()).stream().map(local).toList());
            List<Area> localBuildings=new ArrayList<>();List<Rectangle2D> boxes=new ArrayList<>();
            for(Footprint f:buildings){Area shape=pathArea(f.ring().stream().map(local).toList());if(!intersects(shape,allowed))continue;localBuildings.add(shape);boxes.add(shape.getBounds2D());}
            inParcelCount+=boxes.size();if(boxes.size()<2)continue;
            List<RowItem> items=new ArrayList<>();for(int i=0;i<boxes.size();i++){Rectangle2D b=boxes.get(i);items.add(new RowItem(i,(b.getMinY()+b.getMaxY())/2,b));}
            items.sort(java.util.Comparator.comparingDouble(RowItem::center));
            double typicalHeight=items.stream().mapToDouble(i->i.bounds().getHeight()).sorted().skip((items.size()-1)/2).findFirst().orElse(1);
            double tolerance=Math.max(0.5,Math.min(3.0,typicalHeight*0.2));List<List<RowItem>> rows=new ArrayList<>();
            for(RowItem item:items){if(rows.isEmpty()||item.center()-rows.get(rows.size()-1).stream().mapToDouble(RowItem::center).average().orElse(item.center())>tolerance)rows.add(new ArrayList<>());rows.get(rows.size()-1).add(item);}
            Rectangle2D bounds=allowed.getBounds2D();
            for(int ri=0;ri+1<rows.size();ri++){
                List<RowItem> upper=rows.get(ri),lower=rows.get(ri+1);
                double upperEdge=upper.stream().mapToDouble(i->i.bounds().getMaxY()).max().orElse(0),lowerEdge=lower.stream().mapToDouble(i->i.bounds().getMinY()).min().orElse(0);
                double freeGap=lowerEdge-upperEdge-2*buildingClearance-roadWidth;if(freeGap< -EPS){rejected++;continue;}
                double cy=(upperEdge+lowerEdge)/2;double rowMinX=Math.max(upper.stream().mapToDouble(i->i.bounds().getMinX()).min().orElse(bounds.getMinX()),lower.stream().mapToDouble(i->i.bounds().getMinX()).min().orElse(bounds.getMinX()));double rowMaxX=Math.min(upper.stream().mapToDouble(i->i.bounds().getMaxX()).max().orElse(bounds.getMaxX()),lower.stream().mapToDouble(i->i.bounds().getMaxX()).max().orElse(bounds.getMaxX()));
                if(rowMaxX-rowMinX<Math.max(roadWidth*2,4)){rejected++;continue;}
                for(double[] interval:new double[][]{{rowMinX,rowMaxX}}){considered++;double endBuffer=buildingClearance+roadWidth/2;double x1=Math.max(bounds.getMinX(),interval[0]+endBuffer),x2=Math.min(bounds.getMaxX(),interval[1]-endBuffer);if(x2-x1<Math.max(roadWidth*2,4)){rejected++;continue;}
                    List<Point> line=List.of(new Point(x1,cy),new Point(x2,cy));Area corridor=new Area(new BasicStroke((float)roadWidth,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND).createStrokedShape(path(line)));Area outside=(Area)corridor.clone();outside.subtract(allowed);boolean conflict=!outside.isEmpty();
                    if(!conflict){Area roadBuffer=new Area(new BasicStroke((float)(roadWidth+2*buildingClearance),BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND).createStrokedShape(path(line)));for(Area b:localBuildings)if(intersects(roadBuffer,b)){conflict=true;break;}}
                    if(conflict){rejected++;continue;}roads.add(new Road(line.stream().map(world).toList(),roadWidth));
                }
            }
        }
        if(roads.size()>MAX_BUILDINGS)return new RoadResult(List.of(),rejected,"候选道路超过500条上限，请分块生成。");
        String summary="地块内识别 "+inParcelCount+" 栋，主方向 "+String.format(java.util.Locale.ROOT,"%.1f°",Math.toDegrees(angle))+"；检查 "+considered+" 段局部排间通道，拒绝 "+rejected+" 段。";
        return new RoadResult(roads,rejected,roads.isEmpty()?summary+"未找到满足道路宽度、建筑退距和地块边界条件的通道。":summary+"可生成 "+roads.size()+" 条 service 道路。");
    }
    private record RowItem(int index,double center,Rectangle2D bounds){}
    private static double dominantAngle(List<Footprint> buildings){double sx=0,sy=0;for(Footprint f:buildings){List<Point> ring=normalizedRing(f.ring());double longest=0,angle=0;for(int i=0;i<ring.size();i++){Point a=ring.get(i),b=ring.get((i+1)%ring.size());double dx=b.x()-a.x(),dy=b.y()-a.y(),length=Math.hypot(dx,dy);if(length>longest){longest=length;angle=Math.atan2(dy,dx);}}if(longest>EPS){sx+=longest*Math.cos(2*angle);sy+=longest*Math.sin(2*angle);}}return 0.5*Math.atan2(sy,sx);}
    private static Path2D path(List<Point> points){Path2D.Double p=new Path2D.Double();for(int i=0;i<points.size();i++){Point q=points.get(i);if(i==0)p.moveTo(q.x(),q.y());else p.lineTo(q.x(),q.y());}return p;}
    /** Generates inward storefront polygons; overlapping building/road areas become multipolygon holes. */
    public static FootprintResult layoutShops(List<Parcel> parcels,List<Footprint> existingBuildings,double streetSetback,double depth,double height,double chamferDegrees){
        ShopResult result=layoutShopPolygons(parcels,existingBuildings,List.of(),streetSetback,depth,height,chamferDegrees);List<Footprint> flattened=result.polygons().stream().map(p->new Footprint(p.outer())).toList();return new FootprintResult(flattened,result.rejected(),0,result.message());
    }
    public static ShopResult layoutShopPolygons(List<Parcel> parcels,List<Footprint> buildings,List<Road> roads,double streetSetback,double depth,double height,double chamferDegrees){return layoutShopPolygons(parcels,buildings,roads,streetSetback,depth,height,chamferDegrees,0);}
    public static ShopResult layoutShopPolygons(List<Parcel> parcels,List<Footprint> buildings,List<Road> roads,double streetSetback,double depth,double height,double chamferDegrees,double roadClearance){
        finiteNonNegative(streetSetback,"底商沿街后退距离");finiteNonNegative(roadClearance,"底商到道路退距");finitePositive(depth,"底商进深");finitePositive(height,"底商高度");
        if(chamferDegrees!=30&&chamferDegrees!=45)throw new IllegalArgumentException("倒角角度仅支持30°或45°。");
        if(parcels==null||parcels.isEmpty())throw new IllegalArgumentException("请先选择闭合小区地块。");
        double chamferCut=Math.min(0.75,depth*0.08);List<Area> obstacles=new ArrayList<>();
        if(buildings!=null)for(Footprint f:buildings)obstacles.add(footprintArea(f));
        if(roads!=null)for(Road road:roads)obstacles.add(new Area(new BasicStroke((float)(road.width()+2*roadClearance),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(path(road.centerline()))));
        List<ShopPolygon> result=new ArrayList<>();int rejected=0;
        for(Parcel parcel:parcels){
            List<Area> candidates=new ArrayList<>();
            List<Point> ring=normalizedRing(parcel.ring());Area allowed=polygonArea(new Parcel(ring));double signed=signedArea(ring);double side=signed>=0?1:-1;
            List<Point> outer=offsetVertices(ring,streetSetback,side),inner=offsetVertices(ring,streetSetback+depth,side);
            for(int i=0;i<ring.size();i++){
                int j=(i+1)%ring.size();List<Point> base=List.of(outer.get(i),outer.get(j),inner.get(j),inner.get(i));
                List<Point> shape=chamferCorners(base,chamferCut);

                Area area=pathArea(shape);if(!containsArea(allowed,area)){rejected++;continue;}
                for(Area obstacle:obstacles)area.subtract(obstacle);
                if(area.isEmpty()){rejected++;continue;}
                candidates.add(area);
            }
            boolean overlaps=false;
            for(int i=0;i<candidates.size()&&!overlaps;i++)for(int j=i+1;j<candidates.size();j++)if(intersects(candidates.get(i),candidates.get(j))){overlaps=true;break;}
            if(overlaps){Area merged=new Area();for(Area candidate:candidates)merged.add(candidate);result.addAll(classifyContours(contours(merged)));}
            else for(Area candidate:candidates)result.addAll(classifyContours(contours(candidate)));
            if(result.size()>MAX_BUILDINGS)return new ShopResult(List.of(),rejected,"底商段数超过500段上限，请分割地块后分别生成。");
        }
        String message=result.isEmpty()?"没有找到可完整放入地块的底商边段。":"可生成 "+result.size()+" 个不重叠底商面；已扣除建筑和道路占用区域，跳过 "+rejected+" 条越界边。";
        return new ShopResult(result,rejected,message);
    }
    private static List<List<Point>> contours(Area area){
        List<List<Point>> out=new ArrayList<>();List<Point> current=null;double[] c=new double[6];PathIterator it=area.getPathIterator(null,0.05);
        while(!it.isDone()){int type=it.currentSegment(c);if(type==PathIterator.SEG_MOVETO){if(current!=null&&current.size()>=3)out.add(current);current=new ArrayList<>();current.add(new Point(c[0],c[1]));}else if(type==PathIterator.SEG_LINETO&&current!=null)current.add(new Point(c[0],c[1]));else if(type==PathIterator.SEG_CLOSE&&current!=null){if(current.size()>=3)out.add(current);current=null;}it.next();}
        if(current!=null&&current.size()>=3)out.add(current);return out;
    }
    private static List<ShopPolygon> classifyContours(List<List<Point>> contours){
        List<List<Point>> sorted=new ArrayList<>(contours);sorted.sort(java.util.Comparator.comparingDouble((List<Point> c)->Math.abs(signedArea(c))).reversed());
        int[] depth=new int[sorted.size()];int[] parent=new int[sorted.size()];java.util.Arrays.fill(parent,-1);
        List<Area> areas=sorted.stream().map(LayoutGeometry::pathArea).toList();
        for(int i=0;i<sorted.size();i++){
            Point sample=interiorPoint(sorted.get(i));double parentArea=Double.POSITIVE_INFINITY;
            for(int j=0;j<i;j++)if(areas.get(j).contains(sample.x(),sample.y())){double size=Math.abs(signedArea(sorted.get(j)));if(size<parentArea){parentArea=size;parent[i]=j;}}
            if(parent[i]>=0)depth[i]=depth[parent[i]]+1;
        }
        List<ShopPolygon> result=new ArrayList<>();
        for(int i=0;i<sorted.size();i++)if(depth[i]%2==0){List<List<Point>> holes=new ArrayList<>();for(int j=0;j<sorted.size();j++)if(parent[j]==i&&depth[j]%2==1)holes.add(sorted.get(j));result.add(new ShopPolygon(sorted.get(i),holes));}
        return result;
    }
    private static Point interiorPoint(List<Point> ring){Area area=pathArea(ring);Rectangle2D b=area.getBounds2D();for(int yi=1;yi<10;yi++)for(int xi=1;xi<10;xi++){Point p=new Point(b.getMinX()+b.getWidth()*xi/10,b.getMinY()+b.getHeight()*yi/10);if(area.contains(p.x(),p.y()))return p;}return ring.get(0);}
    private static List<Point> offsetVertices(List<Point> ring,double offset,double side){
        List<Point> out=new ArrayList<>();int n=ring.size();
        for(int i=0;i<n;i++){
            Point prev=ring.get((i+n-1)%n),v=ring.get(i),next=ring.get((i+1)%n);
            double pDx=v.x()-prev.x(),pDy=v.y()-prev.y(),cDx=next.x()-v.x(),cDy=next.y()-v.y();
            double pLen=Math.hypot(pDx,pDy),cLen=Math.hypot(cDx,cDy);if(pLen<=EPS||cLen<=EPS)throw new IllegalArgumentException("地块存在零长度边。");
            double pNx=-pDy/pLen*side,pNy=pDx/pLen*side,cNx=-cDy/cLen*side,cNy=cDx/cLen*side;
            Point pa=new Point(v.x()+pNx*offset,v.y()+pNy*offset),ca=new Point(v.x()+cNx*offset,v.y()+cNy*offset);
            double denom=pDx*cDy-pDy*cDx;
            if(Math.abs(denom)<EPS){double nx=pNx+cNx,ny=pNy+cNy,norm=Math.hypot(nx,ny);out.add(norm<EPS?pa:new Point(v.x()+nx/norm*offset/Math.max(0.1,Math.abs((pNx*cNx+pNy*cNy+1)/2)),v.y()+ny/norm*offset/Math.max(0.1,Math.abs((pNx*cNx+pNy*cNy+1)/2))));continue;}
            double t=((ca.x()-pa.x())*cDy-(ca.y()-pa.y())*cDx)/denom;
            out.add(new Point(pa.x()+t*pDx,pa.y()+t*pDy));
        }
        return out;
    }
    private static List<Point> chamferCorners(List<Point> polygon,double cut){
        List<Point> out=new ArrayList<>();for(int i=0;i<polygon.size();i++){
            Point prev=polygon.get((i+polygon.size()-1)%polygon.size()),v=polygon.get(i),next=polygon.get((i+1)%polygon.size());
            double a=Math.hypot(prev.x()-v.x(),prev.y()-v.y()),b=Math.hypot(next.x()-v.x(),next.y()-v.y());
            double ca=Math.min(cut,a*0.2),cb=Math.min(cut,b*0.2);
            out.add(new Point(v.x()+(prev.x()-v.x())*ca/a,v.y()+(prev.y()-v.y())*ca/a));
            out.add(new Point(v.x()+(next.x()-v.x())*cb/b,v.y()+(next.y()-v.y())*cb/b));
        }return out;
    }
    private static List<Point> normalizedRing(List<Point> input){List<Point> r=new ArrayList<>(input);if(r.size()>1&&distanceSquared(r.get(0),r.get(r.size()-1))<=EPS*EPS)r.remove(r.size()-1);if(r.size()<3)throw new IllegalArgumentException("地块至少需要三个不同顶点。");return r;}
    private static double signedArea(List<Point> p){double a=0;for(int i=0;i<p.size();i++){Point x=p.get(i),y=p.get((i+1)%p.size());a+=x.x()*y.y()-y.x()*x.y();}return a/2;}

    /** Tiles a selected building ring by translation while preserving its local orientation. */
    public static FootprintResult layoutFootprints(List<Parcel> parcels,List<Footprint> existingBuildings,
            Footprint sample,double sideClearance,Settings settings) {
        validate(settings);
        finiteNonNegative(sideClearance,"同排楼栋侧向净距");
        if(parcels==null||parcels.isEmpty())throw new IllegalArgumentException("请先选择至少一个闭合地块。");
        Area sampleArea=footprintArea(sample);
        Point origin=sample.ring().get(0);
        double ux=1,uy=0,longest=0;
        for(int i=1;i<sample.ring().size();i++){
            Point a=sample.ring().get(i-1),b=sample.ring().get(i);double dx=b.x()-a.x(),dy=b.y()-a.y(),len=dx*dx+dy*dy;
            if(len>longest){longest=len;double norm=Math.sqrt(len);ux=dx/norm;uy=dy/norm;}
        }
        if(longest<=EPS*EPS)throw new IllegalArgumentException("样板建筑边界无有效方向。");
        final double axisX=ux,axisY=uy,normalX=-uy,normalY=ux;
        java.util.function.Function<Point,Point> toLocal=p->{double dx=p.x()-origin.x(),dy=p.y()-origin.y();return new Point(dx*axisX+dy*axisY,dx*normalX+dy*normalY);};
        java.util.function.Function<Point,Point> toWorld=p->new Point(origin.x()+p.x()*axisX+p.y()*normalX,origin.y()+p.x()*axisY+p.y()*normalY);
        Footprint localSample=new Footprint(sample.ring().stream().map(toLocal).toList());
        List<Parcel> localParcels=parcels.stream().map(p->new Parcel(p.ring().stream().map(toLocal).toList())).toList();
        List<Footprint> localExisting=existingBuildings==null?List.of():existingBuildings.stream().map(f->new Footprint(f.ring().stream().map(toLocal).toList())).toList();
        Area localSampleArea=footprintArea(localSample);
        Rectangle2D sampleBounds=localSampleArea.getBounds2D();
        double width=sampleBounds.getWidth(),depth=sampleBounds.getHeight();
        if(width<=EPS||depth<=EPS)throw new IllegalArgumentException("样板建筑宽度或进深为零。");
        double rowGap=effectiveRowSpacing(settings),pitchX=width+sideClearance,pitchY=depth+rowGap;
        List<Area> parcelAreas=localParcels.stream().map(LayoutGeometry::polygonArea).toList();
        List<Area> obstacles=new ArrayList<>();
        for(Footprint f:localExisting)obstacles.add(footprintArea(f));
        List<Footprint> placed=new ArrayList<>();int rejected=0;long scanned=0;
        for(Area parcel:parcelAreas){
            Rectangle2D bounds=parcel.getBounds2D();
            long rows=safeCount(bounds.getHeight(),settings.endSetback(),depth,pitchY);
            long cols=safeCount(bounds.getWidth(),settings.endSetback(),width,pitchX);
            for(long row=0;row<rows;row++){
                double y=bounds.getMinY()+settings.endSetback()+row*pitchY;
                double offset=settings.staggered()&&row%2==1?pitchX/2:0;
                for(long col=0;col<cols;col++){
                    if(++scanned>MAX_CANDIDATES)return new FootprintResult(List.of(),rejected,rowGap,"候选网格超过 100 万个位置；请缩小地块或增大间距后重试。");
                    double dx=bounds.getMinX()+settings.endSetback()+col*pitchX+offset-sampleBounds.getMinX();
                    Footprint localCandidate=new Footprint(localSample.ring().stream().map(p->new Point(p.x()+dx,p.y()+y-sampleBounds.getMinY())).toList());
                    Area shape=footprintArea(localCandidate);
                    if(!containsArea(parcel,shape)){rejected++;continue;}
                    boolean collision=false;for(Area obstacle:obstacles)if(intersects(shape,obstacle)){collision=true;break;}
                    if(!collision)for(Footprint prior:placed)if(intersects(shape,footprintArea(prior))){collision=true;break;}
                    if(collision){rejected++;continue;}
                    placed.add(localCandidate);
                    if(placed.size()>MAX_BUILDINGS)return new FootprintResult(List.of(),rejected,rowGap,"布局超过 500 栋上限；请缩小地块或增大间距。");
                }
            }
        }
        List<Footprint> worldPlaced=placed.stream().map(f->new Footprint(f.ring().stream().map(toWorld).toList())).toList();
        String message=worldPlaced.isEmpty()?"没有找到可完整放入地块且避开既有建筑的位置。":"可放置 "+worldPlaced.size()+" 栋样板楼；拒绝 "+rejected+" 个越界或冲突位置。";
        return new FootprintResult(worldPlaced,rejected,rowGap,message);
    }

    static void validate(Settings s) {
        if (s == null) throw new IllegalArgumentException("布局参数缺失。");
        finitePositive(s.shortSide(), "建筑短边宽度");finitePositive(s.longSide(), "建筑长边长度");finitePositive(s.height(), "建筑高度");
        finiteNonNegative(s.endSetback(), "建筑端部退距");finiteNonNegative(s.sideClearance(), "同排侧向净距");finiteNonNegative(s.minimumRowSpacing(), "最小排间距");finiteNonNegative(s.sunFactor(), "日照距离系数");
        if (s.shortSide() > 500 || s.longSide() > 500 || s.height() > 1000 || s.endSetback() > 10000 || s.sideClearance() > 10000 || s.minimumRowSpacing() > 10000 || s.sunFactor() > 100) throw new IllegalArgumentException("参数超出安全范围。");
    }
    private static void finitePositive(double v,String n){if(!Double.isFinite(v)||v<=0)throw new IllegalArgumentException(n+"必须是有限正数。");}
    private static void finiteNonNegative(double v,String n){if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException(n+"必须是有限非负数。");}
    private static long safeCount(double span,double margin,double size,double pitch) {
        double available=span-2*margin;if(available+EPS<size||!Double.isFinite(available))return 0;
        double n=Math.floor((available-size+EPS)/pitch)+1;if(!Double.isFinite(n)||n<0||n>MAX_BUILDINGS*MAX_BUILDINGS)return MAX_BUILDINGS*MAX_BUILDINGS+1L;return(long)n;
    }
    private static Area polygonArea(Parcel p) {
        if(p==null||p.ring().size()<3)throw new IllegalArgumentException("地块至少需要三个不同顶点。");
        Area area=pathArea(p.ring());if(area.isEmpty())throw new IllegalArgumentException("地块边界没有有效面积。");return area;
    }
    private static Area footprintArea(Footprint p){if(p==null||p.ring().size()<3)throw new IllegalArgumentException("建筑样板至少需要三个不同顶点。");Area a=pathArea(p.ring());if(a.isEmpty())throw new IllegalArgumentException("建筑样板轮廓无有效面积。");return a;}
    private static Area pathArea(List<Point> points){Path2D.Double path=new Path2D.Double(Path2D.WIND_EVEN_ODD);Point first=points.get(0);requirePoint(first);path.moveTo(first.x(),first.y());Point prev=first;for(int i=1;i<points.size();i++){Point p=points.get(i);requirePoint(p);if(distanceSquared(prev,p)>EPS*EPS)path.lineTo(p.x(),p.y());prev=p;}path.closePath();return new Area(path);}
    private static void requirePoint(Point p){if(p==null||!Double.isFinite(p.x())||!Double.isFinite(p.y()))throw new IllegalArgumentException("地块坐标无效。");}
    private static double distanceSquared(Point a,Point b){double dx=a.x()-b.x(),dy=a.y()-b.y();return dx*dx+dy*dy;}
    private static Area rectArea(Rect r){return new Area(new Rectangle2D.Double(r.minX(),r.minY(),r.width(),r.height()));}
    private static boolean containsRectangle(Area parcel,Rect r){return containsArea(parcel,rectArea(r));}
    private static boolean containsArea(Area outer,Area inner){Area outside=(Area)inner.clone();outside.subtract(outer);return outside.isEmpty();}
    private static boolean intersects(Area a,Area b){Area overlap=(Area)a.clone();overlap.intersect(b);return !overlap.isEmpty();}
}
