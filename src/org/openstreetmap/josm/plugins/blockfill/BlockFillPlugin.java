package org.openstreetmap.josm.plugins.blockfill;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JOptionPane;
import org.openstreetmap.josm.data.preferences.sources.SourceEntry;
import org.openstreetmap.josm.data.preferences.sources.SourceType;
import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.Logging;

public final class BlockFillPlugin extends Plugin {
    private BlockFillPanel panel;
    private BlockFillMode mode;
    private IconToggleButton button;
    private final javax.swing.Action openAction;

    public BlockFillPlugin(PluginInformation info) {
        super(info);
        openAction = new BlockFillOpenAction(this);
        MainApplication.getMenu().toolsMenu.add(openAction);
        prepareResources();
    }

    private void prepareResources() {
        try {
            Path user = Config.getDirs().getUserDataDirectory(true).toPath();
            Path catalog = user.resolve("blockfill-landuse.tsv");
            if (!Files.exists(catalog) || !Files.readString(catalog).contains("blockfill-catalog-version=2")) {
                try (InputStream in = getClass().getResourceAsStream("/landuse.tsv")) { if (in != null) Files.copy(in, catalog, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            }
            Path style = user.resolve("blockfill.mapcss");
            if (!Files.exists(style)) {
                try (InputStream in = getClass().getResourceAsStream("/blockfill.mapcss")) { if (in != null) Files.copy(in, style); }
            }
            if (Files.exists(catalog)) System.setProperty("blockfill.catalog", catalog.toString());
            if (Files.exists(style)) {
                SourceEntry entry = new SourceEntry(SourceType.MAP_PAINT_STYLE, style.toUri().toString(), "街区填充预览", null, true);
                MapPaintStyles.addStyle(entry);
                MapPaintStyles.readFromPreferences();
            }
        } catch (IOException | RuntimeException ex) { Logging.error("街区填充资源初始化失败", ex); }
    }

    void showPanel() {
        if (panel == null) throw new IllegalStateException("JOSM 地图界面尚未初始化，请先打开地图数据。");
        panel.showDialog();
        if (mode != null && MainApplication.getMap() != null) MainApplication.getMap().selectMapMode(mode);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame != null && oldFrame != newFrame) {
            if (panel != null) oldFrame.removeToggleDialog(panel);
            if (mode != null) {
                if (button != null) oldFrame.allMapModeButtons.remove(button);
                mode.exitMode();
            }
            panel = null; mode = null; button = null;
        }
        if (newFrame == null) return;
        try {
            panel = new BlockFillPanel(); mode = new BlockFillMode(panel); panel.attach(mode);
            newFrame.addToggleDialog(panel);
            button = new IconToggleButton(mode); newFrame.addMapMode(button);
        } catch (RuntimeException ex) {
            panel = null; mode = null; button = null;
            Logging.error("街区填充界面初始化失败", ex);
            JOptionPane.showMessageDialog(MainApplication.getMainFrame(), "街区填充工具初始化失败：\n" + ex.getMessage() + "\n请查看 帮助 → 显示日志。", "街区填充", JOptionPane.ERROR_MESSAGE);
        }
    }
}
