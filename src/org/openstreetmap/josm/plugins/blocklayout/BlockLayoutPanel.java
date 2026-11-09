package org.openstreetmap.josm.plugins.blocklayout;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseWheelEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeListener;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.spi.preferences.Config;

/** Independent settings panel for the residential building layout plugin. */
public final class BlockLayoutPanel extends ToggleDialog {
    private static final String PREFIX=System.getProperty("blocklayout.testPreferencePrefix","blocklayout.");
    private static final Map<String,LayoutSettings> PRESETS=presets();
    final JComboBox<String> preset=new JComboBox<>(new String[]{"自定义 / 上次参数","多层住宅小区","中高层住宅","高层住宅"});
    final JSpinner width=number(12,1,500,1),length=number(48,1,500,1),height=number(18,1,1000,1);
    final JSpinner endSetback=number(6,0,10000,1),sideClearance=number(12,0,10000,1),rowSpacing=number(24,0,10000,1);
    final JCheckBox sunSpacing=new JCheckBox("按高度×日照距离系数增大排距",false);
    final JSpinner sunFactor=number(1.5,0,100,0.1);
    final JCheckBox staggered=new JCheckBox("隔行错列",false);
    final JComboBox<String> buildingTag=new JComboBox<>(new String[]{"building=apartments","building=residential","building=yes"});
    final JCheckBox useSample=new JCheckBox("使用所选建筑作为样板轮廓",false),communityShops=new JCheckBox("生成社区底商围合",false),factoryMode=new JCheckBox("生成工业厂房",false);
    final JCheckBox factoryRoads=new JCheckBox("同时生成工业 service 通道",false);
    final JSpinner factoryRoadWidth=number(6,3,20,0.5),factoryRoadClearance=number(3,0,30,0.5);
    final javax.swing.JTabbedPane tabs=new javax.swing.JTabbedPane();
    final JSpinner shopSetback=number(6,0,100,0.5),shopDepth=number(8,1,100,0.5),shopHeight=number(4,1,100,0.5),shopRoadClearance=number(2,0,100,0.5);
    final JSpinner factoryMinWidth=number(12,5,100,1),factoryMaxWidth=number(24,5,150,1),factoryMinLength=number(30,10,200,1),factoryMaxLength=number(60,10,300,1),factoryFloorHeight=number(6,3,20,0.5),factoryMinLevels=number(1,1,12,1),factoryMaxLevels=number(2,1,12,1),factorySetback=number(8,0,100,1),factoryGap=number(8,0,100,1),factoryRowGap=number(15,0,100,1),factorySeed=number(42,0,1000000,1);
    final JSpinner factoryGroupSize=number(3,0,10,1);
    final JComboBox<String> chamfer=new JComboBox<>(new String[]{"30°端部倒角","45°端部倒角"});
    final JCheckBox streetTreeMode=new JCheckBox("生成行道树",false);
    final JComboBox<String> treeMode=new JComboBox<>(new String[]{"地块内service道路","选中道路"});
    final JSpinner treeSpacing=number(6.0,4.0,20.0,0.5),treeOffset=number(2.0,0,20.0,0.5);
    final JCheckBox treeDoubleSide=new JCheckBox("道路双侧种植",true);
    final JButton pickEdge=new JButton("拾取地块边方向"),resetEdge=new JButton("恢复默认方向");
    final JLabel directionStatus=new JLabel("排列方向：默认地图轴向");
    final JLabel status=new JLabel("选择闭合地块后开始预览。"),selectionHint=new JLabel("地块输入：只选择闭合地块 way。样板模式再额外选择一个 building way。");
    private BlockLayoutMode mode;
    private boolean loading;
    private boolean devMode=java.lang.Boolean.getBoolean("blocklayout.dev");
    private String activePreset="自定义 / 上次参数";

    public BlockLayoutPanel() {
        super("建筑排布","edit","在选中的闭合地块内预览住宅楼、底商和厂房布局",null,360);
        JPanel housing=new JPanel(new GridBagLayout());housing.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        int row=0;addRow(housing,row++,"住宅预设",preset);addRow(housing,row++,"建筑短边宽度（米）",width);addRow(housing,row++,"建筑长边长度（米）",length);addRow(housing,row++,"建筑高度（米）",height);
        addRow(housing,row++,"建筑端部退距（米）",endSetback);addRow(housing,row++,"同排楼栋侧向净距（米）",sideClearance);addRow(housing,row++,"最小排间距（米）",rowSpacing);addRow(housing,row++,"日照距离系数",sunFactor);addRow(housing,row++,"生成标签",buildingTag);
        addFullRow(housing,row++,sunSpacing);addFullRow(housing,row++,staggered);addFullRow(housing,row++,useSample);
        JPanel shops=new JPanel(new GridBagLayout());shops.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));row=0;
        addFullRow(shops,row++,communityShops);addRow(shops,row++,"底商沿街后退（米）",shopSetback);addRow(shops,row++,"底商进深（米）",shopDepth);addRow(shops,row++,"底商高度（米）",shopHeight);addRow(shops,row++,"底商到道路退距（米）",shopRoadClearance);addRow(shops,row++,"端部倒角",chamfer);
        JPanel factories=new JPanel(new GridBagLayout());factories.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));row=0;
        addFullRow(factories,row++,factoryMode);if(devMode){addFullRow(factories,row++,factoryRoads);addRow(factories,row++,"道路宽度（米）",factoryRoadWidth);addRow(factories,row++,"道路建筑退距（米）",factoryRoadClearance);}
        addRow(factories,row++,"厂房最小短边（米）",factoryMinWidth);addRow(factories,row++,"厂房最大短边（米）",factoryMaxWidth);addRow(factories,row++,"厂房最小长边（米）",factoryMinLength);addRow(factories,row++,"厂房最大长边（米）",factoryMaxLength);addRow(factories,row++,"标准层高（米）",factoryFloorHeight);addRow(factories,row++,"最少层数",factoryMinLevels);addRow(factories,row++,"最多层数",factoryMaxLevels);addRow(factories,row++,"地块退界（米）",factorySetback);addRow(factories,row++,"同排间隔（米）",factoryGap);addRow(factories,row++,"排间间隔（米）",factoryRowGap);addRow(factories,row++,"联排最大栋数（0关闭）",factoryGroupSize);addRow(factories,row++,"随机种子",factorySeed);
        JPanel trees=devMode?new JPanel(new GridBagLayout()):null;if(trees!=null){trees.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));row=0;
        addFullRow(trees,row++,streetTreeMode);addRow(trees,row++,"生成模式",treeMode);addRow(trees,row++,"行道树间距(米)",treeSpacing);addRow(trees,row++,"距道路边偏移(米)",treeOffset);addFullRow(trees,row++,treeDoubleSide);}
        tabs.addTab("住宅",housing);tabs.addTab("底商",shops);tabs.addTab("工业",factories);if(devMode&&trees!=null)tabs.addTab("行道树",trees);
        JPanel direction=new JPanel(new GridBagLayout());direction.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));row=0;addFullRow(direction,row++,pickEdge);addFullRow(direction,row++,resetEdge);addFullRow(direction,row++,directionStatus);addFullRow(direction,row++,selectionHint);
        JButton previewButton=new JButton("开始排布预览");previewButton.addActionListener(e->{try{if(mode!=null)mode.startPreview();}catch(RuntimeException ex){message(ex.getMessage());}});
        addFullRow(direction,row++,previewButton);
        JTextArea note=new JTextArea("住宅三类预设和自定义分别记忆修改值。厂房联排按同尺寸矩形组生成，彼此保留同排间隔；0关闭，1仅单栋，2以上随机选择组内栋数。组团优先使用长边范围上段。改变随机种子可更换布局。拾取地块边可确定排列方向。\n\n左键确认一次生成，Ctrl+Z一次撤销；建筑需完整位于地块内。厂房尺寸范围是概化外轮廓，不等同于结构轴线模数，也不是消防或规划合规审查。");
        note.setLineWrap(true);note.setWrapStyleWord(true);note.setEditable(false);note.setOpaque(false);
        JPanel content=scrollContentPanel();content.add(tabs,BorderLayout.NORTH);content.add(direction,BorderLayout.CENTER);content.add(note,BorderLayout.SOUTH);
        JScrollPane scroll=new JScrollPane(content,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setViewportBorder(BorderFactory.createEmptyBorder(0,0,0,3));scroll.getVerticalScrollBar().setUnitIncrement(18);add(scroll,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(4,4));footer.setBorder(BorderFactory.createEmptyBorder(3,4,3,4));footer.add(status,BorderLayout.CENTER);add(footer,BorderLayout.SOUTH);
        ChangeListener changed=e->{if(!loading)settingsChanged();};
        for(JSpinner spinner:new JSpinner[]{width,length,height,endSetback,sideClearance,rowSpacing,sunFactor,shopSetback,shopDepth,shopHeight,shopRoadClearance,factoryRoadWidth,factoryRoadClearance,factoryMinWidth,factoryMaxWidth,factoryMinLength,factoryMaxLength,factoryFloorHeight,factoryMinLevels,factoryMaxLevels,factorySetback,factoryGap,factoryRowGap,factorySeed,factoryGroupSize}){
            spinner.addChangeListener(changed);
            spinner.addMouseWheelListener(e->wheelAdjust(spinner,e));
            spinner.getEditor().addMouseWheelListener(e->wheelAdjust(spinner,e));
        }
        sunSpacing.addActionListener(e->settingsChanged());staggered.addActionListener(e->settingsChanged());buildingTag.addActionListener(e->settingsChanged());useSample.addActionListener(e->{selectionHint.setText(useSample.isSelected()?"地块输入：选择闭合地块 way，并额外选择一个闭合 building way 作为样板。":"地块输入：只选择闭合地块 way。样板模式再额外选择一个 building way。");settingsChanged();});
        preset.addActionListener(e->{if(!loading)applyPreset((String)preset.getSelectedItem());});
        communityShops.addActionListener(e->{useSample.setSelected(false);factoryMode.setSelected(false);useSample.setEnabled(!communityShops.isSelected());settingsChanged();});
        useSample.addActionListener(e->{if(useSample.isSelected()){communityShops.setSelected(false);factoryMode.setSelected(false);}communityShops.setEnabled(!useSample.isSelected());selectionHint.setText(useSample.isSelected()?"地块输入：选择闭合地块 way，并额外选择一个闭合 building way 作为样板。":"地块输入：只选择闭合地块 way。样板模式再额外选择一个 building way。");settingsChanged();});
        factoryMode.addActionListener(e->{if(factoryMode.isSelected()){communityShops.setSelected(false);useSample.setSelected(false);tabs.setSelectedIndex(2);selectionHint.setText("选择工业用地闭合地块；厂房尺寸按范围随机变化，方向可用拾取地块边设置。");}settingsChanged();});
        communityShops.addActionListener(e->{if(communityShops.isSelected())tabs.setSelectedIndex(1);});
        factoryRoads.addActionListener(e->settingsChanged());
        streetTreeMode.addActionListener(e->{if(streetTreeMode.isSelected()){communityShops.setSelected(false);useSample.setSelected(false);factoryMode.setSelected(false);tabs.setSelectedIndex(3);selectionHint.setText(treeMode.getSelectedIndex()==0?"选择闭合地块;插件将沿地块内service道路生成行道树。":"选择一条highway道路(非snservice);插件将沿道路生成行道树。");}settingsChanged();});
        treeMode.addActionListener(e->{if(streetTreeMode.isSelected()){selectionHint.setText(treeMode.getSelectedIndex()==0?"选择闭合地块;插件将沿地块内service道路生成行道树。":"选择一条highway道路(非snservice);插件将沿道路生成行道树。");}settingsChanged();});
        treeDoubleSide.addActionListener(e->settingsChanged());
        pickEdge.addActionListener(e->{if(mode!=null)mode.beginDirectionPick();});resetEdge.addActionListener(e->{if(mode!=null)mode.resetDirection();});
        loadLastSettings();
    }

    private static Map<String,LayoutSettings> presets(){
        Map<String,LayoutSettings> m=new LinkedHashMap<>();
        m.put("多层住宅小区",new LayoutSettings(12,48,18,6,12,24,false,1.5,false,"apartments"));
        m.put("中高层住宅",new LayoutSettings(14,52,36,8,14,36,true,1.5,true,"apartments"));
        m.put("高层住宅",new LayoutSettings(16,60,72,10,18,48,true,1.5,true,"apartments"));
        return Map.copyOf(m);
    }
    private static void wheelAdjust(JSpinner spinner,MouseWheelEvent event){
        if(!spinner.isEnabled())return;double value=((Number)spinner.getValue()).doubleValue();double step=((SpinnerNumberModel)spinner.getModel()).getStepSize().doubleValue();double next=value-event.getPreciseWheelRotation()*step;
        SpinnerNumberModel model=(SpinnerNumberModel)spinner.getModel();if(!(model.getMinimum() instanceof Number minValue)||!(model.getMaximum() instanceof Number maxValue))return;double min=minValue.doubleValue(),max=maxValue.doubleValue();
        try{spinner.setValue(Math.max(min,Math.min(max,next)));event.consume();}catch(IllegalArgumentException ignored){ }
    }
    private static JSpinner number(double v,double min,double max,double step){return new JSpinner(new SpinnerNumberModel(v,min,max,step));}
    private static JPanel scrollContentPanel(){return new JPanel(new BorderLayout(4,6)){
        @Override public java.awt.Dimension getPreferredSize(){java.awt.Dimension d=super.getPreferredSize();java.awt.Container parent=getParent();if(parent instanceof javax.swing.JViewport viewport&&viewport.getWidth()>0)d.width=viewport.getWidth();return d;}
    };}
    private static void addRow(JPanel p,int row,String label,java.awt.Component c){
        GridBagConstraints g=new GridBagConstraints();g.gridy=row;g.insets=new Insets(3,2,3,6);g.anchor=GridBagConstraints.LINE_START;
        g.gridx=0;g.weightx=0.48;g.fill=GridBagConstraints.HORIZONTAL;g.gridwidth=1;JLabel text=new JLabel("<html>"+label+"</html>");text.setMinimumSize(new java.awt.Dimension(0,text.getPreferredSize().height));p.add(text,g);
        g.gridx=1;g.weightx=0.52;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(3,2,3,2);p.add(c,g);
    }
    private static JPanel implementsScrollable(){return new JPanel(new BorderLayout(4,6)){
        @Override public java.awt.Dimension getPreferredSize(){java.awt.Dimension d=super.getPreferredSize();java.awt.Container parent=getParent();if(parent instanceof javax.swing.JViewport viewport)d.width=viewport.getWidth();return d;}
    };}
    private static void addFullRow(JPanel p,int row,java.awt.Component c){GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=row;g.gridwidth=2;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.anchor=GridBagConstraints.LINE_START;g.insets=new Insets(3,2,3,2);p.add(c,g);}
    void attach(BlockLayoutMode m){mode=m;}
    private void applyPreset(String name){
        if(name==null)return;
        LayoutSettings fallback=PRESETS.getOrDefault(name,new LayoutSettings(12,48,18,6,12,24,false,1.5,false,"apartments"));
        LayoutSettings s=readPreset(name,fallback);activePreset=name;
        loading=true;try{set(width,s.width());set(length,s.length());set(height,s.height());set(endSetback,s.endSetback());set(sideClearance,s.sideClearance());set(rowSpacing,s.rowSpacing());set(sunFactor,s.sunFactor());sunSpacing.setSelected(s.sunSpacing());staggered.setSelected(s.staggered());buildingTag.setSelectedIndex(switch(s.buildingTag()){case "residential"->1;case "yes"->2;default->0;});}
        finally{loading=false;}if(mode!=null)mode.settingsChanged();
        Config.getPref().put(PREFIX+"activePreset",activePreset);
    }
    private void loadLastSettings(){
        try{loading=true;set(width,get("width",12));set(length,get("length",48));set(height,get("height",18));set(endSetback,get("endSetback",6));set(sideClearance,get("sideClearance",12));set(rowSpacing,get("rowSpacing",24));set(sunFactor,get("sunFactor",1.5));sunSpacing.setSelected(Boolean.parseBoolean(Config.getPref().get(PREFIX+"sunSpacing","false")));staggered.setSelected(Boolean.parseBoolean(Config.getPref().get(PREFIX+"staggered","false")));useSample.setSelected(Boolean.parseBoolean(Config.getPref().get(PREFIX+"useSample","false")));communityShops.setSelected(Boolean.parseBoolean(Config.getPref().get(PREFIX+"communityShops","false")));factoryMode.setSelected(false);set(shopSetback,Double.parseDouble(Config.getPref().get(PREFIX+"shopSetback","6")));set(shopDepth,Double.parseDouble(Config.getPref().get(PREFIX+"shopDepth","8")));set(shopHeight,Double.parseDouble(Config.getPref().get(PREFIX+"shopHeight","4")));chamfer.setSelectedIndex(Math.max(0,Math.min(1,Integer.parseInt(Config.getPref().get(PREFIX+"chamfer","0")))));buildingTag.setSelectedIndex(Math.max(0,Math.min(2,Integer.parseInt(Config.getPref().get(PREFIX+"buildingTag","0")))));}
        catch(RuntimeException ignored){ }
        finally{loading=false;}
        LayoutSettings legacy=settings();
        if(Config.getPref().get(presetKey("自定义 / 上次参数"),"").isEmpty())writePreset("自定义 / 上次参数",legacy);
        String saved=Config.getPref().get(PREFIX+"activePreset","自定义 / 上次参数");
        if(!PRESETS.containsKey(saved)&&!"自定义 / 上次参数".equals(saved))saved="自定义 / 上次参数";
        loading=true;try{preset.setSelectedItem(saved);}finally{loading=false;}
        applyPreset(saved);
        loading=true;try{set(factoryRoadWidth,get("factoryRoadWidth",6));set(factoryRoadClearance,get("factoryRoadClearance",3));set(factoryMinWidth,get("factoryMinWidth",12));set(factoryMaxWidth,get("factoryMaxWidth",24));set(factoryMinLength,get("factoryMinLength",30));set(factoryMaxLength,get("factoryMaxLength",60));set(factoryFloorHeight,get("factoryFloorHeight",6));set(factoryMinLevels,get("factoryMinLevels",1));set(factoryMaxLevels,get("factoryMaxLevels",2));set(factorySetback,get("factorySetback",8));set(factoryGap,get("factoryGap",8));set(factoryRowGap,get("factoryRowGap",15));set(factorySeed,get("factorySeed",42));set(factoryGroupSize,get("factoryGroupSize",3));}finally{loading=false;}
        if(useSample.isSelected())selectionHint.setText("地块输入：选择闭合地块 way，并额外选择一个闭合 building way 作为样板。");communityShops.setEnabled(!useSample.isSelected());useSample.setEnabled(!communityShops.isSelected());
        Double degrees=savedDirectionDegrees();if(degrees!=null)directionStatus.setText(String.format(java.util.Locale.ROOT,"排列方向：与所选地块边平行（%.1f°）",degrees));
    }
    private double get(String name,double fallback){return Double.parseDouble(Config.getPref().get(PREFIX+name,Double.toString(fallback)));}
    private static void set(JSpinner spinner,double value){spinner.setValue(Math.max(((Number)((SpinnerNumberModel)spinner.getModel()).getMinimum()).doubleValue(),Math.min(((Number)((SpinnerNumberModel)spinner.getModel()).getMaximum()).doubleValue(),value)));}
    private void saveSettings(){try{LayoutSettings s=settings();Config.getPref().put(PREFIX+"width",Double.toString(s.width()));Config.getPref().put(PREFIX+"length",Double.toString(s.length()));Config.getPref().put(PREFIX+"height",Double.toString(s.height()));Config.getPref().put(PREFIX+"endSetback",Double.toString(s.endSetback()));Config.getPref().put(PREFIX+"sideClearance",Double.toString(s.sideClearance()));Config.getPref().put(PREFIX+"rowSpacing",Double.toString(s.rowSpacing()));Config.getPref().put(PREFIX+"sunFactor",Double.toString(s.sunFactor()));Config.getPref().put(PREFIX+"sunSpacing",Boolean.toString(s.sunSpacing()));Config.getPref().put(PREFIX+"staggered",Boolean.toString(s.staggered()));Config.getPref().put(PREFIX+"useSample",Boolean.toString(useSample.isSelected()));Config.getPref().put(PREFIX+"communityShops",Boolean.toString(communityShops.isSelected()));Config.getPref().put(PREFIX+"factoryRoadWidth",Double.toString(value(factoryRoadWidth)));Config.getPref().put(PREFIX+"factoryRoadClearance",Double.toString(value(factoryRoadClearance)));Config.getPref().put(PREFIX+"factoryMinWidth",Double.toString(value(factoryMinWidth)));Config.getPref().put(PREFIX+"factoryMaxWidth",Double.toString(value(factoryMaxWidth)));Config.getPref().put(PREFIX+"factoryMinLength",Double.toString(value(factoryMinLength)));Config.getPref().put(PREFIX+"factoryMaxLength",Double.toString(value(factoryMaxLength)));Config.getPref().put(PREFIX+"factoryFloorHeight",Double.toString(value(factoryFloorHeight)));Config.getPref().put(PREFIX+"factoryMinLevels",Double.toString(value(factoryMinLevels)));Config.getPref().put(PREFIX+"factoryMaxLevels",Double.toString(value(factoryMaxLevels)));Config.getPref().put(PREFIX+"factorySetback",Double.toString(value(factorySetback)));Config.getPref().put(PREFIX+"factoryGap",Double.toString(value(factoryGap)));Config.getPref().put(PREFIX+"factoryRowGap",Double.toString(value(factoryRowGap)));Config.getPref().put(PREFIX+"factorySeed",Long.toString(factorySeed()));Config.getPref().put(PREFIX+"factoryGroupSize",Integer.toString(factoryGroupSize()));Config.getPref().put(PREFIX+"shopRoadClearance",Double.toString(value(shopRoadClearance)));Config.getPref().put(PREFIX+"shopSetback",Double.toString(value(shopSetback)));Config.getPref().put(PREFIX+"shopDepth",Double.toString(value(shopDepth)));Config.getPref().put(PREFIX+"shopHeight",Double.toString(value(shopHeight)));Config.getPref().put(PREFIX+"chamfer",Integer.toString(chamfer.getSelectedIndex()));Config.getPref().put(PREFIX+"buildingTag",Integer.toString(buildingTag.getSelectedIndex()));}catch(RuntimeException ignored){ }}
    private void settingsChanged(){
        if(loading)return;
        saveSettings();
        if(!factoryMode()&&!communityShops())try{writePreset(activePreset,settings());}catch(IllegalArgumentException ignored){ }
        Config.getPref().put(PREFIX+"activePreset",activePreset);
        if(mode!=null)mode.settingsChanged();
    }
    private static String presetKey(String name){return PREFIX+"savedPreset."+name;}
    private static void writePreset(String name,LayoutSettings s){
        Config.getPref().put(presetKey(name),s.width()+","+s.length()+","+s.height()+","+s.endSetback()+","+s.sideClearance()+","+s.rowSpacing()+","+s.sunSpacing()+","+s.sunFactor()+","+s.staggered()+","+s.buildingTag());
    }
    private static LayoutSettings readPreset(String name,LayoutSettings fallback){
        try{String[] p=Config.getPref().get(presetKey(name),"").split(",");if(p.length!=10)return fallback;
            LayoutSettings s=new LayoutSettings(Double.parseDouble(p[0]),Double.parseDouble(p[1]),Double.parseDouble(p[2]),Double.parseDouble(p[3]),Double.parseDouble(p[4]),Double.parseDouble(p[5]),Boolean.parseBoolean(p[6]),Double.parseDouble(p[7]),Boolean.parseBoolean(p[8]),p[9]);s.validate();return s;
        }catch(IllegalArgumentException ex){return fallback;}
    }
    LayoutSettings settings(){LayoutSettings s=new LayoutSettings(value(width),value(length),value(height),value(endSetback),value(sideClearance),value(rowSpacing),sunSpacing.isSelected(),value(sunFactor),staggered.isSelected(),new String[]{"apartments","residential","yes"}[buildingTag.getSelectedIndex()]);s.validate();return s;}
    private static double value(JSpinner s){return((Number)s.getValue()).doubleValue();}
    boolean useSample(){return useSample.isSelected();}
    boolean communityShops(){return communityShops.isSelected();}
    boolean factoryMode(){return factoryMode.isSelected();}
    boolean factoryRoads(){return factoryMode.isSelected()&&factoryRoads.isSelected();}
    double factoryRoadWidth(){return value(factoryRoadWidth);}
    double factoryRoadClearance(){return value(factoryRoadClearance);}
    double factoryMinWidth(){return value(factoryMinWidth);}
    double factoryMaxWidth(){return value(factoryMaxWidth);}
    double factoryMinLength(){return value(factoryMinLength);}
    double factoryMaxLength(){return value(factoryMaxLength);}
    double factoryFloorHeight(){return value(factoryFloorHeight);}
    int factoryMinLevels(){return (int)value(factoryMinLevels);}
    int factoryMaxLevels(){return (int)value(factoryMaxLevels);}
    double factorySetback(){return value(factorySetback);}
    double factoryGap(){return value(factoryGap);}
    double factoryRowGap(){return value(factoryRowGap);}
    int factoryGroupSize(){return ((Number)factoryGroupSize.getValue()).intValue();}
    long factorySeed(){return Math.round(value(factorySeed));}
    double shopRoadClearance(){return value(shopRoadClearance);}
    double shopSetback(){return value(shopSetback);}
    double shopDepth(){return value(shopDepth);}
    double shopHeight(){return value(shopHeight);}
    double chamferDegrees(){return chamfer.getSelectedIndex()==0?30:45;}
    boolean streetTreeMode(){return streetTreeMode.isSelected();}
    boolean treeUseSelectedRoad(){return treeMode.getSelectedIndex()==1;}
    double treeSpacing(){return value(treeSpacing);}
    double treeOffset(){return value(treeOffset);}
    boolean treeDoubleSide(){return treeDoubleSide.isSelected();}
    void directionMessage(String text){directionStatus.setText(text);}
    void saveDirection(double degrees){try{Config.getPref().put(PREFIX+"directionDegrees",Double.toString(degrees));}catch(RuntimeException ignored){}}
    void clearDirection(){directionStatus.setText("排列方向：默认地图轴向");try{Config.getPref().put(PREFIX+"directionDegrees",null);}catch(RuntimeException ignored){}}
    Double savedDirectionDegrees(){try{return Double.parseDouble(Config.getPref().get(PREFIX+"directionDegrees"));}catch(RuntimeException ex){return null;}}
    void message(String text){status.setText(text==null?"操作失败。":text);status.setToolTipText(status.getText());}
}




