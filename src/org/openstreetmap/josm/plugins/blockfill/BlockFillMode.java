package org.openstreetmap.josm.plugins.blockfill;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import org.openstreetmap.josm.tools.ImageProvider;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.command.*;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.*;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.gui.*;
import org.openstreetmap.josm.gui.layer.*;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Logging;

public final class BlockFillMode extends MapMode implements MapViewPaintable {
    private static final Set<String> MAIN_ROADS=Set.of("motorway","motorway_link","trunk","trunk_link","primary","primary_link","secondary","secondary_link","tertiary","tertiary_link","unclassified","residential","living_street","road","service","pedestrian");
    private final BlockFillPanel panel;
    private final javax.swing.Timer previewTimer;
    private DataSet data;
    private DataSet geometryData;
    private BlockGeometry geometry;
    private LocalMeters meters;
    private Point mouse;
    private BlockGeometry.Candidate candidate;
    private BlockGeometry.Face sourceFace;
    private boolean active;
    private long revision;
    private SwingWorker<BuildResult,Void> worker;
    private String buildError;
    private final DataSetListenerAdapter listener=new DataSetListenerAdapter(event -> invalidateGeometry());
    private final KeyEventDispatcher keys=this::dispatchKey;
    record BuildResult(BlockGeometry geometry,LocalMeters meters) { }
    static final class LocalMeters {
        private static final double R=6371008.8;
        final double lon0,lat0,cos;
        LocalMeters(double lat,double lon){lat0=lat;lon0=lon;cos=Math.cos(Math.toRadians(lat));if(Math.abs(cos)<0.01)throw new IllegalArgumentException("第一版不支持极地区域。");}
        BlockGeometry.Point project(LatLon ll){return new BlockGeometry.Point(R*Math.toRadians(ll.lon()-lon0)*cos,R*Math.toRadians(ll.lat()-lat0));}
        LatLon inverse(BlockGeometry.Point p){return new LatLon(lat0+Math.toDegrees(p.y()/R),lon0+Math.toDegrees(p.x()/(R*cos)));}
    }
    public BlockFillMode(BlockFillPanel panel) {
        super("街区填充", null, "在道路围合区域点击生成用地面", Shortcut.registerShortcut("mapmode:blockfill","街区填充",KeyEvent.VK_L,Shortcut.ALT_SHIFT), Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        putValue(SMALL_ICON,BlockFillIcons.icon());
        putValue(LARGE_ICON_KEY,BlockFillIcons.icon());
        this.panel=panel;
        previewTimer=new javax.swing.Timer(110,e -> updatePreview());previewTimer.setRepeats(false);
    }
    @Override public boolean layerIsSupported(Layer layer){return layer instanceof OsmDataLayer;}
    @Override public void enterMode() {
        super.enterMode();active=true;
        MainApplication.getMap().mapView.addMouseListener(this);MainApplication.getMap().mapView.addMouseMotionListener(this);MainApplication.getMap().mapView.addTemporaryLayer(this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keys);
        panel.showDialog();bindData();invalidateGeometry();
    }
    @Override public void exitMode() {
        active=false;previewTimer.stop();revision++;
        if(worker!=null)worker.cancel(true);
        if(MainApplication.getMap()!=null){MainApplication.getMap().mapView.removeMouseListener(this);MainApplication.getMap().mapView.removeMouseMotionListener(this);MainApplication.getMap().mapView.removeTemporaryLayer(this);}
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keys);
        if(data!=null)data.removeDataSetListener(listener);data=null;geometry=null;candidate=null;sourceFace=null;
        super.exitMode();
    }
    private void bindData(){
        DataSet now=MainApplication.getLayerManager().getEditDataSet();
        if(now!=data){if(data!=null)data.removeDataSetListener(listener);data=now;if(data!=null)data.addDataSetListener(listener);geometry=null;revision++;}
    }
    void invalidateGeometry(){
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(this::invalidateGeometry);return;}
        geometry=null;candidate=null;sourceFace=null;buildError=null;revision++;
        if(worker!=null)worker.cancel(true);
        if(active){previewTimer.restart();repaint();}
    }
    void refreshPreview(){if(active){previewTimer.restart();repaint();}}
    @Override public void mouseMoved(MouseEvent e){mouse=e.getPoint();previewTimer.restart();}
    @Override public void mouseDragged(MouseEvent e){candidate=null;sourceFace=null;repaint();}
    @Override public void mouseExited(MouseEvent e){mouse=null;candidate=null;sourceFace=null;repaint();}
    @Override public void mousePressed(MouseEvent e){
        if(e.getButton()!=MouseEvent.BUTTON1 || e.isControlDown()||e.isAltDown()||e.isShiftDown())return;
        mouse=e.getPoint();previewTimer.stop();updatePreview();
        if(candidate==null||!candidate.valid()){if(candidate!=null)panel.message(candidate.error());return;}
        createArea();
    }
    private boolean dispatchKey(KeyEvent e){
        if(!active||e.getID()!=KeyEvent.KEY_PRESSED||e.isControlDown()||e.isAltDown()||e.isMetaDown())return false;
        Component focus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if(focus!=null&&SwingUtilities.isDescendingFrom(focus,panel))return false;
        if(focus instanceof javax.swing.text.JTextComponent)return false;
        if(e.getKeyCode()==KeyEvent.VK_ESCAPE){MainApplication.getMap().selectSelectTool(false);return true;}
        int i=e.getKeyCode()-KeyEvent.VK_1;if(i>=0&&i<Math.min(9,panel.category.getItemCount())){panel.category.setSelectedIndex(i);refreshPreview();return true;}return false;
    }
    private void updatePreview(){
        if(!active||MainApplication.getMap()==null)return;
        bindData();candidate=null;sourceFace=null;
        if(data==null){panel.message("请先创建或打开可编辑的 OSM 数据图层。");repaint();return;}
        if(geometry==null||geometryData!=data){if(buildError!=null){panel.message(buildError);return;}if(worker==null||worker.isDone())build();return;}
        if(mouse==null){panel.message("道路 "+geometry.edgeCount+" 段 / 街区 "+geometry.faces().size()+" 块；移入街区预览");repaint();return;}
        LatLon ll=MainApplication.getMap().mapView.getLatLon(mouse.x,mouse.y);
        BlockGeometry.Point p=meters.project(ll);
        candidate=geometry.at(p.x(),p.y(),panel.insetMeters());
        for(BlockGeometry.Face face:geometry.faces())if(face.path().contains(p.x(),p.y())){sourceFace=face;break;}
        if(candidate.valid())panel.message(String.format(Locale.ROOT,"%s · %.0f m² · 点击生成",panel.entry().name(),candidate.area()));
        else panel.message(candidate.error());repaint();
    }
    private void build(){
        if(data==null)return;
        final DataSet snapshotData=data;final long token=revision;
        final boolean selected=panel.selectedOnly.isSelected(),minor=panel.minorRoads.isSelected();
        final MapView mapView=MainApplication.getMap().mapView;
        Bounds view=mapView.getLatLonBounds(mapView.getVisibleRect());
        if(view==null){buildError="无法读取当前地图视口，请缩放到待绘制街区后重试。";panel.message(buildError);return;}
        // Query ways intersecting a local working window, and clip their segments to that window.
        // A single active layer may span an entire country; never project every road from its first node.
        final double margin=0.03;
        final double minLat=Math.max(-90,view.getMinLat()-margin),maxLat=Math.min(90,view.getMaxLat()+margin);
        final double minLon=Math.max(-180,view.getMinLon()-margin),maxLon=Math.min(180,view.getMaxLon()+margin);
        final Collection<Way> ways=selected?new ArrayList<>(data.getSelectedWays()):new ArrayList<>(data.searchWays(new BBox(minLon,minLat,maxLon,maxLat)));
        final List<Way> usable=new ArrayList<>();
        for(Way w:ways)if(eligible(w,minor))usable.add(w);
        if(usable.isEmpty()){
            buildError=selected?"选中的范围内没有可用道路；检查 highway 标签或道路筛选。":"当前地图视口附近没有可用道路；请缩放到要绘制的街区并刷新道路。";
            panel.message(buildError);return;
        }
        final LatLon center=selected?usable.get(0).firstNode().getCoor():view.getCenter();
        final LocalMeters local=new LocalMeters(center.lat(),center.lon());
        final List<BlockGeometry.Segment> segments=new ArrayList<>();
        for(Way w:usable){List<Node> nodes=w.getNodes();for(int i=1;i<nodes.size();i++){
            Node a=nodes.get(i-1),b=nodes.get(i);
            boolean aIn=inBounds(a.getCoor(),minLat,minLon,maxLat,maxLon),bIn=inBounds(b.getCoor(),minLat,minLon,maxLat,maxLon);
            if(!selected&&!aIn&&!bIn)continue;
            // Do not let a way crossing the local window drag its far-away end into the projection.
            if(!selected&&(!withinLocalProjection(a.getCoor(),center)||!withinLocalProjection(b.getCoor(),center)))continue;
            BlockGeometry.Point pa=local.project(a.getCoor()),pb=local.project(b.getCoor());
            if(Math.max(Math.max(Math.abs(pa.x()),Math.abs(pa.y())),Math.max(Math.abs(pb.x()),Math.abs(pb.y())))>100000){
                buildError="工作区域仍超过100公里；请再放大地图，或勾选‘仅使用选中的道路’。";panel.message(buildError);return;
            }
            segments.add(new BlockGeometry.Segment(new BlockGeometry.Vertex(a.getUniqueId(),pa.x(),pa.y()),new BlockGeometry.Vertex(b.getUniqueId(),pb.x(),pb.y())));
            if(segments.size()>BlockGeometry.MAX_EDGES){buildError="当前视口道路超过40000段，请放大地图或仅使用选中的道路。";panel.message(buildError);return;}
        }}
        if(segments.isEmpty()){buildError="当前工作范围没有可构建的道路线段；请稍微缩小地图或调整道路筛选。";panel.message(buildError);return;}
        panel.message("正在计算当前视口道路围合（"+segments.size()+"段）…");
        worker=new SwingWorker<>(){
            @Override protected BuildResult doInBackground(){return new BuildResult(new BlockGeometry(segments),local);}
            @Override protected void done(){
                if(!active||token!=revision||snapshotData!=MainApplication.getLayerManager().getEditDataSet()||isCancelled())return;
                try{BuildResult r=get();geometry=r.geometry();meters=r.meters();geometryData=snapshotData;worker=null;if(geometry.rejectedRingCount>0){panel.message("拒绝了 "+geometry.rejectedRingCount+" 个过于复杂的闭合环，请缩小范围或按选中道路筛选。");repaint();return;}updatePreview();}
                catch(Exception ex){Logging.error(ex);buildError="围合计算失败："+(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage());panel.message(buildError);worker=null;}
            }
        };worker.execute();
    }
    static boolean inBounds(LatLon c,double minLat,double minLon,double maxLat,double maxLon){return c!=null&&c.lat()>=minLat&&c.lat()<=maxLat&&c.lon()>=minLon&&c.lon()<=maxLon;}
    static boolean withinLocalProjection(LatLon coordinate,LatLon center){return coordinate!=null&&center!=null&&Math.abs(coordinate.lat()-center.lat())<=0.25&&Math.abs(coordinate.lon()-center.lon())<=0.25;}
    private static boolean hasHighway(Way way){String value=way.get("highway");return value!=null&&!value.isBlank();}
    static boolean eligible(Way w,boolean minor){
        if(!w.isUsable()||w.isDisabled()||w.getNodesCount()<2||w.hasTag("area","yes"))return false;
        for(Node n:w.getNodes())if(!n.isUsable()||n.getCoor()==null)return false;
        String highway=w.get("highway");if(!MAIN_ROADS.contains(highway==null?"":highway)||(!minor&&Set.of("service","pedestrian").contains(highway)))return false;
        String layer=w.get("layer");if(layer!=null&&!layer.equals("0"))return false;
        for(String key:List.of("bridge","tunnel")){String value=w.get(key);if(value!=null&&!Set.of("no","false","0").contains(value))return false;}
        return true;
    }
    private void createArea(){
        DataSet current=MainApplication.getLayerManager().getEditDataSet();if(current!=data){invalidateGeometry();return;}
        Map<String,String> tags=new LinkedHashMap<>();
        String generatedName;
        try { generatedName=panel.generatedName(panel.naming.existingNames()); }
        catch(RuntimeException ex){panel.message("名称生成失败："+ex.getMessage());return;}
        if(panel.localMode()){tags.put("blockfill:class",panel.entry().code());tags.put("blockfill:class_name",panel.entry().name());tags.put("blockfill:scheme","CN-2023");tags.put("blockfill:inset_m",Double.toString(panel.insetMeters()));}
        else{tags.putAll(panel.entry().osmTags());if(tags.isEmpty()){panel.message("此类别不能直接对应单一 OSM 标签；请用本地研究模式，或先细分用途。");return;}}
        if(generatedName!=null)tags.put("name",generatedName);
        List<LatLon> coords=candidate.points().stream().map(meters::inverse).toList();
        // Compare full interiors, not only the click position. Never overwrite or overlay an existing mapped area.
        Area intended=new Area(BlockGeometry.path(candidate.points()));
        for(Way existing:data.getWays()){
            if(!existing.isUsable()||!existing.isClosed()||existing.getNodesCount()<4||hasHighway(existing))continue;
            boolean areaTag=existing.hasKey("landuse")||existing.hasKey("building")||existing.hasKey("amenity")||existing.hasKey("leisure")||existing.hasKey("natural")||existing.hasKey("blockfill:class")||existing.hasTag("area","yes");
            if(!areaTag)continue;
            List<BlockGeometry.Point> p=new ArrayList<>();boolean complete=true;for(Node n:existing.getNodes()){if(n.getCoor()==null){complete=false;break;}p.add(meters.project(n.getCoor()));}if(!complete)continue;
            Area overlap=new Area(BlockGeometry.path(p));overlap.intersect(intended);
            if(!overlap.isEmpty()){
                // Buildings and small amenities routinely lie inside landuse. Block landuse footprints, and warn for other areas only in status.
                if(existing.hasKey("landuse")||existing.hasKey("blockfill:class")||existing.hasKey("leisure")||existing.hasKey("natural")){
                    data.setSelected(existing);panel.message("已有用地/绿地/自然面与候选区域重叠，已选中；请核对或分割后再填充。");return;
                }
            }
        }
        for(Relation r:data.getRelations()){
            if(!r.isUsable()||!r.isMultipolygon()||!(r.hasKey("landuse")||r.hasKey("leisure")||r.hasKey("natural")||r.hasKey("blockfill:class")))continue;
            for(RelationMember member:r.getMembers())if(member.isWay()){
                Way w=member.getWay(); for(Node n:w.getNodes())if(n.getCoor()!=null){BlockGeometry.Point p=meters.project(n.getCoor());if(intended.contains(p.x(),p.y())){data.setSelected(r);panel.message("候选范围内存在用地多多边形关系，请先检查；第一版不自动叠加。");return;}}
                if(w.isClosed()){
                    List<BlockGeometry.Point> points=new ArrayList<>();for(Node n:w.getNodes())if(n.getCoor()!=null)points.add(meters.project(n.getCoor()));
                    Area overlap=new Area(BlockGeometry.path(points));overlap.intersect(intended);if(!overlap.isEmpty()){data.setSelected(r);panel.message("候选区域与用地多多边形关系重叠，请先核对。");return;}
                }
            }
        }
        List<Command> commands=new ArrayList<>();List<Node> nodes=new ArrayList<>();
        // Independent nodes intentionally avoid gluing landuse to highway centerlines.
        for(LatLon ll:coords){Node n=new Node(ll);nodes.add(n);commands.add(new AddCommand(data,n));}
        nodes.add(nodes.get(0));Way area=new Way();area.setNodes(nodes);area.setKeys(tags);commands.add(new AddCommand(data,area));
        UndoRedoHandler.getInstance().add(new SequenceCommand("街区填充："+panel.entry().name(),commands));data.setSelected(area);
        panel.nameCreated(generatedName);
        panel.message("已生成 "+panel.entry().name()+(generatedName==null?"":"："+generatedName)+"；Ctrl+Z 撤销，继续点击下一块。");
        candidate=null;sourceFace=null;repaint();
    }
    private void repaint(){if(MainApplication.getMap()!=null)MainApplication.getMap().mapView.repaint();}
    @Override public void paint(Graphics2D graphics,MapView mv,Bounds bounds){
        if(!active||meters==null)return;Graphics2D g=(Graphics2D)graphics.create();
        try{
            if(sourceFace!=null){g.setColor(new Color(80,80,80,180));g.setStroke(new BasicStroke(2,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1,new float[]{5,5},0));g.draw(screen(sourceFace.vertices().stream().map(v->new BlockGeometry.Point(v.x(),v.y())).toList(),mv));}
            if(candidate!=null&&candidate.valid()){
                Color c=panel.entry().color();Path2D.Double p=screen(candidate.points(),mv);g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),95));g.fill(p);g.setColor(c.darker());g.setStroke(new BasicStroke(3));g.draw(p);
            }
        }finally{g.dispose();}
    }
    private Path2D.Double screen(List<BlockGeometry.Point> points,MapView mv){Path2D.Double p=new Path2D.Double();for(int i=0;i<points.size();i++){Point pixel=mv.getPoint(meters.inverse(points.get(i)));if(i==0)p.moveTo(pixel.x,pixel.y);else p.lineTo(pixel.x,pixel.y);}p.closePath();return p;}
    @Override public String getModeHelpText(){return "街区填充：悬停预览、点击生成，1—9 切换用地，Esc 退出；Ctrl+Z 撤销。";}
}
