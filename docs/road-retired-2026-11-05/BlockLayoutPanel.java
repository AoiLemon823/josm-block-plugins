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
    final JCheckBox useSample=new JCheckBox("使用所选建筑作为样板轮廓",false),communityShops=new JCheckBox("生成社区底商围合",false),serviceRoads=new JCheckBox("生成内部 service 道路",false);
    final JCheckBox exitRoads=new JCheckBox("按两个出口节点寻路",false);
    final JSpinner shopSetback=number(6,0,100,0.5),shopDepth=number(8,1,100,0.5),shopHeight=number(4,1,100,0.5);
    final JSpinner roadWidth=number(5,2,20,0.5),roadClearance=number(3,0,100,0.5),shopRoadClearance=number(2,0,100,0.5);
    final JComboBox<String> chamfer=new JComboBox<>(new String[]{"30°端部倒角","45°端部倒角"});
    final JButton pickEdge=new JButton("拾取地块边方向"),resetEdge=new JButton("恢复默认方向");
    final JLabel directionStatus=new JLabel("排列方向：默认地图轴向");
    final JLabel status=new JLabel("选择闭合地块后开始预览。"),selectionHint=new JLabel("地块输入：只选择闭合地块 way。样板模式再额外选择一个 building way。");
    private BlockLayoutMode mode;
    private boolean loading;

    public BlockLayoutPanel() {
        super("住宅楼与底商排布","edit","在选中的闭合地块内预览建筑布局",null,360);
        JPanel form=new JPanel(new GridBagLayout());form.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        int row=0;addRow(form,row++,"参数预设",preset);addRow(form,row++,"建筑短边宽度（米）",width);
        addRow(form,row++,"建筑长边长度（米）",length);addRow(form,row++,"建筑高度（米）",height);
        addRow(form,row++,"建筑端部退距（米）",endSetback);addRow(form,row++,"同排楼栋侧向净距（米）",sideClearance);
        addRow(form,row++,"最小排间距（米）",rowSpacing);addRow(form,row++,"日照距离系数",sunFactor);addRow(form,row++,"生成标签",buildingTag);
        addFullRow(form,row++,sunSpacing);addFullRow(form,row++,staggered);addFullRow(form,row++,useSample);addFullRow(form,row++,communityShops);addFullRow(form,row++,serviceRoads);
        addFullRow(form,row++,exitRoads);
        addRow(form,row++,"service 道路宽度（米）",roadWidth);addRow(form,row++,"道路到建筑退距（米）",roadClearance);addRow(form,row++,"底商到道路退距（米）",shopRoadClearance);
        addRow(form,row++,"底商沿街后退（米）",shopSetback);addRow(form,row++,"底商进深（米）",shopDepth);addRow(form,row++,"底商高度（米）",shopHeight);addRow(form,row++,"端部倒角",chamfer);
        addFullRow(form,row++,pickEdge);addFullRow(form,row++,resetEdge);addFullRow(form,row++,directionStatus);
        JButton previewButton=new JButton("开始排布预览");previewButton.addActionListener(e->{try{if(mode!=null)mode.startPreview();}catch(RuntimeException ex){message(ex.getMessage());}});
        addFullRow(form,row++,previewButton);addFullRow(form,row++,selectionHint);
        JTextArea note=new JTextArea("流程：选地块并生成住宅，手动调整布局；再勾选内部 service 道路生成，基于已摆住宅检查通道；最后可生成后退的底商多边形，设置后退、进深和高度。service 道路按住宅排间空隙估算，预览后可手动调整。拾取地块边可令住宅阵列平行该边。\n\n楼栋越界不裁切；已存在 building 面作为避让区。左键确认一次生成，Ctrl+Z 一次撤销，Esc 退出。最多生成500栋。\n\n日照排距按 max(最小排距, 建筑高度×系数) 估算，不是当地日照规范合规证明。未纳入纬度、朝向、地形或地方规范。\n\n插件只新增 building way，不修改边界、样板和已有地物；按高度约每3米估算层数，生成后请复核。");
        note.setLineWrap(true);note.setWrapStyleWord(true);note.setEditable(false);note.setOpaque(false);
        JPanel content=scrollContentPanel();content.add(form,BorderLayout.NORTH);content.add(note,BorderLayout.CENTER);
        JScrollPane scroll=new JScrollPane(content,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setViewportBorder(BorderFactory.createEmptyBorder(0,0,0,3));scroll.getVerticalScrollBar().setUnitIncrement(18);add(scroll,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(4,4));footer.setBorder(BorderFactory.createEmptyBorder(3,4,3,4));footer.add(status,BorderLayout.CENTER);add(footer,BorderLayout.SOUTH);
        ChangeListener changed=e->{if(!loading)settingsChanged();};
        for(JSpinner spinner:new JSpinner[]{width,length,height,endSetback,sideClearance,rowSpacing,sunFactor,shopSetback,shopDepth,shopHeight,roadWidth,roadClearance,shopRoadClearance}){
            spinner.addChangeListener(changed);
            spinner.addMouseWheelListener(e->wheelAdjust(spinner,e));
            spinner.getEditor().addMouseWheelListener(e->wheelAdjust(spinner,e));
        }
        sunSpacing.addActionListener(e->settingsChanged());staggered.addActionListener(e->settingsChanged());buildingTag.addActionListener(e->settingsChanged());useSample.addActionListener(e->{selectionHint.setText(useSample.isSelected()?"地块输入：选择闭合地块 way，并额外选择一个闭合 building way 作为样板。":"地块输入：只选择闭合地块 way。样板模式再额外选择一个 building way。");settingsChanged();});
        preset.addActionListener(e->{if(!loading)applyPreset((String)preset.getSelectedItem());});
        communityShops.addActionListener(e->{useSample.setSelected(false);useSample.setEnabled(!communityShops.isSelected());serviceRoads.setSelected(false);settingsChanged();});
        useSample.addActionListener(e->{if(useSample.isSelected())communityShops.setSelected(false);communityShops.setEnabled(!useSample.isSelected());selectionHint.setText(useSample.isSelected()?"地块输入：选择闭合地块 way，并额外选择一个闭合 building way 作为样板。":"地块输入：只选择闭合地块 way。样板模式再额外选择一个 building way。");settingsChanged();});
        serviceRoads.addActionListener(e->{if(serviceRoads.isSelected()){communityShops.setSelected(false);useSample.setSelected(false);selectionHint.setText("先生成并手动调整住宅；选择小区地块后，再生成其间 service 道路。");}else selectionHint.setText("地块输入：只选择闭合地块 way。样板模式再额外选择一个 building way。");settingsChanged();});
        exitRoads.addActionListener(e->{if(exitRoads.isSelected()){serviceRoads.setSelected(true);communityShops.setSelected(false);useSample.setSelected(false);selectionHint.setText("选择一个地块 way 和边界上的两个出口节点，再预览出口寻路。");}settingsChanged();});
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
        if("自定义 / 上次参数".equals(name))return;LayoutSettings s=PRESETS.get(name);if(s==null)return;
        loading=true;try{set(width,s.width());set(length,s.length());set(height,s.height());set(endSetback,s.endSetback());set(sideClearance,s.sideClearance());set(rowSpacing,s.rowSpacing());set(sunFactor,s.sunFactor());sunSpacing.setSelected(s.sunSpacing());staggered.setSelected(s.staggered());buildingTag.setSelectedIndex(switch(s.buildingTag()){case "residential"->1;case "yes"->2;default->0;});}
        finally{loading=false;}settingsChanged();
    }
    private void loadLastSettings(){
        try{loading=true;set(width,get("width",12));set(length,get("length",48));set(height,get("height",18));set(endSetback,get("endSetback",6));set(sideClearance,get("sideClearance",12));set(rowSpacing,get("rowSpacing",24));set(sunFactor,get("sunFactor",1.5));sunSpacing.setSelected(Boolean.parseBoolean(Config.getPref().get(PREFIX+"sunSpacing","false")));staggered.setSelected(Boolean.parseBoolean(Config.getPref().get(PREFIX+"staggered","false")));useSample.setSelected(Boolean.parseBoolean(Config.getPref().get(PREFIX+"useSample","false")));communityShops.setSelected(Boolean.parseBoolean(Config.getPref().get(PREFIX+"communityShops","false")));serviceRoads.setSelected(false);set(roadWidth,Double.parseDouble(Config.getPref().get(PREFIX+"roadWidth","5")));set(roadClearance,Double.parseDouble(Config.getPref().get(PREFIX+"roadClearance","3")));set(shopRoadClearance,Double.parseDouble(Config.getPref().get(PREFIX+"shopRoadClearance","2")));set(shopSetback,Double.parseDouble(Config.getPref().get(PREFIX+"shopSetback","6")));set(shopDepth,Double.parseDouble(Config.getPref().get(PREFIX+"shopDepth","8")));set(shopHeight,Double.parseDouble(Config.getPref().get(PREFIX+"shopHeight","4")));chamfer.setSelectedIndex(Math.max(0,Math.min(1,Integer.parseInt(Config.getPref().get(PREFIX+"chamfer","0")))));buildingTag.setSelectedIndex(Math.max(0,Math.min(2,Integer.parseInt(Config.getPref().get(PREFIX+"buildingTag","0")))));}
        catch(RuntimeException ignored){ }
        finally{loading=false;}
        if(useSample.isSelected())selectionHint.setText("地块输入：选择闭合地块 way，并额外选择一个闭合 building way 作为样板。");communityShops.setEnabled(!useSample.isSelected());useSample.setEnabled(!communityShops.isSelected());
        Double degrees=savedDirectionDegrees();if(degrees!=null)directionStatus.setText(String.format(java.util.Locale.ROOT,"排列方向：与所选地块边平行（%.1f°）",degrees));
    }
    private double get(String name,double fallback){return Double.parseDouble(Config.getPref().get(PREFIX+name,Double.toString(fallback)));}
    private static void set(JSpinner spinner,double value){spinner.setValue(Math.max(((Number)((SpinnerNumberModel)spinner.getModel()).getMinimum()).doubleValue(),Math.min(((Number)((SpinnerNumberModel)spinner.getModel()).getMaximum()).doubleValue(),value)));}
    private void saveSettings(){try{LayoutSettings s=settings();Config.getPref().put(PREFIX+"width",Double.toString(s.width()));Config.getPref().put(PREFIX+"length",Double.toString(s.length()));Config.getPref().put(PREFIX+"height",Double.toString(s.height()));Config.getPref().put(PREFIX+"endSetback",Double.toString(s.endSetback()));Config.getPref().put(PREFIX+"sideClearance",Double.toString(s.sideClearance()));Config.getPref().put(PREFIX+"rowSpacing",Double.toString(s.rowSpacing()));Config.getPref().put(PREFIX+"sunFactor",Double.toString(s.sunFactor()));Config.getPref().put(PREFIX+"sunSpacing",Boolean.toString(s.sunSpacing()));Config.getPref().put(PREFIX+"staggered",Boolean.toString(s.staggered()));Config.getPref().put(PREFIX+"useSample",Boolean.toString(useSample.isSelected()));Config.getPref().put(PREFIX+"communityShops",Boolean.toString(communityShops.isSelected()));Config.getPref().put(PREFIX+"roadWidth",Double.toString(value(roadWidth)));Config.getPref().put(PREFIX+"roadClearance",Double.toString(value(roadClearance)));Config.getPref().put(PREFIX+"shopRoadClearance",Double.toString(value(shopRoadClearance)));Config.getPref().put(PREFIX+"shopSetback",Double.toString(value(shopSetback)));Config.getPref().put(PREFIX+"shopDepth",Double.toString(value(shopDepth)));Config.getPref().put(PREFIX+"shopHeight",Double.toString(value(shopHeight)));Config.getPref().put(PREFIX+"chamfer",Integer.toString(chamfer.getSelectedIndex()));Config.getPref().put(PREFIX+"buildingTag",Integer.toString(buildingTag.getSelectedIndex()));}catch(RuntimeException ignored){ }}
    private void settingsChanged(){saveSettings();if(mode!=null)mode.settingsChanged();}
    LayoutSettings settings(){LayoutSettings s=new LayoutSettings(value(width),value(length),value(height),value(endSetback),value(sideClearance),value(rowSpacing),sunSpacing.isSelected(),value(sunFactor),staggered.isSelected(),new String[]{"apartments","residential","yes"}[buildingTag.getSelectedIndex()]);s.validate();return s;}
    private static double value(JSpinner s){return((Number)s.getValue()).doubleValue();}
    boolean useSample(){return useSample.isSelected();}
    boolean communityShops(){return communityShops.isSelected();}
    boolean serviceRoads(){return serviceRoads.isSelected();}
    boolean exitRoads(){return serviceRoads.isSelected()&&exitRoads.isSelected();}
    double roadWidth(){return value(roadWidth);}
    double roadClearance(){return value(roadClearance);}
    double shopRoadClearance(){return value(shopRoadClearance);}
    double shopSetback(){return value(shopSetback);}
    double shopDepth(){return value(shopDepth);}
    double shopHeight(){return value(shopHeight);}
    double chamferDegrees(){return chamfer.getSelectedIndex()==0?30:45;}
    void directionMessage(String text){directionStatus.setText(text);}
    void saveDirection(double degrees){try{Config.getPref().put(PREFIX+"directionDegrees",Double.toString(degrees));}catch(RuntimeException ignored){}}
    void clearDirection(){directionStatus.setText("排列方向：默认地图轴向");try{Config.getPref().put(PREFIX+"directionDegrees",null);}catch(RuntimeException ignored){}}
    Double savedDirectionDegrees(){try{return Double.parseDouble(Config.getPref().get(PREFIX+"directionDegrees"));}catch(RuntimeException ex){return null;}}
    void message(String text){status.setText(text==null?"操作失败。":text);status.setToolTipText(status.getText());}
}
