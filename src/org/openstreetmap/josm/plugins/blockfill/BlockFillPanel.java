package org.openstreetmap.josm.plugins.blockfill;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
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
    final JLabel status = new JLabel("点击“进入街区填充模式”，再将鼠标移入街区");
    final JButton modeButton = new JButton("进入街区填充模式");
    private BlockFillMode mode;
    private boolean modeActive;
    public BlockFillPanel() {
        super("街区填充", "edit", "道路围合生成面与用地分类", null, 440);
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
        settings.add(hint("用地类别（1—9 切换前九类）","1—9 快速切换前九类。创建时把所选分类对应的 OSM 常用标签直接写入地块，请核对分类与标签页内容。"),g);g.gridy++;settings.add(category,g);
        g.gridy++;settings.add(hint("输出模式","OSM 常用标签（推荐）直接赋值标准标签；本地研究模式只保存中国分类代码，仅供本地研究，不上传 OSM。"),g);g.gridy++;settings.add(output,g);
        JPanel offset=new JPanel(new BorderLayout(6,0));offset.add(hint("向内退界（米）","默认退界 5 米，为近似值，实际地块边界仍需核对。"),BorderLayout.WEST);offset.add(inset,BorderLayout.CENTER);
        JPanel chamferRow=new JPanel(new BorderLayout(6,0));chamferRow.add(hint("切角距离（米）","路口切角只作用于 highway 交点上的凸拐角，沿两侧道路边界各切此距离；距离超出相邻道路长度时会自动收敛，切角距离按生成后的地块边界量取。"),BorderLayout.WEST);chamferRow.add(chamfer,BorderLayout.CENTER);
        chamferEnabled.setToolTipText("只作用于 highway 交点上的凸拐角，沿两侧道路边界各切设定距离。");
        minorRoads.setToolTipText("默认不纳入桥梁、隧道和非零 layer 道路；勾选后额外纳入服务道路、步行街等次要道路参与围合。");
        selectedOnly.setToolTipText("只使用当前选中的道路围合地块，忽略其他道路。");
        g.gridy++;settings.add(offset,g);g.gridy++;settings.add(chamferEnabled,g);g.gridy++;settings.add(chamferRow,g);g.gridy++;settings.add(minorRoads,g);g.gridy++;settings.add(selectedOnly,g);
        JButton refresh=new JButton("刷新道路 / 更新选中范围");refresh.addActionListener(e->{if(mode!=null)mode.invalidateGeometry();});g.gridy++;settings.add(refresh,g);
        g.gridy++;g.weighty=1;settings.add(Box.createVerticalGlue(),g);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("① 地块与标签",new JScrollPane(settings));tabs.addTab("② 架空名称（OGF）",new JScrollPane(naming));
        JPanel bottom=new JPanel(new BorderLayout(3,4));modeButton.addActionListener(e->toggleMode());
        bottom.add(modeButton,BorderLayout.NORTH);bottom.add(status,BorderLayout.SOUTH);
        JPanel body=new JPanel(new BorderLayout());body.add(tabs,BorderLayout.CENTER);body.add(bottom,BorderLayout.SOUTH);
        createLayout(body,false,null);
        category.addActionListener(e->{updateNameKind();changed(false);});output.addActionListener(e->changed(false));inset.addChangeListener(e->changed(false));minorRoads.addActionListener(e->changed(true));selectedOnly.addActionListener(e->changed(true));naming.onChanged(()->changed(false));updateNameKind();
        chamfer.setToolTipText("沿路口两条道路边界各切除的距离，仅在 highway 交点上的凸拐角生效。");
        chamfer.setEnabled(chamferEnabled.isSelected());
        chamferEnabled.addActionListener(e->{chamfer.setEnabled(chamferEnabled.isSelected());changed(false);});
        chamfer.addChangeListener(e->changed(false));
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
    void attach(BlockFillMode mode){this.mode=mode;updateModeButton();}
    void modeActiveChanged(boolean active){modeActive=active;updateModeButton();}
    @Override public void showNotify(){updateModeButton();}
    private void toggleMode(){
        MapFrame map=MainApplication.getMap();
        if(map==null)return;
        if(modeActive)map.selectSelectTool(false);
        else if(mode!=null)map.selectMapMode(mode);
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
}
