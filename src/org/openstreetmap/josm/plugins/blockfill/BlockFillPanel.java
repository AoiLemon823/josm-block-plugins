package org.openstreetmap.josm.plugins.blockfill;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.gui.MainApplication;

public final class BlockFillPanel extends ToggleDialog {
    final JComboBox<LanduseCatalog.Entry> category;
    final JComboBox<String> output = new JComboBox<>(new String[]{"OSM 常用标签（推荐）", "本地研究（保存中国分类）"});
    final JSpinner inset = new JSpinner(new SpinnerNumberModel(5.0, 0.0, 100.0, 1.0));
    final JCheckBox minorRoads = new JCheckBox("纳入服务道路、步行街", false);
    final JCheckBox selectedOnly = new JCheckBox("仅使用选中的道路", false);
    final NamingPanel naming = new NamingPanel();
    final JLabel status = new JLabel("点击“开始填充”，再将鼠标移入街区");
    private BlockFillMode mode;
    public BlockFillPanel() {
        super("街区填充", "edit", "道路围合生成面与用地分类", null, 440);
        List<LanduseCatalog.Entry> entries;
        try { entries=LanduseCatalog.load(); } catch(Exception ex) { Logging.error(ex); entries=List.of(new LanduseCatalog.Entry("居住用地", "0701", new Color(0xE8C547), java.util.Map.of("landuse","residential"))); }
        category = new JComboBox<>(entries.toArray(new LanduseCatalog.Entry[0]));
        JPanel settings = new JPanel(new GridBagLayout());
        GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=0;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(4,6,4,6);
        settings.add(new JLabel("用地类别（1—9 切换前九类）"),g);g.gridy++;settings.add(category,g);
        g.gridy++;settings.add(new JLabel("输出模式"),g);g.gridy++;settings.add(output,g);
        JPanel offset=new JPanel(new BorderLayout(6,0));offset.add(new JLabel("向内退界（米）"),BorderLayout.WEST);offset.add(inset,BorderLayout.CENTER);
        g.gridy++;settings.add(offset,g);g.gridy++;settings.add(minorRoads,g);g.gridy++;settings.add(selectedOnly,g);
        JButton refresh=new JButton("刷新道路 / 更新选中范围");refresh.addActionListener(e->{if(mode!=null)mode.invalidateGeometry();});g.gridy++;settings.add(refresh,g);
        JTextArea help=new JTextArea("悬停预览 · 左键生成 · Ctrl+Z 撤销；创建不弹确认框\nOSM 常用标签会直接赋值，请核对分类和标签页内容。\nEsc 返回选择工具。默认退界5米，不纳入桥梁、隧道和非零layer道路。\n退界是近似值，实际地块边界仍需核对。");help.setLineWrap(true);help.setWrapStyleWord(true);help.setEditable(false);help.setOpaque(false);g.gridy++;settings.add(help,g);
        g.gridy++;g.weighty=1;settings.add(Box.createVerticalGlue(),g);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("① 地块与标签",new JScrollPane(settings));tabs.addTab("② 架空名称（OGF）",new JScrollPane(naming));
        add(tabs,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(3,4));JButton start=new JButton("开始填充  Alt+Shift+L");start.addActionListener(e->{if(MainApplication.getMap()!=null)MainApplication.getMap().selectMapMode(mode);});bottom.add(start,BorderLayout.NORTH);bottom.add(status,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
        category.addActionListener(e->{updateNameKind();changed(false);});output.addActionListener(e->changed(false));inset.addChangeListener(e->changed(false));minorRoads.addActionListener(e->changed(true));selectedOnly.addActionListener(e->changed(true));naming.onChanged(()->changed(false));updateNameKind();
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
    void attach(BlockFillMode mode){this.mode=mode;}
    void changed(boolean rebuild){if(mode!=null){if(rebuild)mode.invalidateGeometry();else mode.refreshPreview();}}
    LanduseCatalog.Entry entry(){return (LanduseCatalog.Entry)category.getSelectedItem();}
    boolean localMode(){return output.getSelectedIndex()==1;}
    double insetMeters(){return ((Number)inset.getValue()).doubleValue();}
    void message(String text){status.setText(text);status.setToolTipText(text);}
}
