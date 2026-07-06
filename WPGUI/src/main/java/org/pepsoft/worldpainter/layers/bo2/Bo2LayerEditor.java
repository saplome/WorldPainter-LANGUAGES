/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.pepsoft.worldpainter.layers.bo2;

import org.pepsoft.minecraft.Material;
import org.pepsoft.util.DesktopUtils;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.WPI18n;
import org.pepsoft.worldpainter.layers.AbstractLayerEditor;
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.layers.exporters.ExporterSettings;
import org.pepsoft.worldpainter.objects.WPObject;
import org.pepsoft.worldpainter.plugins.CustomObjectManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.vecmath.Point3i;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;

import static java.lang.Math.round;
import static java.lang.String.format;
import static org.pepsoft.minecraft.Material.PERSISTENT;
import static org.pepsoft.util.swing.MessageUtils.*;
import static org.pepsoft.worldpainter.ExceptionHandler.doWithoutExceptionReporting;
import static org.pepsoft.worldpainter.Platform.Capability.NAME_BASED;
import static org.pepsoft.worldpainter.objects.WPObject.*;

/**
 *
 * @author Pepijn Schmitz
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"}) // Managed by NetBeans
public class Bo2LayerEditor extends AbstractLayerEditor<Bo2Layer> implements ListSelectionListener, DocumentListener {
    /**
     * Creates new form Bo2LayerEditor
     */
    public Bo2LayerEditor() {
        initComponents();
        
        listModel = new DefaultListModel<>();
        listObjects.setModel(listModel);
        listObjects.setCellRenderer(new WPObjectListCellRenderer());
        
        listObjects.getSelectionModel().addListSelectionListener(this);
        fieldName.getDocument().addDocumentListener(this);

        updateBlocksPerAttempt();
    }

    // LayerEditor
    
    @Override
    public Bo2Layer createLayer() {
        return new Bo2Layer(new Bo2ObjectTube(WPI18n.s("ui.bo2.defaultLayerName"), Collections.emptyList()), WPI18n.s("ui.bo2.defaultLayerDescription"), Color.ORANGE);
    }

    @Override
    public void setLayer(Bo2Layer layer) {
        super.setLayer(layer);
        reset();
    }

    @Override
    public void commit() {
        if (! isCommitAvailable()) {
            throw new IllegalStateException("Settings invalid or incomplete");
        }
        saveSettings(layer);
    }
    
    @Override
    public void reset() {
        List<WPObject> objects = new ArrayList<>();
        fieldName.setText(layer.getName());
        paintPicker1.setPaint(layer.getPaint());
        paintPicker1.setOpacity(layer.getOpacity());
        List<File> files = layer.getFiles();
        if (files != null) {
            if (files.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Existing layer contains new style objects");
                }
                // New layer; files stored in object attributes
                objects.addAll(layer.getObjectProvider().getAllObjects());
            } else {
                // Old layer; files stored separately
                int missingFiles = 0;
                CustomObjectManager customObjectManager = CustomObjectManager.getInstance();
                if ((files.size() == 1) && files.get(0).isDirectory()) {
                    logger.info("Existing custom object layer contains old style directory; migrating to new style");
                    File[] filesInDir = files.get(0).listFiles((FilenameFilter) CustomObjectManager.getInstance().getFileFilter());
                    //noinspection ConstantConditions // Cannot happen as we already checked that files.get(0) is an extant directory
                    for (File file: filesInDir) {
                        try {
                            objects.add(customObjectManager.loadObject(file));
                        } catch (IOException e) {
                            logger.error("I/O error while trying to load custom object " + file, e);
                            missingFiles++;
                        }
                    }
                } else {
                    logger.info("Existing custom object layer contains old style file list; migrating to new style");
                    for (File file: files) {
                        if (file.exists()) {
                            try {
                                objects.add(customObjectManager.loadObject(file));
                            } catch (IOException e) {
                                logger.error("I/O error while trying to load custom object " + file, e);
                                missingFiles++;
                            }
                        } else {
                            missingFiles++;
                        }
                    }
                }
                if (missingFiles > 0) {
                    showWarning(this,
                            java.text.MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.bo2.missingFiles.message"), missingFiles),
                            org.pepsoft.worldpainter.WPI18n.s("ui.bo2.missingFiles.title"));
                }
            }
        } else {
            logger.info("Existing custom object layer contains very old style objects with no file information; migrating to new style");
            // Very old layer; no file information at all
            objects.addAll(layer.getObjectProvider().getAllObjects());
        }
        listModel.clear();
        for (WPObject object: objects) {
            listModel.addElement(object.clone());
        }
        spinnerBlocksPerAttempt.setValue(layer.getDensity());
        spinnerGrid.setValue(layer.getGridX());
        spinnerRandomOffset.setValue(layer.getRandomDisplacement());
        
        refreshLeafDecaySettings();
        
        settingsChanged();
    }

    @Override
    public ExporterSettings getSettings() {
        if (! isCommitAvailable()) {
            throw new IllegalStateException("Settings invalid or incomplete");
        }
        final Bo2Layer previewLayer = saveSettings(null);
        return new ExporterSettings() {
            @Override
            public boolean isApplyEverywhere() {
                return false;
            }

            @Override
            public Bo2Layer getLayer() {
                return previewLayer;
            }

            @Override
            public ExporterSettings clone() {
                throw new UnsupportedOperationException("Not supported");
            }
        };
    }

    @Override
    public boolean isCommitAvailable() {
        boolean filesSelected = listModel.getSize() > 0;
        boolean nameSpecified = fieldName.getText().trim().length() > 0;
        return filesSelected && nameSpecified;
    }

    @Override
    public void setContext(LayerEditorContext context) {
        super.setContext(context);
        colourScheme = context.getColourScheme();
    }
        
    // ListSelectionListener
    
    @Override
    public void valueChanged(ListSelectionEvent e) {
        settingsChanged();
    }

    // DocumentListener
    
    @Override
    public void insertUpdate(DocumentEvent e) {
        settingsChanged();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        settingsChanged();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        settingsChanged();
    }

    private Bo2Layer saveSettings(Bo2Layer layer) {
        String name = fieldName.getText();
        List<WPObject> objects = new ArrayList<>(listModel.getSize());
        for (int i = 0; i < listModel.getSize(); i++) {
            objects.add(listModel.getElementAt(i));
        }
        Bo2ObjectProvider objectProvider = new Bo2ObjectTube(name, objects);
        if (layer == null) {
            layer = new Bo2Layer(objectProvider, WPI18n.s("ui.bo2.defaultLayerDescription"), paintPicker1.getPaint());
        } else {
            layer.setObjectProvider(objectProvider);
            layer.setPaint(paintPicker1.getPaint());
        }
        layer.setOpacity(paintPicker1.getOpacity());
        layer.setDensity((Integer) spinnerBlocksPerAttempt.getValue());
        layer.setGridX((Integer) spinnerGrid.getValue());
        layer.setGridY((Integer) spinnerGrid.getValue());
        layer.setRandomDisplacement((Integer) spinnerRandomOffset.getValue());
        return layer;
    }

    private void settingsChanged() {
        setControlStates();
        context.settingsChanged();
    }
    
    private void setControlStates() {
        boolean filesSelected = listModel.getSize() > 0;
        boolean objectsSelected = listObjects.getSelectedIndex() != -1;
        buttonRemoveFile.setEnabled(objectsSelected);
        buttonReloadAll.setEnabled(filesSelected);
        buttonEdit.setEnabled(objectsSelected);
    }
    
    private void addFilesOrDirectory() {
        // Can't use FileUtils.selectFilesForOpen() because it doesn't support
        // selecting directories, or adding custom components to the dialog
        JFileChooser fileChooser = new JFileChooser();
        Configuration config = Configuration.getInstance();
        if ((config.getCustomObjectsDirectory() != null) && config.getCustomObjectsDirectory().isDirectory()) {
            fileChooser.setCurrentDirectory(config.getCustomObjectsDirectory());
        }
        fileChooser.setDialogTitle(org.pepsoft.worldpainter.WPI18n.s("ui.dlg.selectFilesOrDir"));
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        CustomObjectManager.UniversalFileFilter fileFilter = CustomObjectManager.getInstance().getFileFilter();
        fileChooser.setFileFilter(fileFilter);
        WPObjectPreviewer previewer = new WPObjectPreviewer();
        previewer.setDimension(App.getInstance().getDimension());
        fileChooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY, previewer);
        fileChooser.setAccessory(previewer);
        if (doWithoutExceptionReporting(() -> fileChooser.showOpenDialog(this)) == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            if (selectedFiles.length > 0) {
                Platform platform = context.getDimension().getWorld().getPlatform();
                boolean checkForNameOnlyMaterials = ! platform.capabilities.contains(NAME_BASED);
                Set<String> nameOnlyMaterialsNames = checkForNameOnlyMaterials ? new HashSet<>() : null;
                config.setCustomObjectsDirectory(selectedFiles[0].getParentFile());
                for (File selectedFile: selectedFiles) {
                    if (selectedFile.isDirectory()) {
                        if (fieldName.getText().isEmpty()) {
                            String name = selectedFiles[0].getName();
                            if (name.length() > 12) {
                                name = "..." + name.substring(name.length() - 10);
                            }
                            fieldName.setText(name);
                        }
                        File[] files = selectedFile.listFiles((FilenameFilter) fileFilter);
                        if (files == null) {
                            beepAndShowError(this,
                                    java.text.MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.bo2.notValidDirectory.message"), selectedFile.getName()),
                                    org.pepsoft.worldpainter.WPI18n.s("ui.bo2.notValidDirectory.title"));
                        } else if (files.length == 0) {
                            beepAndShowError(this,
                                    java.text.MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.bo2.noCustomObjectFiles.message"), selectedFile.getName()),
                                    org.pepsoft.worldpainter.WPI18n.s("ui.bo2.noCustomObjectFiles.title"));
                        } else {
                            for (File file: files) {
                                addFile(checkForNameOnlyMaterials, nameOnlyMaterialsNames, file);
                            }
                        }
                    } else {
                        if (fieldName.getText().isEmpty()) {
                            String name = selectedFile.getName();
                            int p = name.lastIndexOf('.');
                            if (p != -1) {
                                name = name.substring(0, p);
                            }
                            if (name.length() > 12) {
                                name = "..." + name.substring(name.length() - 10);
                            }
                            fieldName.setText(name);
                        }
                        addFile(checkForNameOnlyMaterials, nameOnlyMaterialsNames, selectedFile);
                    }
                }
                settingsChanged();
                refreshLeafDecaySettings();
                if (checkForNameOnlyMaterials && (! nameOnlyMaterialsNames.isEmpty())) {
                    final String materialNames;
                    if (nameOnlyMaterialsNames.size() > 4) {
                        materialNames = MessageFormat.format(WPI18n.s("ui.bo2.incompatibleMaterials.more"),
                                String.join(", ", new ArrayList<>(nameOnlyMaterialsNames).subList(0, 3)),
                                nameOnlyMaterialsNames.size() - 3);
                    } else {
                        materialNames = String.join(", ", nameOnlyMaterialsNames);
                    }
                    beepAndShowWarning(this,
                            MessageFormat.format(WPI18n.s("ui.bo2.incompatibleMaterials.message"), platform.displayName, materialNames),
                            WPI18n.s("ui.bo2.incompatibleMaterials.title"));
                }
            }
        }
    }

    private void addFile(boolean checkForNameOnlyMaterials, Set<String> nameOnlyMaterialsNames, File file) {
        try {
            WPObject object = CustomObjectManager.getInstance().loadObject(file);
            if (checkForNameOnlyMaterials) {
                Set<String> materialNamesEncountered = new HashSet<>();
                object.visitBlocks((o, x, y, z, material) -> {
                    if (! materialNamesEncountered.contains(material.name)) {
                        materialNamesEncountered.add(material.name);
                        if (material.blockType == -1) {
                            nameOnlyMaterialsNames.add(material.name);
                        }
                    }
                    return true;
                });
            }
            listModel.addElement(object);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException while trying to load custom object " + file, e);
            JOptionPane.showMessageDialog(this, e.getMessage() + org.pepsoft.worldpainter.WPI18n.s("ui.h.8b6de33632") + file.getName() + org.pepsoft.worldpainter.WPI18n.s("ui.h.08483fa90c"), org.pepsoft.worldpainter.WPI18n.s("ui.h.dbb734c5a8"), JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            logger.error("I/O error while trying to load custom object " + file, e);
            JOptionPane.showMessageDialog(this, org.pepsoft.worldpainter.WPI18n.s("ui.h.3d3f9a4d91") + file.getName() + org.pepsoft.worldpainter.WPI18n.s("ui.h.08483fa90c"), org.pepsoft.worldpainter.WPI18n.s("ui.h.2f09b5309e"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeFiles() {
        int[] selectedIndices = listObjects.getSelectedIndices();
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            listModel.removeElementAt(selectedIndices[i]);
        }
        settingsChanged();
        refreshLeafDecaySettings();
    }

    private void reloadObjects() {
        StringBuilder noFiles = new StringBuilder();
        StringBuilder notFound = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        int[] indices;
        if (listObjects.getSelectedIndex() != -1) {
            indices = listObjects.getSelectedIndices();
        } else {
            indices = new int[listModel.getSize()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = i;
            }
        }
        CustomObjectManager customObjectManager = CustomObjectManager.getInstance();
        for (int index: indices) {
            WPObject object = listModel.getElementAt(index);
            File file = object.getAttribute(ATTRIBUTE_FILE);
            if (file != null) {
                if (file.isFile() && file.canRead()) {
                    try {
                        Map<String, Serializable> existingAttributes = object.getAttributes();
                        object = customObjectManager.loadObject(file);
                        if (existingAttributes != null) {
                            Map<String, Serializable> attributes = object.getAttributes();
                            if (attributes == null) {
                                attributes = new HashMap<>();
                            }
                            attributes.putAll(existingAttributes);
                            object.setAttributes(attributes);
                        }
                        listModel.setElementAt(object, index);
                    } catch (IOException e) {
                        logger.error("I/O error while reloading " + file, e);
                        errors.append(file.getPath()).append('\n');
                    }
                } else {
                    notFound.append(file.getPath()).append('\n');
                }
            } else {
                noFiles.append(object.getName()).append('\n');
            }
        }
        if ((noFiles.length() > 0) || (notFound.length() > 0) || (errors.length() > 0)) {
            StringBuilder message = new StringBuilder();
            message.append(WPI18n.s("ui.bo2.reloadFailed.header"));
            if (noFiles.length() > 0) {
                message.append(WPI18n.s("ui.bo2.reloadFailed.noFiles"));
                message.append(noFiles);
            }
            if (notFound.length() > 0) {
                message.append(WPI18n.s("ui.bo2.reloadFailed.notFound"));
                message.append(notFound);
            }
            if (errors.length() > 0) {
                message.append(WPI18n.s("ui.bo2.reloadFailed.ioErrors"));
                message.append(errors);
            }
            JOptionPane.showMessageDialog(this, message, org.pepsoft.worldpainter.WPI18n.s("ui.h.11b3fed838"), JOptionPane.ERROR_MESSAGE);
        } else {
            showInfo(this,
                    java.text.MessageFormat.format(org.pepsoft.worldpainter.WPI18n.s("ui.bo2.objectsReloaded.message"), indices.length),
                    org.pepsoft.worldpainter.WPI18n.s("ui.success.title"));
        }
        refreshLeafDecaySettings();
    }
    
    private void editObjects() {
        List<WPObject> selectedObjects = new ArrayList<>(listObjects.getSelectedIndices().length);
        int[] selectedIndices = listObjects.getSelectedIndices();
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            selectedObjects.add(listModel.getElementAt(selectedIndices[i]));
        }
        EditObjectAttributes dialog = new EditObjectAttributes(SwingUtilities.getWindowAncestor(this), selectedObjects, colourScheme);
        dialog.setVisible(true);
        if (! dialog.isCancelled()) {
            settingsChanged();
            refreshLeafDecaySettings();
        }
    }

    private void refreshLeafDecaySettings() {
        if (listModel.isEmpty()) {
            labelLeafDecayTitle.setEnabled(false);
            labelEffectiveLeafDecaySetting.setEnabled(false);
            labelEffectiveLeafDecaySetting.setText(org.pepsoft.worldpainter.WPI18n.s("wp.n.a.382b0f5185"));
            buttonSetDecay.setEnabled(false);
            buttonSetNoDecay.setEnabled(false);
            buttonReset.setEnabled(false);
            return;
        }
        boolean decayingLeavesFound = false;
        boolean nonDecayingLeavesFound = false;
        outer:
        for (Enumeration<WPObject> e = listModel.elements(); e.hasMoreElements(); ) {
            WPObject object = e.nextElement();
            int leafDecayMode = object.getAttribute(ATTRIBUTE_LEAF_DECAY_MODE);
            switch (leafDecayMode) {
                case LEAF_DECAY_NO_CHANGE:
                    // Leaf decay attribute not set (or set to "no change"); examine actual blocks
                    object.prepareForExport(context.getDimension());
                    Point3i dim = object.getDimensions();
                    for (int x = 0; x < dim.x; x++) {
                        for (int y = 0; y < dim.y; y++) {
                            for (int z = 0; z < dim.z; z++) {
                                if (object.getMask(x, y, z)) {
                                    final Material material = object.getMaterial(x, y, z);
                                    if (material.leafBlock) {
                                        if (material.is(PERSISTENT)) {
                                            // Non decaying leaf block
                                            nonDecayingLeavesFound = true;
                                            if (decayingLeavesFound) {
                                                // We have enough information; no reason to continue the examination
                                                break outer;
                                            }
                                        } else {
                                            // Decaying leaf block
                                            decayingLeavesFound = true;
                                            if (nonDecayingLeavesFound) {
                                                // We have enough information; no reason to continue the examination
                                                break outer;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                case LEAF_DECAY_OFF:
                    // Leaf decay attribute set to "off"; don't examine blocks for performance (even though this could
                    // lead to misleading information if the object doesn't contain any leaf blocks)
                    nonDecayingLeavesFound = true;
                    if (decayingLeavesFound) {
                        // We have enough information; no reason to continue the examination
                        break outer;
                    }
                    break;
                case LEAF_DECAY_ON:
                    // Leaf decay attribute set to "off"; don't examine blocks for performance (even though this could
                    // lead to misleading information if the object doesn't contain any leaf blocks)
                    decayingLeavesFound = true;
                    if (nonDecayingLeavesFound) {
                        // We have enough information; no reason to continue the examination
                        break outer;
                    }
                    break;
                default:
                    throw new InternalError();
            }
        }

        if (decayingLeavesFound) {
            labelLeafDecayTitle.setEnabled(true);
            labelEffectiveLeafDecaySetting.setEnabled(true);
            buttonSetNoDecay.setEnabled(true);
            buttonReset.setEnabled(true);
            if (nonDecayingLeavesFound) {
                // Both decaying and non decaying leaves found
                labelEffectiveLeafDecaySetting.setText(org.pepsoft.worldpainter.WPI18n.s("wp.html.decaying.i.and.303a8cd146"));
                buttonSetDecay.setEnabled(true);
            } else {
                // Only decaying leaves found
                labelEffectiveLeafDecaySetting.setText(org.pepsoft.worldpainter.WPI18n.s("wp.html.leaves.b.do.5a25f50034"));
                buttonSetDecay.setEnabled(false);
            }
        } else {
            if (nonDecayingLeavesFound) {
                // Only non decaying leaves found
                labelLeafDecayTitle.setEnabled(true);
                labelEffectiveLeafDecaySetting.setEnabled(true);
                labelEffectiveLeafDecaySetting.setText(org.pepsoft.worldpainter.WPI18n.s("wp.html.leaves.do.b.4caa96b58b"));
                buttonSetDecay.setEnabled(true);
                buttonSetNoDecay.setEnabled(false);
                buttonReset.setEnabled(true);
            } else {
                // No leaf blocks encountered at all, so N/A
                labelLeafDecayTitle.setEnabled(false);
                labelEffectiveLeafDecaySetting.setEnabled(false);
                labelEffectiveLeafDecaySetting.setText(org.pepsoft.worldpainter.WPI18n.s("wp.n.a.382b0f5185"));
                buttonSetDecay.setEnabled(false);
                buttonSetNoDecay.setEnabled(false);
                buttonReset.setEnabled(false);
            }
        }
    }

    private void setLeavesDecay() {
        for (Enumeration<WPObject> e = listModel.elements(); e.hasMoreElements(); ) {
            WPObject object = e.nextElement();
            object.setAttribute(ATTRIBUTE_LEAF_DECAY_MODE, LEAF_DECAY_ON);
        }
        refreshLeafDecaySettings();
    }

    private void setLeavesNoDecay() {
        for (Enumeration<WPObject> e = listModel.elements(); e.hasMoreElements(); ) {
            WPObject object = e.nextElement();
            object.setAttribute(ATTRIBUTE_LEAF_DECAY_MODE, LEAF_DECAY_OFF);
        }
        refreshLeafDecaySettings();
    }

    private void resetLeafDecay() {
        for (Enumeration<WPObject> e = listModel.elements(); e.hasMoreElements(); ) {
            WPObject object = e.nextElement();
            object.getAttributes().remove(ATTRIBUTE_LEAF_DECAY_MODE.key);
        }
        refreshLeafDecaySettings();
    }

    private void updateBlocksPerAttempt() {
        final int grid = (Integer) spinnerGrid.getValue();
        final float blocksAt50 = (float) ((Integer) spinnerBlocksPerAttempt.getValue()) * grid * grid;
        final float blocksAt1 = blocksAt50 * 64, blocksAt100 = round(blocksAt50 / 3.515625f);
        labelBlocksPerAttempt.setText(format("one per %d blocks at 1%%; %d blocks at 50%%; %d blocks at 100%%)",
                round(blocksAt1),
                round(blocksAt50),
                round((blocksAt100 <= 1) ? 1 : blocksAt100)));
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonReloadAll = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();
        buttonEdit = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        labelLeafDecayTitle = new javax.swing.JLabel();
        labelEffectiveLeafDecaySetting = new javax.swing.JLabel();
        buttonSetDecay = new javax.swing.JButton();
        buttonSetNoDecay = new javax.swing.JButton();
        buttonReset = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        listObjects = new javax.swing.JList<>();
        jLabel6 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        fieldName = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        paintPicker1 = new org.pepsoft.worldpainter.layers.renderers.PaintPicker();
        jLabel2 = new javax.swing.JLabel();
        buttonAddFile = new javax.swing.JButton();
        buttonRemoveFile = new javax.swing.JButton();
        jLabel7 = new javax.swing.JLabel();
        spinnerBlocksPerAttempt = new javax.swing.JSpinner();
        labelBlocksPerAttempt = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        spinnerGrid = new javax.swing.JSpinner();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        spinnerRandomOffset = new javax.swing.JSpinner();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();

        buttonReloadAll.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/pepsoft/worldpainter/icons/arrow_rotate_clockwise.png"))); // NOI18N
        buttonReloadAll.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("wp.reload.all.or.selected.9388c09386"));
        buttonReloadAll.setEnabled(false);
        buttonReloadAll.setMargin(new java.awt.Insets(2, 2, 2, 2));
        buttonReloadAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonReloadAllActionPerformed(evt);
            }
        });

        jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);

        buttonEdit.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/pepsoft/worldpainter/icons/brick_edit.png"))); // NOI18N
        buttonEdit.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("wp.edit.selected.object.s.af7f4281ad"));
        buttonEdit.setEnabled(false);
        buttonEdit.setMargin(new java.awt.Insets(2, 2, 2, 2));
        buttonEdit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonEditActionPerformed(evt);
            }
        });

        labelLeafDecayTitle.setText(org.pepsoft.worldpainter.WPI18n.s("wp.leaf.decay.settings.for.fc54e1287d"));

        labelEffectiveLeafDecaySetting.setText(org.pepsoft.worldpainter.WPI18n.s("wp.html.leaves.do.b.4caa96b58b"));
        labelEffectiveLeafDecaySetting.setEnabled(false);

        buttonSetDecay.setText(org.pepsoft.worldpainter.WPI18n.s("wp.set.all.to.decay.ba4936e46f"));
        buttonSetDecay.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("wp.set.all.objects.to.88d2503c32"));
        buttonSetDecay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonSetDecayActionPerformed(evt);
            }
        });

        buttonSetNoDecay.setText(org.pepsoft.worldpainter.WPI18n.s("wp.html.set.all.to.f72c00ec35"));
        buttonSetNoDecay.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("wp.set.all.objects.to.bcbb1f0b50"));
        buttonSetNoDecay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonSetNoDecayActionPerformed(evt);
            }
        });

        buttonReset.setText(org.pepsoft.worldpainter.WPI18n.s("wp.reset.526d688f37"));
        buttonReset.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("wp.reset.leaf.decay.to.972a3f8cc0"));
        buttonReset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonResetActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(labelEffectiveLeafDecaySetting, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(labelLeafDecayTitle)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(buttonSetDecay)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonSetNoDecay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonReset)))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(labelLeafDecayTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(labelEffectiveLeafDecaySetting, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buttonSetDecay)
                    .addComponent(buttonSetNoDecay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(buttonReset)))
        );

        listObjects.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                listObjectsMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(listObjects);

        jLabel6.setForeground(new java.awt.Color(0, 0, 255));
        jLabel6.setText(org.pepsoft.worldpainter.WPI18n.s("wp.html.u.get.custom.40b1d94e39"));
        jLabel6.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jLabel6.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel6MouseClicked(evt);
            }
        });

        jLabel1.setText(org.pepsoft.worldpainter.WPI18n.s("wp.define.your.custom.object.d2e1a95c5a"));

        jLabel3.setText(org.pepsoft.worldpainter.WPI18n.s("wp.name.4e140ba723"));

        fieldName.setColumns(15);

        jLabel4.setText(org.pepsoft.worldpainter.WPI18n.s("wp.paint.d65951a421"));

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(paintPicker1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fieldName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(fieldName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(paintPicker1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        jLabel2.setText(org.pepsoft.worldpainter.WPI18n.s("wp.object.s.598442195f"));

        buttonAddFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/pepsoft/worldpainter/icons/brick_add.png"))); // NOI18N
        buttonAddFile.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("wp.add.one.or.more.6ff25bbdd2"));
        buttonAddFile.setMargin(new java.awt.Insets(2, 2, 2, 2));
        buttonAddFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonAddFileActionPerformed(evt);
            }
        });

        buttonRemoveFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/pepsoft/worldpainter/icons/brick_delete.png"))); // NOI18N
        buttonRemoveFile.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("wp.remove.selected.object.s.d3b1f676f7"));
        buttonRemoveFile.setEnabled(false);
        buttonRemoveFile.setMargin(new java.awt.Insets(2, 2, 2, 2));
        buttonRemoveFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonRemoveFileActionPerformed(evt);
            }
        });

        jLabel7.setText(org.pepsoft.worldpainter.WPI18n.s("wp.spawn.chance.f3e725aa66"));

        spinnerBlocksPerAttempt.setModel(new javax.swing.SpinnerNumberModel(20, 1, 99999, 1));
        spinnerBlocksPerAttempt.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerBlocksPerAttemptStateChanged(evt);
            }
        });

        labelBlocksPerAttempt.setText(org.pepsoft.worldpainter.WPI18n.s("wp.one.per.x.blocks.5dfb644251"));

        jLabel10.setText(org.pepsoft.worldpainter.WPI18n.s("wp.one.in.1ce8ebb713"));

        jLabel5.setText(org.pepsoft.worldpainter.WPI18n.s("wp.grid.c4aba355b2"));

        spinnerGrid.setModel(new javax.swing.SpinnerNumberModel(1, 1, 999, 1));
        spinnerGrid.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerGridStateChanged(evt);
            }
        });

        jLabel8.setText(org.pepsoft.worldpainter.WPI18n.s("wp.at.2906d77f75"));

        jLabel9.setText(org.pepsoft.worldpainter.WPI18n.s("wp.random.offset.778f1eb64a"));

        spinnerRandomOffset.setModel(new javax.swing.SpinnerNumberModel(0, 0, 999, 1));
        spinnerRandomOffset.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerRandomOffsetStateChanged(evt);
            }
        });

        jLabel11.setText(org.pepsoft.worldpainter.WPI18n.s("wp.block.s.1216aaefbc"));

        jLabel12.setText(org.pepsoft.worldpainter.WPI18n.s("wp.effective.density.d4676653dc"));

        jLabel13.setText(org.pepsoft.worldpainter.WPI18n.s("wp.objects.displaced.in.a.958de2abc0"));

        jLabel14.setText(org.pepsoft.worldpainter.WPI18n.s("wp.block.s.1216aaefbc"));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(buttonAddFile, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(buttonRemoveFile, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(buttonEdit, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(buttonReloadAll, javax.swing.GroupLayout.Alignment.TRAILING)))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2)
                    .addComponent(jLabel1)
                    .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel7)
                            .addComponent(jLabel5)
                            .addComponent(jLabel9)
                            .addComponent(jLabel12))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(spinnerRandomOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel11)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel13))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(spinnerGrid, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel14))
                            .addComponent(labelBlocksPerAttempt)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel10)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(spinnerBlocksPerAttempt, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel8)))))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(buttonAddFile)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonRemoveFile)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonEdit)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonReloadAll)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(spinnerGrid, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel14))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(spinnerBlocksPerAttempt, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel10)
                    .addComponent(jLabel8))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(labelBlocksPerAttempt)
                    .addComponent(jLabel12))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(spinnerRandomOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11)
                    .addComponent(jLabel13))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jSeparator2)
                    .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void buttonReloadAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonReloadAllActionPerformed
        reloadObjects();
    }//GEN-LAST:event_buttonReloadAllActionPerformed

    private void buttonEditActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonEditActionPerformed
        editObjects();
    }//GEN-LAST:event_buttonEditActionPerformed

    private void buttonSetDecayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonSetDecayActionPerformed
        setLeavesDecay();
    }//GEN-LAST:event_buttonSetDecayActionPerformed

    private void buttonSetNoDecayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonSetNoDecayActionPerformed
        setLeavesNoDecay();
    }//GEN-LAST:event_buttonSetNoDecayActionPerformed

    private void buttonResetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonResetActionPerformed
        resetLeafDecay();
    }//GEN-LAST:event_buttonResetActionPerformed

    private void listObjectsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listObjectsMouseClicked
        if (evt.getClickCount() == 2) {
            int row = listObjects.getSelectedIndex();
            if (row != -1) {
                WPObject object = listModel.getElementAt(row);
                EditObjectAttributes dialog = new EditObjectAttributes(SwingUtilities.getWindowAncestor(this), object, colourScheme);
                dialog.setVisible(true);
                if (! dialog.isCancelled()) {
                    refreshLeafDecaySettings();
                }
            }
        }
    }//GEN-LAST:event_listObjectsMouseClicked

    private void jLabel6MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel6MouseClicked
        try {
            DesktopUtils.open(new URL("https://www.worldpainter.net/doc/legacy/customobjects"));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed URL exception while trying to open https://www.worldpainter.net/doc/legacy/customobjects", e);
        }
    }//GEN-LAST:event_jLabel6MouseClicked

    private void buttonAddFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonAddFileActionPerformed
        addFilesOrDirectory();
    }//GEN-LAST:event_buttonAddFileActionPerformed

    private void buttonRemoveFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonRemoveFileActionPerformed
        removeFiles();
    }//GEN-LAST:event_buttonRemoveFileActionPerformed

    private void spinnerBlocksPerAttemptStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerBlocksPerAttemptStateChanged
        updateBlocksPerAttempt();
        settingsChanged();
    }//GEN-LAST:event_spinnerBlocksPerAttemptStateChanged

    private void spinnerGridStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerGridStateChanged
        updateBlocksPerAttempt();
        settingsChanged();
    }//GEN-LAST:event_spinnerGridStateChanged

    private void spinnerRandomOffsetStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerRandomOffsetStateChanged
        settingsChanged();
    }//GEN-LAST:event_spinnerRandomOffsetStateChanged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton buttonAddFile;
    private javax.swing.JButton buttonEdit;
    private javax.swing.JButton buttonReloadAll;
    private javax.swing.JButton buttonRemoveFile;
    private javax.swing.JButton buttonReset;
    private javax.swing.JButton buttonSetDecay;
    private javax.swing.JButton buttonSetNoDecay;
    private javax.swing.JTextField fieldName;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JLabel labelBlocksPerAttempt;
    private javax.swing.JLabel labelEffectiveLeafDecaySetting;
    private javax.swing.JLabel labelLeafDecayTitle;
    private javax.swing.JList<WPObject> listObjects;
    private org.pepsoft.worldpainter.layers.renderers.PaintPicker paintPicker1;
    private javax.swing.JSpinner spinnerBlocksPerAttempt;
    private javax.swing.JSpinner spinnerGrid;
    private javax.swing.JSpinner spinnerRandomOffset;
    // End of variables declaration//GEN-END:variables

    private final DefaultListModel<WPObject> listModel;
    private final NumberFormat numberFormat = NumberFormat.getInstance();
    private ColourScheme colourScheme;

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Bo2LayerEditor.class);
    private static final long serialVersionUID = 1L;
}
