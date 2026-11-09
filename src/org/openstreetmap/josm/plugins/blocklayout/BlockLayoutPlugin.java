package org.openstreetmap.josm.plugins.blocklayout;

import org.openstreetmap.josm.gui.IconToggleButton;
import javax.swing.JOptionPane;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.Logging;

/** Standalone JOSM plugin for building layout; it does not depend on or modify BlockFill. */
public final class BlockLayoutPlugin extends Plugin {
    private BlockLayoutPanel panel;
    private BlockLayoutMode mode;
    private IconToggleButton button;
    public BlockLayoutPlugin(PluginInformation info) {
        super(info);
        // No separate toolsMenu entry — the toggle dialog panel and the map-mode toolbar button
        // are the sole entry points, matching JOSM conventions for editing tools.
    }

    @Override public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if(oldFrame!=null && oldFrame!=newFrame) {
            if(panel!=null) oldFrame.removeToggleDialog(panel);
            if(mode!=null) { if(button!=null)oldFrame.allMapModeButtons.remove(button);mode.exitMode(); }
            panel=null;mode=null;button=null;
        }
        if(newFrame==null)return;
        try {
            panel=new BlockLayoutPanel();mode=new BlockLayoutMode(panel);panel.attach(mode);
            newFrame.addToggleDialog(panel);button=new IconToggleButton(mode);newFrame.addMapMode(button);
        } catch(RuntimeException ex) {
            panel=null;mode=null;button=null;Logging.error("建筑排布插件初始化失败",ex);
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(),"建筑排布插件初始化失败：\n"+ex.getMessage(),"建筑排布",JOptionPane.ERROR_MESSAGE);
        }
    }
}
