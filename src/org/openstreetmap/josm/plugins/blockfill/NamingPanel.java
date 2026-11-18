package org.openstreetmap.josm.plugins.blockfill;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

/** An explicit one-parcel name selection. Preview and selection never modify the data set. */
public final class NamingPanel extends JPanel {
    final JCheckBox enabled=new JCheckBox("架空地图命名（OGF）",false);
    final JTextField city=new JTextField();
    final JTextField district=new JTextField();
    final JTextField road=new JTextField();
    final JTextField developer=new JTextField();
    final JComboBox<String> level=new JComboBox<>();
    String[] levelKeys=new String[0];
    final DefaultListModel<String> candidates=new DefaultListModel<>();
    final JList<String> list=new JList<>(candidates);
    final JTextField selectedName=new JTextField();
    final JButton refresh=new JButton("刷新候选");
    final JLabel state=new JLabel("名称功能：未启用 · 生成地块时不附加 name");
    private final PlaceNameGenerator generator=new PlaceNameGenerator();
    private final JPanel form=new FormPanel();
    private final List<JComponent> controls=new ArrayList<>();
    private PlaceNameGenerator.Kind kind=PlaceNameGenerator.Kind.OTHER;
    private boolean updating;
    private String armedName;
    private boolean nameSkipped;
    private Runnable changed=()->{};
    public NamingPanel(){
        super(new BorderLayout());
        GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=0;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(3,4,3,4);
        form.add(enabled,g);
        JLabel intro=description(220);
        g.gridy++;form.add(intro,g);
        row(g,labeled("城市",city));row(g,labeled("区县",district));row(g,labeled("道路（可空）",road));row(g,labeled("开发商 / 品牌（可空）",developer));row(g,labeled("命名风格",level));row(g,refresh);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setVisibleRowCount(6);JScrollPane listScroll=new JScrollPane(list);listScroll.setPreferredSize(new Dimension(220,130));row(g,listScroll);
        row(g,labeled("下一块名称（可编辑）",selectedName));
        g.gridy++;g.weighty=1;g.fill=GridBagConstraints.BOTH;form.add(new JPanel(),g);
        JScrollPane scroll=new JScrollPane(form);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(4,0));
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(0xD0,0xD0,0xD0)),BorderFactory.createEmptyBorder(4,6,4,6)));
        footer.add(state,BorderLayout.CENTER);add(footer,BorderLayout.SOUTH);
        Collections.addAll(controls,city,district,road,developer,level,refresh,list,selectedName);
        enabled.addActionListener(e->{clearSelection();updateEnabled();refreshCandidatesIfReady();});
        // Rebuild classification when the user edits a local research code or reloads classes.
        enabled.setToolTipText("勾选后将在绘制时给新地块同步生成架空名称；默认关闭。");
        state.setToolTipText("填写城市和区县后自动生成候选并默认使用第一条；点一行或直接编辑名称即可切换下一块名称，点地图创建。");
        developer.setToolTipText("开发商/品牌只用于新建商品房、常规商业和商务办公；社区商业、老社区和村寨风格不加开发商品牌。");
        refresh.setToolTipText("按当前城市、区县、道路、开发商和命名风格重新生成一批候选。");
        selectedName.setToolTipText("留空时每块自动随机取名；输入的内容会立即作为下一块名称。");
        DocumentListener contextListener=new DocumentListener(){public void insertUpdate(DocumentEvent e){invalidateContext();}public void removeUpdate(DocumentEvent e){invalidateContext();}public void changedUpdate(DocumentEvent e){invalidateContext();}};
        city.getDocument().addDocumentListener(contextListener);district.getDocument().addDocumentListener(contextListener);road.getDocument().addDocumentListener(contextListener);developer.getDocument().addDocumentListener(contextListener);
        level.addActionListener(e->{if(!updating)invalidateContext();});
        selectedName.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){edited();}public void removeUpdate(DocumentEvent e){edited();}public void changedUpdate(DocumentEvent e){edited();}private void edited(){applyName();}});
        list.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&list.getSelectedValue()!=null)selectedName.setText(list.getSelectedValue());});
        refresh.addActionListener(e->generateCandidates());updateEnabled();clearSelection();
    }
    /** Form panel that follows the scroll viewport width, so rows and wrapping text use the real width. */
    private static final class FormPanel extends JPanel implements Scrollable {
        FormPanel(){super(new GridBagLayout());}
        @Override public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect,int orientation,int direction){return 16;}
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect,int orientation,int direction){return 64;}
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private static JLabel description(int width){
        JLabel label=new JLabel(descriptionHtml(width)){
            @Override public void setBounds(int x,int y,int w,int h){
                super.setBounds(x,y,w,h);
                if(w>60){
                    String text=descriptionHtml(w-2);
                    if(!text.equals(getText()))super.setText(text);
                }
            }
        };
        label.setForeground(new Color(0x66,0x66,0x66));
        return label;
    }
    private static String descriptionHtml(int width){
        // Swing HTML maps CSS px to points (~4/3 device pixels), so scale down to fit the label width.
        int css=Math.max(90,width*3/4);
        return "<html><body style='width:"+css+"px'>启用后，绘制地块时同步添加随机架空名称。填写必填的「城市」「区县」及可选的「道路」「开发商 / 品牌」，下方列表会自动生成候选名称，默认使用第一条，点选其他候选项即可切换，每次绘制后自动刷新；亦可直接编辑「下一块名称」字段，未填写名称时，创建每块时将自动随机命名。</body></html>";
    }
    void onChanged(Runnable action){changed=action;}
    private void row(GridBagConstraints g,JComponent component){g.gridy++;form.add(component,g);}
    private JPanel labeled(String label,JComponent component){JPanel p=new JPanel(new BorderLayout(5,0));p.add(new JLabel(label),BorderLayout.WEST);p.add(component);return p;}
    void setKind(PlaceNameGenerator.Kind value){
        if(value==kind)return;kind=value;updating=true;level.removeAllItems();
        if(value==PlaceNameGenerator.Kind.RESIDENTIAL){fillLevels(new String[]{"城中村 / 村落","老社区 / 单位家属院","刚需住宅","改善住宅","高端住宅 / 豪宅"},new String[]{"URBAN_VILLAGE","OLD_HOUSING","FIRST_HOME","IMPROVEMENT","LUXURY"});level.setSelectedIndex(3);}
        else if(value==PlaceNameGenerator.Kind.COMMERCIAL){fillLevels(new String[]{"社区商业","中端购物中心","高端商业","奢侈品牌主导商场"},new String[]{"COMMUNITY","MIDRANGE","PREMIUM","LUXURY"});level.setSelectedIndex(1);}
        else if(value==PlaceNameGenerator.Kind.BUSINESS){fillLevels(new String[]{"商务办公楼","商务中心 / 写字楼群","高端商务楼宇","企业总部 / 商务园区"},new String[]{"OFFICE","BUSINESS_CENTER","PREMIUM_OFFICE","HQ_CAMPUS"});level.setSelectedIndex(1);}
        else level.addItem("仅住宅 / 商业适用");updating=false;invalidateContext();updateEnabled();
    }
    private void invalidateContext(){if(updating)return;clearSelection();changed.run();refreshCandidatesIfReady();}
    /** Regenerates the visible batch as soon as the required context fields are filled. */
    private void refreshCandidatesIfReady(){
        if(!active())return;
        if(city.getText().isBlank()||district.getText().isBlank()){
            state.setText("名称功能：已启用 · 填写城市和区县后自动生成候选");
            return;
        }
        generateCandidates();
    }
    private void clearSelection(){armedName=null;candidates.clear();selectedName.setText("");
        if(kind==PlaceNameGenerator.Kind.OTHER)state.setText("名称功能：不可用 · 当前用地类别不支持架空命名");
        else if(!enabled.isSelected())state.setText("名称功能：未启用 · 生成地块时不附加 name");
        else state.setText("名称功能：已启用 · 将按城市、区县等信息自动生成候选");
    }
    private void updateEnabled(){for(JComponent c:controls)c.setEnabled(enabled.isSelected()&&kind!=PlaceNameGenerator.Kind.OTHER);enabled.setEnabled(kind!=PlaceNameGenerator.Kind.OTHER);}
    boolean active(){return enabled.isSelected()&&kind!=PlaceNameGenerator.Kind.OTHER;}
    private void fillLevels(String[] labels,String[] keys){levelKeys=keys;for(String label:labels)level.addItem(label);}
    private String levelKey(){int i=Math.max(0,level.getSelectedIndex());return levelKeys[i];}
    Collection<String> existingNames(){
        var data=MainApplication.getLayerManager().getEditDataSet();Set<String> result=new HashSet<>();
        if(data!=null)for(OsmPrimitive p:data.allPrimitives())if(!p.isDeleted()){if(p.get("name")!=null)result.add(p.get("name"));if(p.get("name:zh")!=null)result.add(p.get("name:zh"));}
        return result;
    }
    boolean generateCandidates(){try{
        List<String> batch=generator.candidates(kind,city.getText(),district.getText(),road.getText(),levelKey(),developer.getText(),12,existingNames());
        clearSelection();for(String name:batch)candidates.addElement(name);
        if(!candidates.isEmpty())list.setSelectedIndex(0);
        state.setText(candidates.isEmpty()?"没有可用的候选名称，将自动随机命名":"已生成 "+candidates.size()+" 个候选 · 下一块将使用："+candidates.get(0));
        return true;
    }catch(RuntimeException ex){state.setText(ex.getMessage());return false;}}
    /** The editable name box always is the name for the next parcel; no extra confirmation step. */
    private void applyName(){
        String text=selectedName.getText().trim();
        armedName=text.isEmpty()?null:text;
        if(armedName!=null)state.setText("下一块将使用："+armedName);
        else if(kind!=PlaceNameGenerator.Kind.OTHER&&enabled.isSelected())state.setText("名称功能：已启用 · 下一块将自动随机命名");
        changed.run();
    }
    String nameForCreation(Collection<String> existing){
        nameSkipped=false;
        if(!active())return null;
        if(armedName!=null){String key=PlaceNameGenerator.normalize(armedName);for(String s:existing)if(s!=null&&PlaceNameGenerator.normalize(s).equals(key))throw new IllegalArgumentException("已存在同名对象，请选择或编辑另一个名称。");return armedName;}
        // Missing required context must not block the parcel; create it without a name and warn instead.
        if(city.getText().isBlank()||district.getText().isBlank()){nameSkipped=true;return null;}
        return generator.next(kind,city.getText(),district.getText(),road.getText(),levelKey(),developer.getText(),existing);
    }
    /** Returns true once when the last {@link #nameForCreation} attempt skipped a name because city/district were empty. */
    boolean consumeNameSkippedWarning(){boolean skipped=nameSkipped;nameSkipped=false;return skipped;}
    void creationSucceeded(String name){
        if(name==null)return;
        generator.markUsed(name);
        // Keep a visible batch ready for the next parcel: regenerate and use its first name.
        if(generateCandidates()&&!candidates.isEmpty())state.setText("已使用："+name+"；下一块将使用："+candidates.get(0));
        else state.setText("已使用："+name+"；下一块将自动随机命名");
    }
}
