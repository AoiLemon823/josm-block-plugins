package org.openstreetmap.josm.plugins.blockfill;

import java.io.File;
import javax.swing.SwingUtilities;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.preferences.JosmBaseDirectories;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;

public final class UiStartupTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        new File("build/test-home").mkdirs();
        System.setProperty("josm.home", new File("build/test-home").getAbsolutePath());
        Config.setBaseDirectoriesProvider(JosmBaseDirectories.getInstance());
        Preferences preferences=new Preferences();preferences.enableSaveOnPut(false);
        Config.setPreferencesInstance(preferences);
        java.lang.reflect.Field content = org.openstreetmap.josm.gui.MainApplication.class.getDeclaredField("contentPanePrivate");
        content.setAccessible(true);
        content.set(null, new javax.swing.JPanel());
        java.lang.reflect.Field toolbar = org.openstreetmap.josm.gui.MainApplication.class.getDeclaredField("toolbar");
        toolbar.setAccessible(true);
        toolbar.set(null, new org.openstreetmap.josm.gui.preferences.ToolbarPreferences());
        java.lang.reflect.Field menu = org.openstreetmap.josm.gui.MainApplication.class.getDeclaredField("menu");
        menu.setAccessible(true);
        menu.set(null, new org.openstreetmap.josm.gui.MainMenu());
        SwingUtilities.invokeAndWait(() -> {
            System.out.println("Creating actual BlockFillPanel...");
            BlockFillPanel panel = new BlockFillPanel();
            System.out.println("Creating actual BlockFillMode...");
            BlockFillMode mode = new BlockFillMode(panel);
            panel.attach(mode);
            if (mode.getValue(javax.swing.Action.SMALL_ICON) == null) throw new AssertionError("Map mode has no icon");
            if (mode.getShortcut() == null) throw new AssertionError("Map mode has no shortcut");
            NamingPanel naming=panel.naming;
            if(naming.active())throw new AssertionError("Fictional naming must be opt-in for real-map users");
            naming.enabled.doClick();naming.city.setText("榆川市");naming.district.setText("静海区");naming.road.setText("百叶路");
            naming.generateCandidates();
            if(naming.candidates.size()!=12)throw new AssertionError("Expected selectable batch");
            naming.list.setSelectedIndex(3);
            boolean unconfirmed=false;try{naming.nameForCreation(java.util.List.of());}catch(IllegalArgumentException ex){unconfirmed=true;}
            if(!unconfirmed)throw new AssertionError("Unconfirmed edited name must not silently be replaced");
            naming.selectedName.setText("华粮·静海雅居");naming.armSelectedName();
            if(!"华粮·静海雅居".equals(naming.nameForCreation(java.util.List.of())))throw new AssertionError("Selected candidate must be used verbatim");
            // Failure/cancel before success must retain the selected name.
            if(!"华粮·静海雅居".equals(naming.nameForCreation(java.util.List.of())))throw new AssertionError("Pending name consumed early");
            boolean collision=false;try{naming.nameForCreation(java.util.List.of("华粮静海雅居"));}catch(IllegalArgumentException ex){collision=true;}
            if(!collision)throw new AssertionError("Normalized name conflict not caught");
            naming.creationSucceeded("华粮·静海雅居");if(!naming.selectedName.getText().isEmpty())throw new AssertionError("Successful creation must clear pending name");
            naming.selectedName.setText("旧候选");naming.armSelectedName();naming.district.setText("望川区");
            if(!naming.selectedName.getText().isEmpty())throw new AssertionError("Context edit must invalidate selection");
            if(naming.nameForCreation(java.util.List.of())==null)throw new AssertionError("Unselected name should randomize when enabled");
            naming.setKind(PlaceNameGenerator.Kind.OTHER);if(naming.nameForCreation(java.util.List.of())!=null)throw new AssertionError("Unsupported category named");
            naming.setKind(PlaceNameGenerator.Kind.RESIDENTIAL);naming.generateCandidates();naming.list.setSelectedIndex(2);naming.armSelectedName();
            naming.setSize(540,720);layoutAll(naming);
            java.awt.image.BufferedImage screenshot=new java.awt.image.BufferedImage(540,720,java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics=screenshot.createGraphics();naming.printAll(graphics);graphics.dispose();
            try{javax.imageio.ImageIO.write(screenshot,"png",new File("build/naming-panel-preview.png"));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
            System.out.println("PASS: panel/mode startup; candidate selection, manual editing, confirmation, cancellation, duplicate guards, context invalidation, opt-in");
            mode.destroy();
        });
        System.exit(0);
    }
    private static void layoutAll(java.awt.Container c){c.doLayout();for(java.awt.Component child:c.getComponents())if(child instanceof java.awt.Container nested)layoutAll(nested);}
}
