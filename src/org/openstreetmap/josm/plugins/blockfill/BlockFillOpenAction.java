package org.openstreetmap.josm.plugins.blockfill;

import javax.swing.*;
import java.awt.event.ActionEvent;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.Logging;

/** Menu fallback for the map toolbar action. */
public final class BlockFillOpenAction extends AbstractAction {
    private final BlockFillPlugin plugin;
    BlockFillOpenAction(BlockFillPlugin plugin) {
        super("街区填充…", BlockFillIcons.icon());
        this.plugin=plugin;
        putValue(SHORT_DESCRIPTION,"打开街区填充面板（Alt+Shift+L 激活填充工具）");
        putValue(ACCELERATOR_KEY,KeyStroke.getKeyStroke("alt shift L"));
    }
    @Override public void actionPerformed(ActionEvent event) {
        try { plugin.showPanel(); }
        catch(RuntimeException ex){Logging.error(ex);JOptionPane.showMessageDialog(MainApplication.getMainFrame(),"街区填充启动失败："+ex.getMessage(),"街区填充",JOptionPane.ERROR_MESSAGE);}
    }
}
