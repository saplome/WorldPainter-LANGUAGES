/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * ExportWorldDialog.java
 *
 * Created on Mar 29, 2011, 5:09:50 PM
 */

package org.pepsoft.worldpainter;

import org.pepsoft.minecraft.ChunkFactory;
import org.pepsoft.util.*;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.Version;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.exporting.WorldExportSettings;
import org.pepsoft.worldpainter.exporting.WorldExporter;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.plugins.PlatformManager;
import org.pepsoft.worldpainter.util.FileInUseException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Comparator.comparingLong;
import static org.pepsoft.minecraft.Constants.*;
import static org.pepsoft.util.ExceptionUtils.chainContains;
import static org.pepsoft.worldpainter.Constants.V_1_17;
import static org.pepsoft.worldpainter.DefaultPlugin.*;

/**
 *
 * @author pepijn
 */
public class ExportProgressDialog extends MultiProgressDialog<Map<Integer, ChunkFactory.Stats>> implements WindowListener {
    /** Creates new form ExportWorldDialog */
    public ExportProgressDialog(Window parent, World2 world, WorldExportSettings exportSettings, File baseDir, String name, String acknowledgedWarnings) {
        super(parent, org.pepsoft.worldpainter.WPI18n.s("ui.export.progress.title"));
        this.world = world;
        this.baseDir = baseDir;
        this.name = name;
        this.exportSettings = exportSettings;
        this.acknowledgedWarnings = acknowledgedWarnings;
        addWindowListener(this);

        JButton minimiseButton = new JButton(org.pepsoft.worldpainter.WPI18n.s("ui.d27532d90e"));
        minimiseButton.addActionListener(e -> App.getInstance().setState(Frame.ICONIFIED));
        addButton(minimiseButton);
    }

    public boolean isAllowRetry() {
        return allowRetry;
    }

    // WindowListener

    @Override
    public void windowClosed(WindowEvent e) {
        // Make sure to clean up any progress that is still showing
        DesktopUtils.setProgressDone(App.getInstance());
    }

    @Override public void windowClosing(WindowEvent e) {}
    @Override public void windowOpened(WindowEvent e) {}
    @Override public void windowIconified(WindowEvent e) {}
    @Override public void windowDeiconified(WindowEvent e) {}
    @Override public void windowActivated(WindowEvent e) {}
    @Override public void windowDeactivated(WindowEvent e) {}

    // MultiProgressDialog

    @Override
    protected String getVerb() {
        return org.pepsoft.worldpainter.WPI18n.s("ui.export.verb");
    }

    @Override
    protected String getResultsReport(Map<Integer, ChunkFactory.Stats> result, long duration) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html>").append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.exportedAs"), new File(baseDir, FileUtils.sanitiseName(name))));
        int hours = (int) (duration / 3600);
        duration = duration - hours * 3600L;
        int minutes = (int) (duration / 60);
        int seconds = (int) (duration - minutes * 60);
        sb.append("<br>").append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.took"), hours + ":" + ((minutes < 10) ? "0" : "") + minutes + ":" + ((seconds < 10) ? "0" : "") + seconds));
        final Platform platform = world.getPlatform();
        final Version mcVersion = platform.getAttribute(ATTRIBUTE_MC_VERSION);
        if ((platform == JAVA_MCREGION) && (world.getMaxHeight() != DEFAULT_MAX_HEIGHT_MCREGION)) {
            sb.append(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.nonStandardHeight.warning"));
        } else if ((mcVersion.isAtLeast(V_1_17)) && ((world.getMaxHeight() - world.getMinHeight()) > 384)) {
            sb.append(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.heightOver384.warning"));
        }
        if ((platform == JAVA_ANVIL_1_17) && ((world.getMinHeight() != DEFAULT_MIN_HEIGHT) || (world.getMaxHeight() != DEFAULT_MAX_HEIGHT_ANVIL))) {
            sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.dataPackHeight.incompatible.warning"), "Minecraft 1.17"));
        } else if ((world.getMinHeight() != DEFAULT_MIN_HEIGHT_1_18) || (world.getMaxHeight() != DEFAULT_MAX_HEIGHT_1_18)) {
            if ((platform == JAVA_ANVIL_1_18) || (platform == JAVA_ANVIL_1_19)) {
                sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.dataPackHeight.warning"), "Minecraft 1.18.2 - 1.20.4"));
            } else if (platform == JAVA_ANVIL_1_20_5) {
                sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.dataPackHeight.warning"), "Minecraft 1.20.5 - 1.21.10"));
            } else if (platform == JAVA_ANVIL_1_21_11) {
                sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.dataPackHeight.warning"), "Minecraft 1.21.11"));
            } else if (platform == JAVA_ANVIL_26_1) {
                sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.dataPackHeight.warning"), "Minecraft 26.1 - 26.2"));
            }
        }
        if (result.size() == 1) {
            ChunkFactory.Stats stats = result.get(result.keySet().iterator().next());
            sb.append("<br><br>").append(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.statistics")).append("<br>");
            dumpStats(sb, stats, world.getMaxHeight() - world.getMinHeight());
            if ((stats.timings != null) && (! stats.timings.isEmpty())) {
                sb.append("<br>").append(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.threeLongest"));
                sb.append("<table><tr><th>").append(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.stage"))
                        .append("</th><th>").append(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.description"))
                        .append("</th><th>").append(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.duration"))
                        .append("</th></tr>");
                stats.timings.entrySet().stream()
                        .sorted(comparingLong((Map.Entry<Object, AtomicLong> entry) -> entry.getValue().longValue()).reversed())
                        .limit(3)
                        .forEach(entry -> sb.append("<tr><td>")
                                .append(formatTimingLabel(entry.getKey()))
                                .append("</td><td>")
                                .append(formatTimingDescription(entry.getKey()))
                                .append("</td><td>")
                                .append(formatTimingValue(entry.getValue().longValue()))
                                .append("</td></tr>"));
                sb.append("</table><br>");
            }
        } else {
            for (Map.Entry<Integer, ChunkFactory.Stats> entry: result.entrySet()) {
                final int dim = entry.getKey();
                final int height;
                ChunkFactory.Stats stats = entry.getValue();
                switch (dim) {
                    case Constants.DIM_NORMAL:
                        sb.append("<br><br>").append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.statisticsFor"), org.pepsoft.worldpainter.WPI18n.s("ui.dimension.surface").toLowerCase())).append("<br>");
                        height = world.getMaxHeight() - world.getMinHeight();
                        break;
                    case Constants.DIM_NETHER:
                        sb.append("<br><br>").append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.statisticsFor"), org.pepsoft.worldpainter.WPI18n.s("ui.dimension.nether"))).append("<br>");
                        height = DEFAULT_MAX_HEIGHT_NETHER;
                        break;
                    case Constants.DIM_END:
                        sb.append("<br><br>").append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.statisticsFor"), org.pepsoft.worldpainter.WPI18n.s("ui.dimension.end"))).append("<br>");
                        height = DEFAULT_MAX_HEIGHT_END;
                        break;
                    default:
                        sb.append("<br><br>").append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.statisticsForDimension"), dim)).append("<br>");
                        height = world.getMaxHeight() - world.getMinHeight();
                        break;
                }
                dumpStats(sb, stats, height);
            }
        }
        if ((backupDir != null) && backupDir.isDirectory()) {
            sb.append("<br>").append(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.backupCreated")).append("<br>").append(backupDir);
        }
        if ((acknowledgedWarnings != null) && (! acknowledgedWarnings.trim().isEmpty())) {
            sb.append("<br><br><em>").append(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.acknowledgedWarnings")).append("</em>");
            sb.append(acknowledgedWarnings);
        }
        sb.append("</html>");
        return sb.toString();
    }

    @Override
    protected String getCancellationMessage() {
        return org.pepsoft.worldpainter.WPI18n.s("ui.export.cancelled.message")
                + (((backupDir != null) && backupDir.isDirectory()) ? ("\n\n" + org.pepsoft.worldpainter.WPI18n.s("ui.export.cancelled.backupCreated") + "\n" + backupDir) : "");
    }

    @Override
    protected ProgressTask<Map<Integer, ChunkFactory.Stats>> getTask() {
        return new ProgressTask<Map<Integer, ChunkFactory.Stats>>() {
            @Override
            public String getName() {
                return MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.progress.exportingWorld"), name);
            }

            @Override
            public Map<Integer, ChunkFactory.Stats> execute(ProgressReceiver progressReceiver) throws OperationCancelled {
                progressReceiver = new TaskbarProgressReceiver(App.getInstance(), progressReceiver);
                progressReceiver.setMessage(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.progress.exportingWorld"), name));
                WorldExporter exporter = PlatformManager.getInstance().getExporter(world, exportSettings);
                try {
                    backupDir = exporter.selectBackupDir(baseDir, name);
                    return exporter.export(baseDir, name, backupDir, progressReceiver);
                } catch (IOException e) {
                    throw new RuntimeException("I/O error while exporting world", e);
                } catch (RuntimeException e) {
                    if (chainContains(e, FileInUseException.class)) {
                        allowRetry = true;
                    }
                    throw e;
                }
            }
        };
    }

    private void dumpStats(final StringBuilder sb, final ChunkFactory.Stats stats, final int height) {
        final NumberFormat formatter = NumberFormat.getIntegerInstance();
        final long duration = stats.time / 1000;
        if (stats.landArea > 0) {
            sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.landArea"), formatter.format(stats.landArea))).append("<br>");
        }
        if (stats.waterArea > 0) {
            sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.waterOrLavaArea"), formatter.format(stats.waterArea))).append("<br>");
            if (stats.landArea > 0) {
                sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.totalSurfaceArea"), formatter.format(stats.landArea + stats.waterArea))).append("<br>");
            }
        }
        final long totalBlocks = stats.surfaceArea * height;
        if (duration > 0) {
            sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.generatedWithRate"), formatter.format(totalBlocks), formatter.format(totalBlocks / duration))).append("<br>");
            if (stats.size > 0) {
                final long kbPerSecond = stats.size / duration / 1024;
                sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.mapSizeWithRate"), formatter.format(stats.size / 1024 / 1024), ((kbPerSecond < 1024) ? (formatter.format(kbPerSecond) + " KB") : (formatter.format(kbPerSecond / 1024) + " MB")))).append("<br>");
            }
        } else {
            sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.generated"), formatter.format(totalBlocks))).append("<br>");
            if (stats.size > 0) {
                sb.append(MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.export.result.mapSize"), formatter.format(stats.size / 1024 / 1024))).append("<br>");
            }
        }
    }

    private String formatTimingLabel(Object label) {
        if (label instanceof Layer) {
            return ((Layer) label).getName();
        } else if (label instanceof ChunkFactory.Stage) {
            return ((ChunkFactory.Stage) label).getName();
        } else {
            return label.toString();
        }
    }

    private String formatTimingDescription(Object label) {
        if (label instanceof Layer) {
            return ((Layer) label).getDescription();
        } else if (label instanceof ChunkFactory.Stage) {
            return ((ChunkFactory.Stage) label).getDescription();
        } else {
            return "";
        }
    }

    private String formatTimingValue(long value) {
        return MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.duration.seconds.short"), formatter.format(value / 1000000000L));
    }

    private final World2 world;
    private final String name, acknowledgedWarnings;
    private final File baseDir;
    private final WorldExportSettings exportSettings;
    private final NumberFormat formatter = NumberFormat.getIntegerInstance();
    private volatile File backupDir;
    private volatile boolean allowRetry = false;
    
    private static final long serialVersionUID = 1L;
}
