package org.openstreetmap.josm.plugins.blocklayout;

import java.io.File;
import javax.swing.SwingUtilities;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.spi.preferences.Config;

public final class LayoutUiStartupTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless","true");
        File home=new File("build-layout/test-home");home.mkdirs();System.setProperty("josm.home",home.getAbsolutePath());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Preferences preferences=new Preferences();preferences.enableSaveOnPut(false);Config.setPreferencesInstance(preferences);for(String key:preferences.getKeySet())if(key.startsWith("blocklayout."))preferences.put(key,null);
        setStatic(org.openstreetmap.josm.gui.MainApplication.class,"contentPanePrivate",new javax.swing.JPanel());
        setStatic(org.openstreetmap.josm.gui.MainApplication.class,"toolbar",new org.openstreetmap.josm.gui.preferences.ToolbarPreferences());
        setStatic(org.openstreetmap.josm.gui.MainApplication.class,"menu",new org.openstreetmap.josm.gui.MainMenu());
        SwingUtilities.invokeAndWait(()->{
            BlockLayoutPanel panel=new BlockLayoutPanel();BlockLayoutMode mode=new BlockLayoutMode(panel);panel.attach(mode);
            if(mode.getValue(javax.swing.Action.SMALL_ICON)==null)throw new AssertionError("Missing map mode icon");
            if(mode.getShortcut()==null)throw new AssertionError("Missing map mode shortcut");
            LayoutSettings settings=panel.settings();settings.validate();
            if(!containsScrollPane(panel))throw new AssertionError("Settings content should scroll");
            if(panel.tabs.getTabCount()!=3||!"住宅".equals(panel.tabs.getTitleAt(0))||!"底商".equals(panel.tabs.getTitleAt(1))||!"工业".equals(panel.tabs.getTitleAt(2)))throw new AssertionError("Settings should be organized into housing, shop and industrial tabs");
            if(!"apartments".equals(settings.buildingTag()))throw new AssertionError("Default building tag");
            if(panel.sunSpacing.isSelected() || panel.staggered.isSelected())throw new AssertionError("Optional layout toggles should default off");
            panel.preset.setSelectedItem("中高层住宅");
            if(((Number)panel.height.getValue()).doubleValue()!=36 || !panel.sunSpacing.isSelected())throw new AssertionError("Preset should populate dimensions and sun spacing");
            panel.height.setValue(39.0);
            panel.preset.setSelectedItem("高层住宅");
            panel.height.setValue(81.0);
            panel.preset.setSelectedItem("中高层住宅");
            if(panel.settings().height()!=39)throw new AssertionError("Edited medium-rise preset must persist across switching");
            panel.preset.setSelectedItem("自定义 / 上次参数");
            if(panel.settings().height()!=18)throw new AssertionError("Custom parameters must remain independent");
            panel.height.setValue(36.0);
            panel.width.setModel(new javax.swing.SpinnerNumberModel(0.0,null,null,1.0));boolean rejected=false;try{panel.settings();}catch(IllegalArgumentException expected){rejected=true;}
            if(!rejected)throw new AssertionError("Invalid UI dimensions must be rejected");
            panel.width.setModel(new javax.swing.SpinnerNumberModel(12.0,1.0,500.0,1.0));
            java.awt.event.MouseWheelEvent wheel=new java.awt.event.MouseWheelEvent(panel.width,java.awt.event.MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,2,2,0,false,java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,1,-1);
            panel.width.getMouseWheelListeners()[0].mouseWheelMoved(wheel);
            if(((Number)panel.width.getValue()).doubleValue()!=13)throw new AssertionError("Mouse wheel should step the focused numeric control");
            if(!panel.sunSpacing.isSelected())panel.sunSpacing.doClick();
            if(!panel.settings().sunSpacing())throw new AssertionError("Sun-spacing toggle not wired");
            if(!panel.staggered.isSelected())panel.staggered.doClick();if(!panel.settings().staggered())throw new AssertionError("Stagger toggle not wired");
            panel.communityShops.doClick();if(!panel.communityShops()||panel.useSample.isEnabled())throw new AssertionError("Shop and sample modes must be exclusive");
            panel.shopSetback.setValue(7.0);panel.shopDepth.setValue(9.0);panel.shopHeight.setValue(5.0);panel.chamfer.setSelectedIndex(1);
            if(panel.shopSetback()!=7||panel.shopDepth()!=9||panel.shopHeight()!=5||panel.chamferDegrees()!=45)throw new AssertionError("Shop controls");
            panel.communityShops.doClick();if(!panel.useSample.isEnabled())throw new AssertionError("Sample mode should re-enable after shop mode");
            if(panel.pickEdge.getActionListeners().length==0||panel.resetEdge.getActionListeners().length==0)throw new AssertionError("Direction controls must be wired");
            panel.factoryMode.doClick();if(!panel.factoryMode()||panel.communityShops.isSelected()||panel.useSample.isSelected())throw new AssertionError("Factory generation mode must exclude other generation modes");
            panel.factoryMinWidth.setValue(14.0);panel.factoryMaxWidth.setValue(24.0);panel.factoryMinLength.setValue(36.0);panel.factoryMaxLength.setValue(72.0);
            if(panel.factoryMinWidth()!=14||panel.factoryMaxWidth()!=24||panel.factoryMinLength()!=36||panel.factoryMaxLength()!=72)throw new AssertionError("Factory controls");
            panel.factoryGroupSize.setValue(4.0);panel.factoryFloorHeight.setValue(7.0);panel.factoryMinLevels.setValue(1.0);panel.factoryMaxLevels.setValue(3.0);
            panel.factoryRoads.doClick();if(!panel.factoryRoads())throw new AssertionError("Factory service road toggle");panel.factoryRoadWidth.setValue(7.0);panel.factoryRoadClearance.setValue(4.0);
            if(panel.factoryRoadWidth()!=7||panel.factoryRoadClearance()!=4)throw new AssertionError("Factory road settings");
            panel.setSize(420,600);layoutAll(panel);java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(420,600,java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();
            try{javax.imageio.ImageIO.write(image,"png",new File("build-layout/panel-preview.png"));}catch(java.io.IOException e){throw new IllegalStateException(e);}
            mode.destroy();
            BlockLayoutPanel restored=new BlockLayoutPanel();
            if(restored.factoryGroupSize()!=4||restored.factoryMinLength()!=36||restored.factoryFloorHeight()!=7||restored.factoryMinLevels()!=1||restored.factoryMaxLevels()!=3)throw new AssertionError("Factory grouping parameters must be restored");
            restored.preset.setSelectedItem("高层住宅");if(restored.settings().height()!=81)throw new AssertionError("Edited high-rise preset must survive panel recreation");
            restored.preset.setSelectedItem("中高层住宅");if(restored.settings().height()!=39)throw new AssertionError("Edited medium-rise preset must survive panel recreation");
            restored.preset.setSelectedItem("自定义 / 上次参数");
            if(((Number)restored.height.getValue()).doubleValue()!=36 || !restored.sunSpacing.isSelected())throw new AssertionError("Last settings should be restored from preferences");
            if(((Number)restored.shopSetback.getValue()).doubleValue()!=7||((Number)restored.shopDepth.getValue()).doubleValue()!=9||((Number)restored.shopHeight.getValue()).doubleValue()!=5||restored.chamferDegrees()!=45)throw new AssertionError("Shop parameters should be restored");
        });
        System.out.println("PASS LayoutUiStartupTest: panel and MapMode initialize; settings validation, defaults, toggles, render");
        System.exit(0);
    }
    private static void setStatic(Class<?> type,String name,Object value)throws Exception{java.lang.reflect.Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(null,value);}
    private static boolean containsScrollPane(java.awt.Container c){for(java.awt.Component child:c.getComponents()){if(child instanceof javax.swing.JScrollPane)return true;if(child instanceof java.awt.Container nested&&containsScrollPane(nested))return true;}return false;}
    private static void layoutAll(java.awt.Container c){c.doLayout();for(java.awt.Component child:c.getComponents())if(child instanceof java.awt.Container nested)layoutAll(nested);}
}
