package org.openstreetmap.josm.plugins.blocklayout;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Logging;

/** JOSM map mode for the independent residential building layout plugin. */
public final class BlockLayoutMode extends MapMode implements MapViewPaintable {
    private final BlockLayoutPanel panel;
    private DataSet data;
    private LocalProjection projection;
    private LayoutGeometry.Result preview;
    private List<LayoutGeometry.Footprint> footprintPreview=List.of();
    private List<LayoutGeometry.ShopPolygon> shopPreview=List.of();
    private List<LayoutGeometry.Road> roadPreview=List.of();
    private List<LayoutGeometry.Footprint> factoryPreview=List.of();
    private List<Integer> factoryPreviewLevels=List.of();
    private List<LayoutGeometry.Point> treePreview=List.of();
    private boolean active;
    private boolean ready;
    private boolean committing;
    private boolean directionPick;
    private Double directionAngle;
    private PreviewKind previewKind=PreviewKind.RECTANGLE;
    private java.util.Set<Long> previewParcelIds=java.util.Set.of();
    private Map<Long,String> previewGeometry=Map.of();
    private final KeyEventDispatcher keyDispatcher=this::dispatchKey;
    private final DataSetListenerAdapter dataListener;

    public BlockLayoutMode(BlockLayoutPanel panel) {
        super("建筑排布", null, "在选中闭合地块内预览建筑排布", Shortcut.registerShortcut("mapmode:blocklayout", "建筑排布", KeyEvent.VK_B, Shortcut.ALT_SHIFT), Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        this.panel=panel;
        Double savedAngle=panel.savedDirectionDegrees();if(savedAngle!=null)directionAngle=Math.toRadians(savedAngle);
        this.dataListener=new DataSetListenerAdapter(event -> {
            if(active && !committing){ready=false;preview=null;footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();factoryPreview=List.of();factoryPreviewLevels=List.of();treePreview=List.of();previewGeometry=Map.of();panel.message("地图数据已变化，请重新开始预览。");repaintMap();}
        });
        putValue(SMALL_ICON, BlockLayoutIcons.icon());
        putValue(LARGE_ICON_KEY, BlockLayoutIcons.icon());
    }

    @Override public boolean layerIsSupported(Layer layer) { return layer instanceof OsmDataLayer; }

    @Override public void enterMode() {
        super.enterMode(); active=true; ready=false;
        if (MainApplication.getMap()!=null) {
            MainApplication.getMap().mapView.addMouseListener(this);
            MainApplication.getMap().mapView.addMouseMotionListener(this);
            MainApplication.getMap().mapView.addTemporaryLayer(this);
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
        panel.message("请在当前图层选择一个或多个闭合地块，然后点击“开始排布预览”。");
    }

    @Override public void exitMode() {
        active=false; ready=false; preview=null;footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();factoryPreview=List.of();factoryPreviewLevels=List.of();treePreview=List.of(); projection=null;previewParcelIds=java.util.Set.of();previewGeometry=Map.of();
        if(data!=null){data.removeDataSetListener(dataListener);data=null;}
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
        if (MainApplication.getMap()!=null) {
            MainApplication.getMap().mapView.removeMouseListener(this);
            MainApplication.getMap().mapView.removeMouseMotionListener(this);
            MainApplication.getMap().mapView.removeTemporaryLayer(this);
        }
        super.exitMode();
    }

    void startPreview() {
        if (!active) { if (MainApplication.getMap()!=null) MainApplication.getMap().selectMapMode(this); return; }
        DataSet current=MainApplication.getLayerManager().getEditDataSet();
        if(data!=current){if(data!=null)data.removeDataSetListener(dataListener);data=current;if(data!=null)data.addDataSetListener(dataListener);}
        try {
            if (data==null) throw new IllegalArgumentException("请先打开一个可编辑的 OSM 数据图层。");
            List<Way> selected=new ArrayList<>(data.getSelectedWays());
            if (selected.isEmpty()) throw new IllegalArgumentException("请先选择至少一个闭合地块 way。");
            List<Way> parcelWays=new ArrayList<>(),sampleWays=new ArrayList<>();
            List<LayoutGeometry.Parcel> parcels=new ArrayList<>();
            for(Way way:selected){
                if(!way.isClosed()||way.getNodesCount()<4)throw new IllegalArgumentException("选区中有非闭合或过小的 way。");
                if(panel.useSample()&&way.hasKey("building"))sampleWays.add(way);else parcelWays.add(way);
            }
            if(parcelWays.isEmpty())throw new IllegalArgumentException("请至少选择一个小区地块 way；样板建筑不能兼作地块。");
            if(panel.useSample()&&sampleWays.size()!=1)throw new IllegalArgumentException("样板模式要求恰好选择一个带 building=* 的闭合 way，并同时选择小区地块。");

            if(!panel.useSample()&&!sampleWays.isEmpty())throw new IllegalArgumentException("矩形模式下请取消建筑选择，或启用样板轮廓模式。");
            LatLon origin=null;
            for(Way way:selected)for(Node node:way.getNodes()){
                if(node.getCoor()==null)throw new IllegalArgumentException("地块或样板包含未定位节点，无法排布。");
                if(origin==null)origin=node.getCoor();
            }
            projection=new LocalProjection(origin);
            for(Way way:parcelWays)parcels.add(parcel(way));
            final LatLon projectionOrigin=origin;
            List<LayoutGeometry.Parcel> obstacles=new ArrayList<>();
            for(Way way:data.getWays()) {
                if(!way.isUsable() || !way.isClosed() || !way.hasKey("building") || way.getNodesCount()<4 || sampleWays.contains(way) || parcelWays.contains(way) || !nearProjectionOrigin(way,origin)) continue;
                obstacles.add(parcel(way));
            }
            LayoutSettings settings=panel.settings();
            if(panel.factoryMode()){
                List<LayoutGeometry.Footprint> existing=data.getWays().stream().filter(w->w.isUsable()&&w.isClosed()&&w.hasKey("building")&&w.getNodesCount()>=4&&!parcelWays.contains(w)&&nearProjectionOrigin(w,projectionOrigin)).map(w->new LayoutGeometry.Footprint(projectRing(w))).toList();
                double angle=directionAngle==null?0:directionAngle;
                FactoryGeometry.Settings fs=new FactoryGeometry.Settings(panel.factoryMinWidth(),panel.factoryMaxWidth(),panel.factoryMinLength(),panel.factoryMaxLength(),panel.factoryFloorHeight(),panel.factoryMinLevels(),panel.factoryMaxLevels(),panel.factorySetback(),panel.factoryGap(),panel.factoryRowGap(),panel.factorySeed(),500,panel.factoryGroupSize());
                FactoryGeometry.Result result=FactoryGeometry.layout(parcels,existing,fs,angle);
                factoryPreview=result.buildings();factoryPreviewLevels=result.levels();footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();
                if(panel.factoryRoads()){FactoryRoadGeometry.Result roadResult=FactoryRoadGeometry.layout(parcels,result.buildings(),panel.factoryRoadWidth(),panel.factoryRoadClearance(),angle);roadPreview=roadResult.roads();}
                preview=null;previewKind=PreviewKind.FACTORIES;capturePreview(selected);ready=true;panel.message(result.message()+" 尺寸 "+panel.factoryMinLength()+"—"+panel.factoryMaxLength()+"×"+panel.factoryMinWidth()+"—"+panel.factoryMaxWidth()+"米；高度 "+panel.factoryMinLevels()+"—"+panel.factoryMaxLevels()+"层×"+panel.factoryFloorHeight()+"米。");
            }else if(panel.communityShops()){
                List<LayoutGeometry.Footprint> existing=data.getWays().stream().filter(w->w.isUsable()&&w.isClosed()&&w.hasKey("building")&&w.getNodesCount()>=4&&!parcelWays.contains(w)&&nearProjectionOrigin(w,projectionOrigin)).map(w->new LayoutGeometry.Footprint(projectRing(w))).toList();
                List<LayoutGeometry.Road> roads=data.getWays().stream().filter(w->w.isUsable()&&w.hasTag("highway","service")&&w.getNodesCount()>=2&&nearProjectionOrigin(w,projectionOrigin)).map(w->{double roadWidth=4;try{String value=w.get("width");if(value!=null)roadWidth=Double.parseDouble(value);}catch(NumberFormatException ignored){}return new LayoutGeometry.Road(w.getNodes().stream().map(n->projection.project(n.getCoor())).toList(),roadWidth);}).toList();
                LayoutGeometry.ShopResult result=LayoutGeometry.layoutShopPolygons(parcels,existing,roads,panel.shopSetback(),panel.shopDepth(),panel.shopHeight(),panel.chamferDegrees(),panel.shopRoadClearance());
                shopPreview=result.polygons();footprintPreview=List.of();roadPreview=List.of();preview=null;previewKind=PreviewKind.SHOPS;capturePreview(selected);ready=true;
                panel.message(result.message()+" 距边 "+panel.shopSetback()+"米，进深 "+panel.shopDepth()+"米，高 "+panel.shopHeight()+"米；道路和建筑占用部分已扣除。");
            }else if(panel.useSample()){ 
                Way sample=sampleWays.get(0);
                LayoutGeometry.Footprint footprint=new LayoutGeometry.Footprint(projectRing(sample));
                List<LayoutGeometry.Footprint> existing=data.getWays().stream().filter(w->w.isUsable()&&w.isClosed()&&w.hasKey("building")&&w.getNodesCount()>=4&&!sampleWays.contains(w)&&nearProjectionOrigin(w,projectionOrigin)).map(w->new LayoutGeometry.Footprint(projectRing(w))).toList();
                LayoutGeometry.FootprintResult result=LayoutGeometry.layoutFootprints(parcels,existing,footprint,settings.sideClearance(),settings.geometry());
                footprintPreview=result.buildings();shopPreview=List.of();roadPreview=List.of();preview=null;previewKind=PreviewKind.SAMPLE;capturePreview(selected);ready=true;
                panel.message(String.format(Locale.ROOT,"样板预览 %d 栋；有效排距 %.1f 米。%s",footprintPreview.size(),result.effectiveRowSpacing(),result.message()));
            }else if(panel.streetTreeMode()){
                List<LayoutGeometry.Road> roads;
                if(panel.treeUseSelectedRoad()){
                    roads=selected.stream().filter(w->w.isUsable()&&!w.isClosed()&&w.hasTag("highway")&&!w.hasTag("highway","service")&&w.getNodesCount()>=2&&nearProjectionOrigin(w,projectionOrigin)).map(w->{double roadWidth=4;try{String value=w.get("width");if(value!=null)roadWidth=Double.parseDouble(value);}catch(NumberFormatException ignored){}return new LayoutGeometry.Road(w.getNodes().stream().map(n->projection.project(n.getCoor())).toList(),roadWidth);}).toList();
                    if(roads.isEmpty())throw new IllegalArgumentException("请选择至少一条带highway标签(非snservice)的道路。");
                    StreetTreeGeometry.Settings treeSettings=new StreetTreeGeometry.Settings(panel.treeSpacing(),panel.treeOffset(),panel.treeDoubleSide());
                    StreetTreeGeometry.Result result=StreetTreeGeometry.layoutRoadTrees(roads.get(0),treeSettings);
                    treePreview=result.trees();footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();factoryPreview=List.of();preview=null;previewKind=PreviewKind.STREET_TREES;capturePreview(selected);ready=true;
                    panel.message(result.message()+" 间距"+panel.treeSpacing()+"米,偏移"+panel.treeOffset()+"米。");
                }else{
                    roads=data.getWays().stream().filter(w->w.isUsable()&&w.hasTag("highway","service")&&w.getNodesCount()>=2&&nearProjectionOrigin(w,projectionOrigin)).map(w->{double roadWidth=4;try{String value=w.get("width");if(value!=null)roadWidth=Double.parseDouble(value);}catch(NumberFormatException ignored){}return new LayoutGeometry.Road(w.getNodes().stream().map(n->projection.project(n.getCoor())).toList(),roadWidth);}).toList();
                    StreetTreeGeometry.Settings treeSettings=new StreetTreeGeometry.Settings(panel.treeSpacing(),panel.treeOffset(),panel.treeDoubleSide());
                    StreetTreeGeometry.Result result=StreetTreeGeometry.layoutServiceRoadTrees(parcels,roads,treeSettings);
                    treePreview=result.trees();footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();factoryPreview=List.of();preview=null;previewKind=PreviewKind.STREET_TREES;capturePreview(selected);ready=true;
                    panel.message(result.message()+" 间距"+panel.treeSpacing()+"米,偏移"+panel.treeOffset()+"米。");
                }
            }else if(directionAngle!=null){
                LayoutGeometry.FootprintResult result=LayoutGeometry.layoutOriented(parcels,obstacles,settings.geometry(),directionAngle);
                footprintPreview=result.buildings();shopPreview=List.of();roadPreview=List.of();preview=null;previewKind=PreviewKind.ORIENTED;capturePreview(selected);ready=true;
                panel.message("按拾取地块边方向预览 "+footprintPreview.size()+" 栋。"+result.message());
            }else{
                preview=LayoutGeometry.layout(parcels,obstacles,settings.geometry());footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();previewKind=PreviewKind.RECTANGLE;
                capturePreview(selected);ready=true;
                panel.message(String.format(Locale.ROOT,"预览 %d 栋；有效排距 %.1f 米。%s 左键确认生成，Esc 退出。",preview.buildings().size(),preview.effectiveRowSpacing(),preview.message()));
            }
            repaintMap();
        } catch (RuntimeException ex) {
            ready=false;preview=null;footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();projection=null;panel.message(ex.getMessage());repaintMap();
        }
    }

    private boolean nearProjectionOrigin(Way way,LatLon origin){
        for(Node node:way.getNodes()){LatLon ll=node.getCoor();if(ll!=null&&Math.abs(ll.lat()-origin.lat())<=0.25&&Math.abs(ll.lon()-origin.lon())<=0.25)return true;}
        return false;
    }
    /** Projects a way's nodes into a local-metre ring, dropping the duplicate node JOSM uses to mark closure. */
    private List<LayoutGeometry.Point> projectRing(Way way){
        List<LayoutGeometry.Point> ring=new ArrayList<>();
        for(Node node:way.getNodes())ring.add(projection.project(node.getCoor()));
        stripClosingDuplicate(ring);
        return ring;
    }

    private static void stripClosingDuplicate(List<LayoutGeometry.Point> ring){
        if(ring.size()>1){LayoutGeometry.Point first=ring.get(0),last=ring.get(ring.size()-1);
            if(Math.abs(first.x()-last.x())<=1e-9&&Math.abs(first.y()-last.y())<=1e-9)ring.remove(ring.size()-1);}
    }

    private LayoutGeometry.Parcel parcel(Way way) {
        List<LayoutGeometry.Point> ring=new ArrayList<>();
        for(Node node:way.getNodes()) {
            LayoutGeometry.Point p=projection.project(node.getCoor());
            if(Math.abs(p.x())>100000 || Math.abs(p.y())>100000)throw new IllegalArgumentException("所选地块距离过远或范围超过100公里；请分批选择附近地块。");
            ring.add(p);
        }
        stripClosingDuplicate(ring);
        return new LayoutGeometry.Parcel(ring);
    }

    @Override public void mouseMoved(MouseEvent e) {
        if(active && ready && preview!=null) panel.message(String.format(Locale.ROOT,"预览 %d 栋；左键确认生成，Ctrl+Z 撤销，Esc 退出。",preview.buildings().size()));
    }
    @Override public void mouseDragged(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
    @Override public void mousePressed(MouseEvent e) {
        if(e.getButton()!=MouseEvent.BUTTON1 || e.isControlDown() || e.isAltDown() || e.isShiftDown()) return;
        if(directionPick){pickDirection(e);return;}
        if(!ready)return;
        if((preview==null||preview.buildings().isEmpty())&&footprintPreview.isEmpty()&&factoryPreview.isEmpty()&&shopPreview.isEmpty()&&roadPreview.isEmpty()&&treePreview.isEmpty()) { panel.message("没有可生成的合规对象。"); return; }
        createBuildings();
    }

    void beginDirectionPick(){
        if(!active){if(MainApplication.getMap()!=null)MainApplication.getMap().selectMapMode(this);}
        DataSet current=MainApplication.getLayerManager().getEditDataSet();
        if(data!=current){if(data!=null)data.removeDataSetListener(dataListener);data=current;if(data!=null)data.addDataSetListener(dataListener);}
        directionPick=true;ready=false;preview=null;footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();panel.message("点击所选地块的一条边，排列方向将与该边平行。");
    }
    void resetDirection(){directionPick=false;directionAngle=null;panel.clearDirection();settingsChanged();}
    private void pickDirection(MouseEvent event){
        List<Way> parcels=data==null?List.of():data.getSelectedWays().stream().filter(w->w.isClosed()&&(!panel.useSample()||!w.hasKey("building"))).toList();
        if(parcels.isEmpty()){panel.message("请先选中闭合地块，再拾取其边方向。");return;}
        if(projection==null){LatLon origin=parcels.get(0).firstNode().getCoor();projection=new LocalProjection(origin);}
        LatLon cursor=MainApplication.getMap().mapView.getLatLon(event.getX(),event.getY());LayoutGeometry.Point p=projection.project(cursor);double best=Double.POSITIVE_INFINITY,angle=0;
        for(Way way:parcels){List<Node> ns=way.getNodes();for(int i=1;i<ns.size();i++){LayoutGeometry.Point a=projection.project(ns.get(i-1).getCoor()),b=projection.project(ns.get(i).getCoor());double dx=b.x()-a.x(),dy=b.y()-a.y(),lengthSquared=dx*dx+dy*dy;if(lengthSquared<1e-12)continue;double t=Math.max(0,Math.min(1,((p.x()-a.x())*dx+(p.y()-a.y())*dy)/lengthSquared));double ex=a.x()+t*dx-p.x(),ey=a.y()+t*dy-p.y(),d=ex*ex+ey*ey;if(d<best){best=d;angle=Math.atan2(dy,dx);}}}
        directionAngle=angle;directionPick=false;panel.saveDirection(Math.toDegrees(angle));panel.directionMessage(String.format(Locale.ROOT,"排列方向：与所选地块边平行（%.1f°）",Math.toDegrees(angle)));panel.message("方向已设置；点击“开始排布预览”更新阵列。");
    }
    private void capturePreview(List<Way> ways){previewParcelIds=ways.stream().map(Way::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());previewGeometry=captureGeometry(ways);}
    private static Map<Long,String> captureGeometry(java.util.Collection<Way> ways){Map<Long,String> snapshot=new LinkedHashMap<>();for(Way way:ways){StringBuilder b=new StringBuilder();for(Node node:way.getNodes())b.append(node.getUniqueId()).append('@').append(node.getCoor()).append(';');snapshot.put(way.getUniqueId(),b.toString());}return Map.copyOf(snapshot);}
    void settingsChanged(){if(active){ready=false;preview=null;footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();factoryPreview=List.of();factoryPreviewLevels=List.of();treePreview=List.of();panel.message("参数已变化，请重新开始预览。");repaintMap();}}

    private void createBuildings() {
        try {
            if(data==null || data!=MainApplication.getLayerManager().getEditDataSet())throw new IllegalStateException("当前可编辑图层已变化，请重新预览。");
            java.util.Set<Long> currentIds=data.getSelectedWays().stream().map(Way::getUniqueId).collect(java.util.stream.Collectors.toSet());
            if(!currentIds.equals(previewParcelIds)||!captureGeometry(data.getSelectedWays()).equals(previewGeometry))throw new IllegalStateException("地块选择或边界几何已变化，请重新预览。");
            if(previewKind==PreviewKind.STREET_TREES){
                List<Command> commands=new ArrayList<>();
                for(LayoutGeometry.Point point:treePreview){Node node=new Node(projection.inverse(point));node.put("natural","tree");commands.add(new AddCommand(data,node));}
                int treeCount=treePreview.size();
                committing=true;
                try { UndoRedoHandler.getInstance().add(new SequenceCommand("行道树："+treeCount+"棵",commands)); }
                finally { committing=false; }
                panel.message("已生成 "+treeCount+" 棵行道树；Ctrl+Z可一次撤销。");
                ready=false;preview=null;footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();factoryPreview=List.of();factoryPreviewLevels=List.of();treePreview=List.of();previewParcelIds=java.util.Set.of();previewGeometry=Map.of();repaintMap();
                return;
            }
            LayoutSettings settings=panel.settings();
            List<Command> commands=new ArrayList<>();Map<String,Node> generatedNodes=new LinkedHashMap<>();
                        List<List<LayoutGeometry.Point>> outlines=previewKind==PreviewKind.RECTANGLE?preview.buildings().stream().map(LayoutGeometry.Rect::corners).toList():(previewKind==PreviewKind.FACTORIES?factoryPreview:footprintPreview).stream().map(LayoutGeometry.Footprint::ring).toList();
            if(previewKind==PreviewKind.SHOPS){
                for(LayoutGeometry.ShopPolygon polygon:shopPreview){Relation relation=toShopRelation(polygon,projection::inverse,data,panel.shopHeight(),commands,generatedNodes);commands.add(new AddCommand(data,relation));}
            }else {if(previewKind==PreviewKind.FACTORIES)for(LayoutGeometry.Road road:roadPreview){Way way=toRoadWay(road,projection::inverse,data,commands,generatedNodes);commands.add(new AddCommand(data,way));}for(int i=0;i<outlines.size();i++){Way building=toBuildingWay(outlines.get(i),projection::inverse,settings,data,commands,generatedNodes);if(previewKind==PreviewKind.FACTORIES){int levels=factoryPreviewLevels.get(i);building.put("building","industrial");building.put("building:levels",Integer.toString(levels));building.put("height",format(panel.factoryFloorHeight()*levels));}commands.add(new AddCommand(data,building));}}
            int buildingCount=previewKind==PreviewKind.SHOPS?shopPreview.size():outlines.size();
            committing=true;
            try { UndoRedoHandler.getInstance().add(new SequenceCommand((previewKind==PreviewKind.FACTORIES?"工业厂房：":previewKind==PreviewKind.SHOPS?"社区底商：":"住宅楼排布：")+buildingCount+("栋/段"),commands)); }
            finally { committing=false; }
            panel.message("已生成 "+buildingCount+(previewKind==PreviewKind.FACTORIES?" 栋工业厂房"+(roadPreview.isEmpty()?"":"及 "+roadPreview.size()+" 条 service 通道"):previewKind==PreviewKind.SHOPS?" 段底商":" 栋住宅楼")+"；Ctrl+Z 可一次撤销。");
            ready=false;preview=null;footprintPreview=List.of();shopPreview=List.of();roadPreview=List.of();factoryPreview=List.of();factoryPreviewLevels=List.of();previewParcelIds=java.util.Set.of();previewGeometry=Map.of();repaintMap();
        } catch (RuntimeException ex) { Logging.error(ex);panel.message("创建失败："+ex.getMessage()); }
    }

    static Way toRoadWay(LayoutGeometry.Road road,java.util.function.Function<LayoutGeometry.Point,LatLon> inverse,DataSet data,List<Command> commands){return toRoadWay(road,inverse,data,commands,new LinkedHashMap<>());}
    static Way toRoadWay(LayoutGeometry.Road road,java.util.function.Function<LayoutGeometry.Point,LatLon> inverse,DataSet data,List<Command> commands,Map<String,Node> cache){Way way=new Way();List<Node> nodes=new ArrayList<>();for(LayoutGeometry.Point p:road.centerline()){Node n=sharedNode(p,inverse,data,commands,cache);nodes.add(n);}way.setNodes(nodes);way.put("highway","service");way.put("service","driveway");way.put("width",format(road.width()));return way;}
    static Relation toShopRelation(LayoutGeometry.ShopPolygon polygon,java.util.function.Function<LayoutGeometry.Point,LatLon> inverse,DataSet data,double height,List<Command> commands){return toShopRelation(polygon,inverse,data,height,commands,new LinkedHashMap<>());}
    static Relation toShopRelation(LayoutGeometry.ShopPolygon polygon,java.util.function.Function<LayoutGeometry.Point,LatLon> inverse,DataSet data,double height,List<Command> commands,Map<String,Node> cache){
        Way outer=addClosedRing(polygon.outer(),inverse,data,commands,cache);Relation relation=new Relation();List<RelationMember> members=new ArrayList<>();members.add(new RelationMember("outer",outer));
        for(List<LayoutGeometry.Point> hole:polygon.holes())members.add(new RelationMember("inner",addClosedRing(hole,inverse,data,commands,cache)));
        relation.setMembers(members);relation.setKeys(Map.of("type","multipolygon","building","retail","height",format(height)));return relation;
    }
    private static Way addClosedRing(List<LayoutGeometry.Point> points,java.util.function.Function<LayoutGeometry.Point,LatLon> inverse,DataSet data,List<Command> commands,Map<String,Node> cache){Way way=new Way();List<Node> nodes=new ArrayList<>();for(LayoutGeometry.Point p:points)nodes.add(sharedNode(p,inverse,data,commands,cache));closeRing(nodes);way.setNodes(nodes);commands.add(new AddCommand(data,way));return way;}
    /**
     * Closes a node list exactly once. JOSM marks a way closed by repeating its first node at the
     * end, and {@code Way.isClosed()} compares node identity, so the trailing entry must be the
     * very same {@link Node} instance. A ring that already repeats its first coordinate therefore
     * loses that redundant copy instead of gaining a second one.
     */
    private static void closeRing(List<Node> nodes){
        if(nodes.isEmpty())return;
        Node first=nodes.get(0);
        if(nodes.size()>1){
            Node last=nodes.get(nodes.size()-1);
            if(samePosition(first,last))nodes.remove(nodes.size()-1);
        }
        nodes.add(first);
    }

    private static boolean samePosition(Node a,Node b){
        if(a==b)return true;
        LatLon ca=a.getCoor(),cb=b.getCoor();
        if(ca==null||cb==null)return false;
        return Math.abs(ca.lat()-cb.lat())<=1e-9&&Math.abs(ca.lon()-cb.lon())<=1e-9;
    }

    private static String nodeKey(LayoutGeometry.Point point){return Double.doubleToLongBits(point.x())+":"+Double.doubleToLongBits(point.y());}
    private static Node sharedNode(LayoutGeometry.Point point,java.util.function.Function<LayoutGeometry.Point,LatLon> inverse,DataSet data,List<Command> commands,Map<String,Node> cache){String key=nodeKey(point);Node node=cache.get(key);if(node==null){node=new Node(inverse.apply(point));cache.put(key,node);commands.add(new AddCommand(data,node));}return node;}
    static Way toShopWay(List<LayoutGeometry.Point> outline,java.util.function.Function<LayoutGeometry.Point,LatLon> inverse,double height){
        List<Node> nodes=new ArrayList<>();for(LayoutGeometry.Point point:outline)nodes.add(new Node(inverse.apply(point)));closeRing(nodes);
        Way shop=new Way();shop.setNodes(nodes);shop.setKeys(Map.of("building","retail","height",format(height)));return shop;
    }
    static Way toBuildingWay(List<LayoutGeometry.Point> outline,java.util.function.Function<LayoutGeometry.Point,LatLon> inverse,LayoutSettings settings){
        List<Node> nodes=new ArrayList<>();for(LayoutGeometry.Point point:outline)nodes.add(new Node(inverse.apply(point)));closeRing(nodes);Way building=new Way();building.setNodes(nodes);Map<String,String> tags=new LinkedHashMap<>();tags.put("building",settings.buildingTag());tags.put("height",format(settings.height()));tags.put("building:levels",Long.toString(Math.max(1,Math.round(settings.height()/3.0))));building.setKeys(tags);return building;
    }
    static Way toBuildingWay(List<LayoutGeometry.Point> outline,java.util.function.Function<LayoutGeometry.Point,LatLon> inverse,LayoutSettings settings,DataSet data,List<Command> commands,Map<String,Node> cache){
        List<Node> nodes=new ArrayList<>();for(LayoutGeometry.Point point:outline)nodes.add(sharedNode(point,inverse,data,commands,cache));nodes.add(nodes.get(0));
        Way building=new Way();building.setNodes(nodes);Map<String,String> tags=new LinkedHashMap<>();tags.put("building",settings.buildingTag());tags.put("height",format(settings.height()));
        long levels=Math.max(1,Math.round(settings.height()/3.0));tags.put("building:levels",Long.toString(levels));building.setKeys(tags);return building;
    }
    private static String format(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}

    private boolean dispatchKey(KeyEvent event) {
        if(!active || event.getID()!=KeyEvent.KEY_PRESSED || event.isControlDown() || event.isAltDown() || event.isMetaDown()) return false;
        if(event.getKeyCode()==KeyEvent.VK_ESCAPE) { if(MainApplication.getMap()!=null) MainApplication.getMap().selectSelectTool(false); return true; }
        return false;
    }
    private void repaintMap(){if(MainApplication.getMap()!=null)MainApplication.getMap().mapView.repaint();}
    @Override public String getModeHelpText(){return "建筑排布：选择闭合地块，开始预览，左键一次生成；Ctrl+Z 撤销，Esc 退出。";}

    @Override public void paint(Graphics2D graphics, MapView mapView, Bounds bounds) {
        if(!active || !ready || projection==null || (preview==null&&footprintPreview.isEmpty()&&factoryPreview.isEmpty()&&shopPreview.isEmpty()&&roadPreview.isEmpty()&&treePreview.isEmpty())) return;
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setStroke(new BasicStroke(2.0f));
            if(previewKind==PreviewKind.STREET_TREES){
                g.setColor(new Color(34,139,34,200));
                for(LayoutGeometry.Point point:treePreview){Point p=mapView.getPoint(projection.inverse(point));g.fillOval(p.x-4,p.y-4,8,8);}
                return;
            }
            List<List<LayoutGeometry.Point>> outlines=preview!=null?preview.buildings().stream().map(LayoutGeometry.Rect::corners).toList():(previewKind==PreviewKind.FACTORIES?factoryPreview:footprintPreview).stream().map(LayoutGeometry.Footprint::ring).toList();
            if(previewKind==PreviewKind.SHOPS){for(LayoutGeometry.ShopPolygon polygon:shopPreview){paintRing(g,mapView,polygon.outer());for(List<LayoutGeometry.Point> hole:polygon.holes())paintRing(g,mapView,hole);}}
                        if(previewKind==PreviewKind.SHOPS)return;
            if(previewKind==PreviewKind.FACTORIES&&!roadPreview.isEmpty()){g.setColor(new Color(90,90,90,190));for(LayoutGeometry.Road road:roadPreview){Path2D.Double rp=new Path2D.Double();for(int i=0;i<road.centerline().size();i++){Point q=mapView.getPoint(projection.inverse(road.centerline().get(i)));if(i==0)rp.moveTo(q.x,q.y);else rp.lineTo(q.x,q.y);}g.setStroke(new BasicStroke(3.0f));g.draw(rp);}}
            for(List<LayoutGeometry.Point> points:outlines) {
                Path2D.Double path=new Path2D.Double();
                for(int i=0;i<points.size();i++){Point p=mapView.getPoint(projection.inverse(points.get(i)));if(i==0)path.moveTo(p.x,p.y);else path.lineTo(p.x,p.y);}path.closePath();
                g.setColor(new Color(70,130,210,55));g.fill(path);g.setColor(new Color(30,90,170,210));g.draw(path);
            }
        } finally {g.dispose();}
    }

    private void paintRing(Graphics2D g,MapView mapView,List<LayoutGeometry.Point> points){Path2D.Double p=new Path2D.Double();for(int i=0;i<points.size();i++){Point q=mapView.getPoint(projection.inverse(points.get(i)));if(i==0)p.moveTo(q.x,q.y);else p.lineTo(q.x,q.y);}p.closePath();g.setColor(new Color(70,130,210,45));g.fill(p);g.setColor(new Color(30,90,170,210));g.draw(p);}
    private enum PreviewKind { RECTANGLE, SAMPLE, ORIENTED, SHOPS, FACTORIES, STREET_TREES }
    private static final class LocalProjection {
        private static final double R=6371008.8;
        private final double lat0,lon0,cos;
        LocalProjection(LatLon origin){lat0=origin.lat();lon0=origin.lon();cos=Math.cos(Math.toRadians(lat0));if(Math.abs(cos)<0.01)throw new IllegalArgumentException("第一版不支持极地区域。");}
        LayoutGeometry.Point project(LatLon ll){return new LayoutGeometry.Point(R*Math.toRadians(ll.lon()-lon0)*cos,R*Math.toRadians(ll.lat()-lat0));}
        LatLon inverse(LayoutGeometry.Point p){return new LatLon(lat0+Math.toDegrees(p.y()/R),lon0+Math.toDegrees(p.x()/(R*cos)));}
    }
}
