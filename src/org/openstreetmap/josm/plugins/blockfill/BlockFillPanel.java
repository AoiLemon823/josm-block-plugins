package org.openstreetmap.josm.plugins.blockfill;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.gui.MainApplication;

public final class BlockFillPanel extends ToggleDialog {
    final JComboBox<LanduseCatalog.Entry> category;
    final JComboBox<String> output = new JComboBox<>(new String[]{"OSM 常用标签（推荐）", "本地研究（保存中国分类）"});
    final JSpinner inset = new JSpinner(new SpinnerNumberModel(5.0, 0.0, 100.0, 1.0));
    final JCheckBox chamferEnabled = new JCheckBox("路口（highway 交点）切角", false);
    final JSpinner chamfer = new JSpinner(new SpinnerNumberModel(6.0, 0.0, 50.0, 1.0));
    final JCheckBox minorRoads = new JCheckBox("纳入服务道路、步行街", false);
    final JCheckBox selectedOnly = new JCheckBox("仅使用选中的道路", false);
    final NamingPanel naming = new NamingPanel();
    // ── POI 生成控件 ────────────────────────────────────────────────────────
    /** 场景选择：oldtown/village/street/mall 多选框组 */
    final JCheckBox poiSceneOldtown  = new JCheckBox("老城区", false);
    final JCheckBox poiSceneVillage  = new JCheckBox("城中村", false);
    final JCheckBox poiSceneStreet   = new JCheckBox("街边商业", true);
    final JCheckBox poiSceneMall     = new JCheckBox("综合体", false);
    /** 每类 POI 数量上限 */
    final JSpinner  poiCountPerType  = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
    /** 随机种子 */
    final JSpinner  poiSeed          = new JSpinner(new SpinnerNumberModel(42, 0, 999999, 1));
    /** 生成按钮（触发 mode.generatePoi()） */
    final JButton   poiGenerate      = new JButton("生成 POI（需先选中建筑 way）");
    /** 类型多选列表（从 PoiNameGenerator.availableTypes() 填充） */
    final JList<String> poiTypeList  = new JList<>(new DefaultListModel<>());
    final JLabel status = new StatusLabel("点击“进入街区填充模式”，再将鼠标移入绘图区");
    final JButton modeButton = new JButton("进入街区填充模式");
    private BlockFillMode mode;
    private boolean modeActive;
    private final AWTEventListener shortcutShield=this::shieldShortcuts;
    private final LayerManager.LayerChangeListener layerListener=new LayerManager.LayerChangeListener(){
        @Override public void layerAdded(LayerManager.LayerAddEvent e){refreshLayerAvailability();}
        @Override public void layerRemoving(LayerManager.LayerRemoveEvent e){SwingUtilities.invokeLater(BlockFillPanel.this::refreshLayerAvailability);}
        @Override public void layerOrderChanged(LayerManager.LayerOrderChangeEvent e){refreshLayerAvailability();}
    };
    private final MainLayerManager.ActiveLayerChangeListener activeLayerListener=e->refreshLayerAvailability();
    private boolean layerListenersAttached;
    private boolean layerHintShown;
    public BlockFillPanel() {
        super("街区填充", "edit", "在道路围合区域生成用地面之面板", null, 440);
        // ToggleDialog keys visibility and collapse state by icon name; use a plugin-specific prefix
        // so several plugin panels do not share (and corrupt) each other's dock/minimized state.
        preferencePrefix="blockfill";
        isShowing=Config.getPref().getBoolean(preferencePrefix+".visible",false);
        isDocked=Config.getPref().getBoolean(preferencePrefix+".docked",true);
        isCollapsed=Config.getPref().getBoolean(preferencePrefix+".minimized",false);
        List<LanduseCatalog.Entry> entries;
        try { entries=LanduseCatalog.load(); } catch(Exception ex) { Logging.error(ex); entries=List.of(new LanduseCatalog.Entry("居住用地", "0701", new Color(0xE8C547), java.util.Map.of("landuse","residential"))); }
        category = new JComboBox<>(entries.toArray(new LanduseCatalog.Entry[0]));
        JPanel settings = new JPanel(new GridBagLayout());
        GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=0;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(4,6,4,6);
        settings.add(hint("用地类别（数字键 1—9 快速切换前九类）","创建地块时将同时写入下方类别对应的用地标签，可使用数字键 1—9 快速切换前九类。"),g);g.gridy++;settings.add(category,g);
        g.gridy++;settings.add(hint("输出模式","OSM 常用标签（推荐）：直接赋值标准标签；本地研究模式：仅保存中国分类代码供本地测试、研究使用，不推荐上传。"),g);g.gridy++;settings.add(output,g);
        JPanel offset=new JPanel(new BorderLayout(6,0));offset.add(hint("向内退界（米）","用地地块从道路中心线起退缩的距离，默认 5 米。"),BorderLayout.WEST);offset.add(inset,BorderLayout.CENTER);
        JPanel chamferRow=new JPanel(new BorderLayout(6,0));chamferRow.add(hint("切角距离（米）","在 highway 交点额外进行拐角的距离；距离超出相邻道路长度时会自动收敛，切角距离按生成后的地块边界量取。"),BorderLayout.WEST);chamferRow.add(chamfer,BorderLayout.CENTER);
        chamferEnabled.setToolTipText("在 highway 交点额外进行拐角，启用后将沿两侧道路边界各切设定距离。");
        minorRoads.setToolTipText("用于围合之道路默认不包含服务道路、步行街等次要道路及桥梁、隧道等各种非零 layer 道路；勾选以使之参与围合。");
        selectedOnly.setToolTipText("只使用当前选中的道路计算围合地块，忽略其他道路。");
        g.gridy++;settings.add(offset,g);g.gridy++;settings.add(chamferEnabled,g);g.gridy++;settings.add(chamferRow,g);g.gridy++;settings.add(minorRoads,g);g.gridy++;settings.add(selectedOnly,g);
        JButton refresh=new JButton("使用新的选择范围重算道路与街区");refresh.addActionListener(e->{if(mode!=null)mode.invalidateGeometry();});g.gridy++;settings.add(refresh,g);
        g.gridy++;g.weighty=1;settings.add(Box.createVerticalGlue(),g);
        // ── POI 页签 ──────────────────────────────────────────────────────────
        JPanel poiPanel = new JPanel(new GridBagLayout());
        poiPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8,8,8,8));
        GridBagConstraints gp = new GridBagConstraints();
        gp.gridx=0; gp.gridy=0; gp.gridwidth=2; gp.weightx=1; gp.fill=GridBagConstraints.HORIZONTAL; gp.insets=new Insets(3,2,3,2);
        poiPanel.add(new JLabel("<html><b>场景</b>（决定词风格，可多选）</html>"), gp);
        gp.gridy++; gp.gridwidth=1; gp.weightx=0.5;
        poiPanel.add(poiSceneOldtown, gp); gp.gridx=1; poiPanel.add(poiSceneVillage, gp);
        gp.gridy++; gp.gridx=0; poiPanel.add(poiSceneStreet, gp); gp.gridx=1; poiPanel.add(poiSceneMall, gp);
        gp.gridy++; gp.gridx=0; gp.gridwidth=2; gp.weightx=1;
        poiPanel.add(new JLabel("<html><b>POI 类型</b>（Ctrl/Shift 多选）</html>"), gp);
        gp.gridy++;
        poiTypeList.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        poiTypeList.setVisibleRowCount(8);
        poiPanel.add(new JScrollPane(poiTypeList), gp);
        gp.gridy++;
        JPanel poiCount = new JPanel(new BorderLayout(4,0));
        poiCount.add(new JLabel("每类最多生成"), BorderLayout.WEST);
        poiCount.add(poiCountPerType, BorderLayout.CENTER);
        poiCount.add(new JLabel("个"), BorderLayout.EAST);
        poiPanel.add(poiCount, gp);
        gp.gridy++;
        JPanel poiSeedRow = new JPanel(new BorderLayout(4,0));
        poiSeedRow.add(new JLabel("随机种子"), BorderLayout.WEST);
        poiSeedRow.add(poiSeed, BorderLayout.CENTER);
        poiPanel.add(poiSeedRow, gp);
        gp.gridy++; poiPanel.add(poiGenerate, gp);
        gp.gridy++; gp.weighty=0;
        JTextArea poiHelp = new JTextArea("选中一个或多个闭合建筑 way，选择场景和类型，点击生成。\n生成的虚构 POI 仅用于 OpenGeofiction 等架空地图。\n生成可 Ctrl+Z 一次撤销。");
        poiHelp.setLineWrap(true); poiHelp.setWrapStyleWord(true); poiHelp.setEditable(false); poiHelp.setOpaque(false);
        poiPanel.add(poiHelp, gp);
        // 场景变化时刷新类型列表
        java.awt.event.ActionListener sceneRefresh = e -> refreshPoiTypes();
        poiSceneOldtown.addActionListener(sceneRefresh); poiSceneVillage.addActionListener(sceneRefresh);
        poiSceneStreet.addActionListener(sceneRefresh);  poiSceneMall.addActionListener(sceneRefresh);
        refreshPoiTypes();
        // 绑定生成按钮
        poiGenerate.addActionListener(e -> { if (mode != null) mode.generatePoi(); });
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("① 地块与标签",new JScrollPane(settings));tabs.addTab("② 架空名称（OGF）",naming);tabs.addTab("③ POI 生成",new JScrollPane(poiPanel));
        JPanel bottom=new JPanel(new BorderLayout(3,4));modeButton.addActionListener(e->toggleMode());
        bottom.add(modeButton,BorderLayout.NORTH);bottom.add(status,BorderLayout.SOUTH);
        JPanel body=new JPanel(new BorderLayout());body.add(tabs,BorderLayout.CENTER);body.add(bottom,BorderLayout.SOUTH);
        createLayout(body,false,null);
        category.addActionListener(e->{updateNameKind();changed(false);});output.addActionListener(e->changed(false));inset.addChangeListener(e->changed(false));minorRoads.addActionListener(e->changed(true));selectedOnly.addActionListener(e->changed(true));naming.onChanged(()->changed(false));updateNameKind();
        chamfer.setToolTipText("沿路口两条道路边界各切除的距离，仅在 highway 交点上的凸拐角生效。");
        chamfer.setEnabled(chamferEnabled.isSelected());
        chamferEnabled.addActionListener(e->{chamfer.setEnabled(chamferEnabled.isSelected());changed(false);});
        chamfer.addChangeListener(e->changed(false));
        Toolkit.getDefaultToolkit().addAWTEventListener(shortcutShield,AWTEvent.KEY_EVENT_MASK);
        attachLayerListeners();refreshLayerAvailability();
    }
    /**
     * JOSM registers global shortcuts on the main window, so typing letters into a docked text field can
     * trigger map modes (for example "a" for drawing). While focus is inside this panel's text fields,
     * keep bindings owned by the focus owner itself (editing, navigation, clipboard) and consume the rest.
     */
    private void shieldShortcuts(AWTEvent event){
        if(event.getID()!=KeyEvent.KEY_PRESSED)return;
        KeyEvent key=(KeyEvent)event;
        Component focus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if(!(focus instanceof javax.swing.text.JTextComponent target)||!SwingUtilities.isDescendingFrom(target,this))return;
        if(key.getKeyCode()==KeyEvent.VK_TAB)return;
        if(target.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStrokeForEvent(key))!=null)return;
        key.consume();
    }
    @Override public void destroy(){
        Toolkit.getDefaultToolkit().removeAWTEventListener(shortcutShield);
        MainLayerManager layers=MainApplication.getLayerManager();
        if(layers!=null&&layerListenersAttached){layers.removeLayerChangeListener(layerListener);layers.removeActiveLayerChangeListener(activeLayerListener);layerListenersAttached=false;}
        super.destroy();
    }
    private void attachLayerListeners(){
        MainLayerManager layers=MainApplication.getLayerManager();
        if(layers==null||layerListenersAttached)return;
        layers.addLayerChangeListener(layerListener);
        layers.addActiveLayerChangeListener(activeLayerListener);
        layerListenersAttached=true;
    }
    /** Requires the active layer itself to be an editable OSM data layer, not merely a remembered edit layer. */
    static boolean hasEditableLayer(){
        MainLayerManager layers=MainApplication.getLayerManager();
        if(layers==null)return false;
        Layer active=layers.getActiveLayer();
        return active instanceof OsmDataLayer data&&!data.isLocked()&&!data.isUploadInProgress();
    }
    /** The fill mode requires an editable OSM data layer; otherwise disable the toggle and explain why. */
    private void refreshLayerAvailability(){
        boolean editable=hasEditableLayer();
        modeButton.setEnabled(editable);
        poiGenerate.setEnabled(editable);
        modeButton.setToolTipText(editable?null:"请新增或切换到可编辑的 OSM 数据图层");
        if(!editable){
            status.setText("请新增或切换到可编辑的 OSM 数据图层后再进入街区填充模式。");
            layerHintShown=true;
        }else if(layerHintShown){
            layerHintShown=false;
            if(!modeActive)status.setText("点击“进入街区填充模式”，再将鼠标移入绘图区");
        }
    }
    private void updateNameKind(){
        String code=entry()==null?"":entry().code();
        // Community service facilities and office districts are not residential/mall naming targets.
        boolean residential=code.equals("0701")||code.equals("0703");
        boolean commercial=code.equals("0901")||code.equals("090101");
        boolean business=code.equals("0902")||code.equals("090201");
        naming.setKind(residential?PlaceNameGenerator.Kind.RESIDENTIAL:commercial?PlaceNameGenerator.Kind.COMMERCIAL:business?PlaceNameGenerator.Kind.BUSINESS:PlaceNameGenerator.Kind.OTHER);
    }
    String generatedName(java.util.Collection<String> existing){return naming.nameForCreation(existing);}
    void nameCreated(String name){naming.creationSucceeded(name);}
    void attach(BlockFillMode mode){this.mode=mode;updateModeButton();refreshLayerAvailability();}
    void modeActiveChanged(boolean active){modeActive=active;updateModeButton();}
    @Override public void showNotify(){updateModeButton();refreshLayerAvailability();}
    private void toggleMode(){
        MapFrame map=MainApplication.getMap();
        if(map==null)return;
        if(modeActive){map.selectSelectTool(false);return;}
        if(!hasEditableLayer()){refreshLayerAvailability();return;}
        if(mode!=null)map.selectMapMode(mode);
    }
    private void updateModeButton(){
        if(modeActive){modeButton.setText("退出街区填充模式（Esc）");return;}
        String key="";
        if(mode!=null&&mode.getShortcut()!=null){
            try{key=mode.getShortcut().getKeyText();}catch(RuntimeException ignored){ }
        }
        modeButton.setText(key==null||key.isEmpty()?"进入街区填充模式":"进入街区填充模式（"+key+"）");
    }
    void changed(boolean rebuild){if(mode!=null){if(rebuild)mode.invalidateGeometry();else mode.refreshPreview();}}
    LanduseCatalog.Entry entry(){return (LanduseCatalog.Entry)category.getSelectedItem();}
    boolean localMode(){return output.getSelectedIndex()==1;}
    double insetMeters(){return ((Number)inset.getValue()).doubleValue();}
    double chamferMeters(){return chamferEnabled.isSelected()?((Number)chamfer.getValue()).doubleValue():0;}
    void message(String text){status.setText(text);status.setToolTipText(text);}
    private static JLabel hint(String text,String tip){
        JLabel label=new JLabel(text);
        label.setToolTipText("<html><body style='width:240px'>"+tip+"</body></html>");
        return label;
    }
    /** Status label that wraps long messages to the current panel width instead of truncating with an ellipsis. */
    private static final class StatusLabel extends JLabel {
        private String raw;
        private int wrapWidth;
        StatusLabel(String text){super();setRaw(text);}
        @Override public void setText(String text){setRaw(text);}
        private void setRaw(String text){
            raw=text==null?"":text;
            setToolTipText(raw);
            applyWrap();
        }
        @Override public void setBounds(int x,int y,int w,int h){
            if(w>20&&w!=wrapWidth){wrapWidth=w;applyWrap();}
            super.setBounds(x,y,w,h);
        }
        private void applyWrap(){
            // Swing HTML treats CSS px as points, so scale down to fit the label width.
            int css=Math.max(90,(wrapWidth>0?wrapWidth:240)*3/4);
            super.setText("<html><body style='width:"+css+"px'>"+escape(raw)+"</body></html>");
        }
        private static String escape(String text){
            return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>");
        }
    }

    void refreshPoiTypes() {
        java.util.Set<PoiNameGenerator.Scene> scenes = poiScenes();
        if (scenes.isEmpty()) { ((DefaultListModel<String>) poiTypeList.getModel()).clear(); return; }
        PoiNameGenerator gen = new PoiNameGenerator(scenes, 0L);
        java.util.List<String> types = new java.util.ArrayList<>(gen.availableTypes());
        java.util.Collections.sort(types);
        DefaultListModel<String> model = (DefaultListModel<String>) poiTypeList.getModel();
        model.clear();
        for (String t : types) model.addElement(t);
        if (model.size() > 0) poiTypeList.setSelectionInterval(0, model.size() - 1);
    }

    java.util.Set<PoiNameGenerator.Scene> poiScenes() {
        java.util.Set<PoiNameGenerator.Scene> s = new java.util.LinkedHashSet<>();
        if (poiSceneOldtown.isSelected()) s.add(PoiNameGenerator.Scene.OLDTOWN);
        if (poiSceneVillage.isSelected())  s.add(PoiNameGenerator.Scene.VILLAGE);
        if (poiSceneStreet.isSelected())   s.add(PoiNameGenerator.Scene.STREET);
        if (poiSceneMall.isSelected())     s.add(PoiNameGenerator.Scene.MALL);
        return s;
    }

    java.util.List<String> selectedPoiTypes() { return poiTypeList.getSelectedValuesList(); }
    int poiCountPerType() { return ((Number) poiCountPerType.getValue()).intValue(); }
    long poiSeed() { return ((Number) poiSeed.getValue()).longValue(); }
}
