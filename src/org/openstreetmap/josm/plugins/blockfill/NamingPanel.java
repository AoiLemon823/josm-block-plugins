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
    final JButton refresh=new JButton("① 生成 / 换一批候选");
    final JButton use=new JButton("② 使用选中名称（下一块）");
    final JLabel state=new JLabel("未启用：生成地块时不附加名称");
    private final PlaceNameGenerator generator=new PlaceNameGenerator();
    private final List<JComponent> controls=new ArrayList<>();
    private PlaceNameGenerator.Kind kind=PlaceNameGenerator.Kind.OTHER;
    private boolean updating;
    private String armedName;
    private Runnable changed=()->{};
    public NamingPanel(){
        super(new GridBagLayout());setBorder(BorderFactory.createTitledBorder("架空地块命名 · 可选候选，可手改"));
        GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.gridy=0;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(3,4,3,4);
        add(enabled,g);row(g,labeled("城市",city));row(g,labeled("区县",district));row(g,labeled("道路（可空）",road));row(g,labeled("开发商 / 品牌（可空）",developer));row(g,labeled("命名风格",level));row(g,refresh);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setVisibleRowCount(6);JScrollPane scroll=new JScrollPane(list);scroll.setPreferredSize(new Dimension(220,130));row(g,scroll);
        row(g,labeled("待用名称（可编辑）",selectedName));row(g,use);
        state.setToolTipText("填写开发商可改变商品房、常规商业和商务楼宇名称；社区商业、老社区与村寨不附加开发商品牌。生成候选 → 选择/编辑 → 确认 → 创建。创建后清空，避免复用。");row(g,state);
        JTextArea help=new JTextArea("开发商/品牌只用于新建商品房、常规商业和商务办公。\n社区商业、老社区和村寨风格不加开发商品牌。\n③ 点地图创建：只写入 name，不弹确认框。候选可选、可改。\n想连续随机：待用框留空，直接点地图，每块自动取新名。");help.setLineWrap(true);help.setWrapStyleWord(true);help.setEditable(false);help.setOpaque(false);row(g,help);
        g.gridy++;g.weighty=1;g.fill=GridBagConstraints.BOTH;add(new JPanel(),g);
        Collections.addAll(controls,city,district,road,developer,level,refresh,list,selectedName,use);
        enabled.addActionListener(e->{clearSelection();updateEnabled();});
        // Rebuild classification when the user edits a local research code or reloads classes.
        enabled.setToolTipText("勾选后才会给新地块生成架空名称；默认关闭。");
        DocumentListener contextListener=new DocumentListener(){public void insertUpdate(DocumentEvent e){invalidateContext();}public void removeUpdate(DocumentEvent e){invalidateContext();}public void changedUpdate(DocumentEvent e){invalidateContext();}};
        city.getDocument().addDocumentListener(contextListener);district.getDocument().addDocumentListener(contextListener);road.getDocument().addDocumentListener(contextListener);developer.getDocument().addDocumentListener(contextListener);
        level.addActionListener(e->{if(!updating)invalidateContext();});
        selectedName.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){edited();}public void removeUpdate(DocumentEvent e){edited();}public void changedUpdate(DocumentEvent e){edited();}private void edited(){armedName=null;state.setText("名称已编辑，点②确认用于下一块");changed.run();}});
        list.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&list.getSelectedValue()!=null)selectedName.setText(list.getSelectedValue());});
        list.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){if(e.getClickCount()==2)armSelectedName();}});
        refresh.addActionListener(e->generateCandidates());use.addActionListener(e->armSelectedName());updateEnabled();
    }
    void onChanged(Runnable action){changed=action;}
    private void row(GridBagConstraints g,JComponent component){g.gridy++;add(component,g);}
    private JPanel labeled(String label,JComponent component){JPanel p=new JPanel(new BorderLayout(5,0));p.add(new JLabel(label),BorderLayout.WEST);p.add(component);return p;}
    void setKind(PlaceNameGenerator.Kind value){
        if(value==kind)return;kind=value;updating=true;level.removeAllItems();
        if(value==PlaceNameGenerator.Kind.RESIDENTIAL){fillLevels(new String[]{"城中村 / 村落","老社区 / 单位家属院","刚需住宅","改善住宅","高端住宅 / 豪宅"},new String[]{"URBAN_VILLAGE","OLD_HOUSING","FIRST_HOME","IMPROVEMENT","LUXURY"});level.setSelectedIndex(3);}
        else if(value==PlaceNameGenerator.Kind.COMMERCIAL){fillLevels(new String[]{"社区商业","中端购物中心","高端商业","奢侈品牌主导商场"},new String[]{"COMMUNITY","MIDRANGE","PREMIUM","LUXURY"});level.setSelectedIndex(1);}
        else if(value==PlaceNameGenerator.Kind.BUSINESS){fillLevels(new String[]{"商务办公楼","商务中心 / 写字楼群","高端商务楼宇","企业总部 / 商务园区"},new String[]{"OFFICE","BUSINESS_CENTER","PREMIUM_OFFICE","HQ_CAMPUS"});level.setSelectedIndex(1);}
        else level.addItem("仅住宅 / 商业适用");updating=false;invalidateContext();updateEnabled();
    }
    private void invalidateContext(){if(updating)return;clearSelection();changed.run();}
    private void clearSelection(){armedName=null;candidates.clear();selectedName.setText("");state.setText(enabled.isSelected()?"未选定：创建时逐块随机；也可先生成并选定候选":"未启用：生成地块时不附加名称");}
    private void updateEnabled(){for(JComponent c:controls)c.setEnabled(enabled.isSelected()&&kind!=PlaceNameGenerator.Kind.OTHER);enabled.setEnabled(kind!=PlaceNameGenerator.Kind.OTHER);}
    boolean active(){return enabled.isSelected()&&kind!=PlaceNameGenerator.Kind.OTHER;}
    private void fillLevels(String[] labels,String[] keys){levelKeys=keys;for(String label:labels)level.addItem(label);}
    private String levelKey(){int i=Math.max(0,level.getSelectedIndex());return levelKeys[i];}
    Collection<String> existingNames(){
        var data=MainApplication.getLayerManager().getEditDataSet();Set<String> result=new HashSet<>();
        if(data!=null)for(OsmPrimitive p:data.allPrimitives())if(!p.isDeleted()){if(p.get("name")!=null)result.add(p.get("name"));if(p.get("name:zh")!=null)result.add(p.get("name:zh"));}
        return result;
    }
    void generateCandidates(){try{
        List<String> batch=generator.candidates(kind,city.getText(),district.getText(),road.getText(),levelKey(),developer.getText(),12,existingNames());
        clearSelection();for(String name:batch)candidates.addElement(name);state.setText("已生成12个候选：点一行，点②确认（可双击）");
    }catch(RuntimeException ex){state.setText(ex.getMessage());}}
    void armSelectedName(){String name=selectedName.getText().trim();if(name.isEmpty()){state.setText("先点选候选或填写名称，再点②");return;}armedName=name;state.setText("下一块将使用："+name);changed.run();}
    String nameForCreation(Collection<String> existing){
        if(!active())return null;
        if(armedName!=null){String key=PlaceNameGenerator.normalize(armedName);for(String s:existing)if(s!=null&&PlaceNameGenerator.normalize(s).equals(key))throw new IllegalArgumentException("已存在同名对象，请选择或编辑另一个名称。");return armedName;}
        if(!selectedName.getText().isBlank())throw new IllegalArgumentException("待用名称尚未确认，请点②“使用选中名称”，或清空后随机生成。");
        return generator.next(kind,city.getText(),district.getText(),road.getText(),levelKey(),developer.getText(),existing);
    }
    void creationSucceeded(String name){if(name!=null){generator.markUsed(name);clearSelection();state.setText("已使用："+name+"；下一块可重新选名或自动随机");}}
}
