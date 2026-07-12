/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.pepsoft.worldpainter;

import org.pepsoft.util.DesktopUtils;
import org.pepsoft.worldpainter.util.FileFilter;
import org.pepsoft.worldpainter.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.pepsoft.util.swing.MessageUtils.beepAndShowError;
import static org.pepsoft.util.swing.MessageUtils.showInfo;

/**
 *
 * @author Pepijn Schmitz
 */
public class MixedMaterialHelper {
    private MixedMaterialHelper() {
        // Prevent instantiation
    }
    
    public static MixedMaterial load(Component parent) {
        Configuration config = Configuration.getInstance();
        File terrainDirectory = config.getTerrainDirectory();
        if ((terrainDirectory == null) || (! terrainDirectory.isDirectory())) {
            terrainDirectory = DesktopUtils.getDocumentsFolder();
        }
        File selectedFile = FileUtils.selectFileForOpen(SwingUtilities.getWindowAncestor(parent), org.pepsoft.worldpainter.WPI18n.s("ui.dlg.selectTerrainFile"), terrainDirectory, new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".terrain");
            }

            @Override
            public String getDescription() {
                return WPI18n.s("ui.filter.worldPainterCustomTerrains");
            }

            @Override
            public String getExtensions() {
                return "*.terrain";
            }
        });
        if (selectedFile != null) {
            try {
                try (ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(selectedFile))))) {
                    return MixedMaterial.duplicateNewMaterialsWhile(() -> (MixedMaterial) in.readObject());
                }
            } catch (IOException e) {
                logger.error("{} while reading {}", e.getClass().getSimpleName(), selectedFile, e);
                beepAndShowError(parent,
                        java.text.MessageFormat.format(WPI18n.s("ui.customTerrain.readInputError.message"), e.getMessage()),
                        WPI18n.s("ui.inputError.title"));
            } catch (ClassCastException e) {
                logger.error("{} while reading {}", e.getClass().getSimpleName(), selectedFile, e);
                beepAndShowError(parent, WPI18n.s("ui.customTerrain.invalidFile.message"), WPI18n.s("ui.invalidFile.title"));
            }
        }
        return null;
    }

    public static MixedMaterial[] loadMultiple(Component parent) {
        Configuration config = Configuration.getInstance();
        File terrainDirectory = config.getTerrainDirectory();
        if ((terrainDirectory == null) || (! terrainDirectory.isDirectory())) {
            terrainDirectory = DesktopUtils.getDocumentsFolder();
        }
        File[] selectedFiles = FileUtils.selectFilesForOpen(SwingUtilities.getWindowAncestor(parent), org.pepsoft.worldpainter.WPI18n.s("ui.dlg.selectTerrainFiles"), terrainDirectory, new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".terrain");
            }

            @Override
            public String getDescription() {
                return WPI18n.s("ui.filter.worldPainterCustomTerrains");
            }

            @Override
            public String getExtensions() {
                return "*.terrain";
            }
        });
        if (selectedFiles != null) {
            return MixedMaterial.duplicateNewMaterialsWhile(() -> {
                final List<MixedMaterial> materials = new ArrayList<>(selectedFiles.length);
                for (File selectedFile: selectedFiles) {
                    try {
                        try (ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(selectedFile))))) {
                            materials.add((MixedMaterial) in.readObject());
                        }
                    } catch (IOException e) {
                        logger.error("{} while reading {}", e.getClass().getSimpleName(), selectedFile, e);
                        beepAndShowError(parent,
                                java.text.MessageFormat.format(WPI18n.s("ui.customTerrain.readInputErrorWithFile.message"), selectedFile, e.getMessage()),
                                WPI18n.s("ui.inputError.title"));
                    } catch (ClassCastException e) {
                        logger.error("{} while reading {}", e.getClass().getSimpleName(), selectedFile, e);
                        beepAndShowError(parent,
                                java.text.MessageFormat.format(WPI18n.s("ui.customTerrain.invalidFileWithName.message"), selectedFile),
                                WPI18n.s("ui.invalidFile.title"));
                    }
                }
                return (! materials.isEmpty()) ? materials.toArray(new MixedMaterial[materials.size()]) : null;
            });
        }
        return null;
    }
    
    public static void save(Component parent, MixedMaterial material) {
        Configuration config = Configuration.getInstance();
        File terrainDirectory = config.getTerrainDirectory();
        if ((terrainDirectory == null) || (! terrainDirectory.isDirectory())) {
            terrainDirectory = DesktopUtils.getDocumentsFolder();
        }
        File selectedFile = new File(terrainDirectory, org.pepsoft.util.FileUtils.sanitiseName(material.getName()) + ".terrain");
        selectedFile = FileUtils.selectFileForSave(SwingUtilities.getWindowAncestor(parent), org.pepsoft.worldpainter.WPI18n.s("ui.dlg.exportTerrainFile"), selectedFile, new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".terrain");
            }

            @Override
            public String getDescription() {
                return WPI18n.s("ui.filter.worldPainterCustomTerrains");
            }

            @Override
            public String getExtensions() {
                return "*.terrain";
            }
        });
        if (selectedFile != null) {
            if (! selectedFile.getName().toLowerCase().endsWith(".terrain")) {
                selectedFile = new File(selectedFile.getPath() + ".terrain");
            }
            if (selectedFile.isFile() && (JOptionPane.showConfirmDialog(parent, org.pepsoft.worldpainter.WPI18n.s("ui.message.filePrefix") + selectedFile.getName() + org.pepsoft.worldpainter.WPI18n.s("ui.confirm.fileAlreadyExistsSuffix"), org.pepsoft.worldpainter.WPI18n.s("ui.dialog.overwriteFile.title"), JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)) {
                return;
            }
            try {
                try (ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(selectedFile))))) {
                    out.writeObject(material);
                }
            } catch (IOException e) {
                throw new RuntimeException("I/O error while trying to write " + selectedFile, e);
            }
            config.setTerrainDirectory(selectedFile.getParentFile());
            showInfo(parent,
                    java.text.MessageFormat.format(WPI18n.s("ui.customTerrain.exported.message"), material.getName()),
                    WPI18n.s("ui.success.title"));
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(MixedMaterialHelper.class);
}
