/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.layers.bo2;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.swing.*;
import javax.swing.JSpinner.NumberEditor;
import javax.vecmath.Point3i;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.Serializable;
import java.util.*;

import static org.pepsoft.minecraft.Material.AIR;
import static org.pepsoft.worldpainter.objects.WPObject.*;

/**
 *
 * @author pepijn
 */
public class EditObjectAttributes extends WorldPainterDialog {
    public EditObjectAttributes(Window parent, WPObject object, ColourScheme colourScheme) {
        this(parent, Collections.singleton(object), colourScheme);
    }
    
    public EditObjectAttributes(Window parent, Collection<WPObject> objects, ColourScheme colourScheme) {
        super(parent);
        this.objects = objects;
        this.colourScheme = colourScheme;
        
        if (objects.isEmpty()) {
            throw new IllegalArgumentException("Collection of objects may not be empty");
        }
        
        initComponents();
        
        // Set the spinner to not use thousands separators to make it slightly
        // smaller
        spinnerFrequency.setEditor(new NumberEditor(spinnerFrequency, "0"));
        
        if (objects.size() == 1) {
            WPObject object = objects.iterator().next();
            fieldName.setText(object.getName());
            file = object.getAttribute(ATTRIBUTE_FILE);
            if (file != null) {
                labelFile.setText(file.getAbsolutePath());
                if (! file.exists()) {
                    labelFile.setForeground(Color.RED);
                }
            } else {
                labelFile.setText(org.pepsoft.worldpainter.WPI18n.s("ui.html.htmlIUnknownI"));
            }
            Point3i offset = object.getOffset();
            offsets.put(object, offset);
            String offsetStr = "<html><u>" + offset.x + ", " + offset.y + ", " + offset.z + "</u></html>";
            labelOffset.setText(offsetStr);
            checkBoxRandomRotation.setSelected(object.getAttribute(ATTRIBUTE_RANDOM_ROTATION) || object.getAttribute(ATTRIBUTE_RANDOM_ROTATION_ONLY));
            checkBoxRandomRotation.setTristateMode(false);
            checkBoxRandomMirroring.setSelected(object.getAttribute(ATTRIBUTE_RANDOM_ROTATION) || object.getAttribute(ATTRIBUTE_RANDOM_MIRRORING_ONLY));
            checkBoxRandomMirroring.setTristateMode(false);
            checkBoxOnAir.setSelected(! object.getAttribute(ATTRIBUTE_NEEDS_FOUNDATION));
            checkBoxOnAir.setTristateMode(false);
            checkBoxUnderLava.setSelected(object.getAttribute(ATTRIBUTE_SPAWN_IN_LAVA));
            checkBoxUnderLava.setTristateMode(false);
            checkBoxUnderWater.setSelected(object.getAttribute(ATTRIBUTE_SPAWN_IN_WATER));
            checkBoxUnderWater.setTristateMode(false);
            checkBoxOnSolidLand.setSelected(object.getAttribute(ATTRIBUTE_SPAWN_ON_LAND));
            checkBoxOnSolidLand.setTristateMode(false);
            checkBoxOnWater.setSelected(object.getAttribute(ATTRIBUTE_SPAWN_ON_WATER));
            checkBoxOnWater.setTristateMode(false);
            checkBoxOnLava.setSelected(object.getAttribute(ATTRIBUTE_SPAWN_ON_LAVA));
            checkBoxOnLava.setTristateMode(false);
            checkBoxCollideWithFloor.setSelected(! object.getAttribute(ATTRIBUTE_SPAWN_ON_WATER_NO_COLLIDE));
            checkBoxCollideWithFloor.setTristateMode(false);
            // Remove "no change" choices
            ((DefaultComboBoxModel) comboBoxCollisionMode.getModel()).removeElementAt(0);
            ((DefaultComboBoxModel) comboBoxUndergroundMode.getModel()).removeElementAt(0);
            ((DefaultComboBoxModel) comboBoxLeafDecayMode.getModel()).removeElementAt(0);
            ((DefaultComboBoxModel) comboBoxWaterlogging.getModel()).removeElementAt(0);
            comboBoxCollisionMode.setSelectedIndex(object.getAttribute(ATTRIBUTE_COLLISION_MODE) - 1);
            comboBoxUndergroundMode.setSelectedIndex(object.getAttribute(ATTRIBUTE_UNDERGROUND_MODE) - 1);
            comboBoxLeafDecayMode.setSelectedIndex(object.getAttribute(ATTRIBUTE_LEAF_DECAY_MODE) - 1);
            comboBoxWaterlogging.setSelectedIndex(object.getAttribute(ATTRIBUTE_MANAGE_WATERLOGGED) ? 0 : 1);
            spinnerFrequency.setValue(object.getAttribute(ATTRIBUTE_FREQUENCY));
            if (object.getAttribute(ATTRIBUTE_HEIGHT_MODE) == HEIGHT_MODE_TERRAIN) {
                radioButtonPlaceOnTerrain.setSelected(true);
            } else {
                radioButtonPlaceAtFixedHeight.setSelected(true);
            }
            spinnerVerticalOffset.setValue(object.getAttribute(ATTRIBUTE_VERTICAL_OFFSET));
            spinnerRandomVariation.setValue(object.getAttribute(ATTRIBUTE_Y_VARIATION));
            SortedSet<Material> materials = new TreeSet<>(Comparator.comparing(Material::toString));
            object.visitBlocks((WPObject o, int x, int y, int z, Material m) -> {
                if (m != AIR) {
                    materials.add(m);
                }
                return true;
            });
            if (! materials.isEmpty()) {
                comboBoxReplacedMaterial.setModel(new DefaultComboBoxModel<>(materials.toArray(new Material[materials.size()])));
                if (object.hasAttribute(ATTRIBUTE_REPLACE_WITH_AIR)) {
                    int[] replaceWithBlock = object.getAttribute(ATTRIBUTE_REPLACE_WITH_AIR);
                    checkBoxReplace.setSelected(true);
                    comboBoxReplacedMaterial.setSelectedItem(Material.get(replaceWithBlock[0], replaceWithBlock[1]));
                } else if (object.hasAttribute(ATTRIBUTE_REPLACE_WITH_AIR_MATERIAL)) {
                    Material replaceWithMaterial = object.getAttribute(ATTRIBUTE_REPLACE_WITH_AIR_MATERIAL);
                    checkBoxReplace.setSelected(true);
                    comboBoxReplacedMaterial.setSelectedItem(replaceWithMaterial);
                }
            } else {
                checkBoxReplace.setEnabled(false);
            }
            checkBoxExtendFoundation.setSelected(object.getAttribute(ATTRIBUTE_EXTEND_FOUNDATION));
            checkBoxExtendFoundation.setTristateMode(false);
            WPObjectPreviewer previewer = new WPObjectPreviewer();
            previewer.setDimension(App.getInstance().getDimension());
            previewer.setObject(object);
            jPanel1.add(previewer, BorderLayout.CENTER);
        } else {
            labelFile.setText(objects.size() + org.pepsoft.worldpainter.WPI18n.s("ui.frag.objectsSelected"));
            fieldName.setText(org.pepsoft.worldpainter.WPI18n.s("ui.text.multiple"));
            fieldName.setEnabled(false);
            file = null;
            long frequencyTotal = 0, variationTotal = 0, verticalOffsetTotal = 0;
            int firstFrequency = -1, firstVariation = -1;
            boolean allFrequenciesIdentical = true, allVariationsIdentical = true;
            Point3i origin = new Point3i();
            for (WPObject object: objects) {
                if (! object.getOffset().equals(origin)) {
                    offsets.put(object, object.getOffset());
                }
                int frequency = object.getAttribute(ATTRIBUTE_FREQUENCY);
                frequencyTotal += frequency;
                if (firstFrequency == -1) {
                    firstFrequency = frequency;
                } else if (frequency != firstFrequency) {
                    allFrequenciesIdentical = false;
                }
                int variation = object.getAttribute(ATTRIBUTE_Y_VARIATION);
                variationTotal += variation;
                if (firstVariation == -1) {
                    firstVariation = variation;
                } else if (variation != firstVariation) {
                    allVariationsIdentical = false;
                }
                int verticalOffset = object.getAttribute(ATTRIBUTE_VERTICAL_OFFSET);
                verticalOffsetTotal += verticalOffset;
            }
            labelOffset.setText(org.pepsoft.worldpainter.WPI18n.s("ui.text.multiple"));
            checkBoxRandomRotation.setMixed(true);
            checkBoxRandomMirroring.setMixed(true);
            checkBoxOnAir.setMixed(true);
            checkBoxUnderLava.setMixed(true);
            checkBoxUnderWater.setMixed(true);
            checkBoxOnSolidLand.setMixed(true);
            checkBoxOnWater.setMixed(true);
            checkBoxOnLava.setMixed(true);
            checkBoxCollideWithFloor.setMixed(true);
            labelOffset.setCursor(null);
            labelOffset.setForeground(null);
            int averageFrequency = (int) (frequencyTotal / objects.size());
            spinnerFrequency.setValue(averageFrequency);
            if (! allFrequenciesIdentical) {
                checkBoxFrequencyActive.setSelected(false);
                checkBoxFrequencyActive.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.html.htmlTheRelativeFrequencies"));
                checkBoxFrequencyActive.setEnabled(true);
                spinnerFrequency.setEnabled(false);
            }
            checkBoxReplace.setEnabled(false);
            checkBoxExtendFoundation.setMixed(true);
            spinnerVerticalOffset.setValue((int) (verticalOffsetTotal / objects.size()));
            int averageVariation = (int) (variationTotal / objects.size());
            spinnerRandomVariation.setValue(averageVariation);
            if (! allVariationsIdentical) {
                checkBoxRandomVariationActive.setSelected(false);
                checkBoxRandomVariationActive.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.html.htmlTheRandomVariations"));
                checkBoxRandomVariationActive.setEnabled(true);
                spinnerRandomVariation.setEnabled(false);
            }
        }
        scaleToUI();
        pack();

        ActionMap actionMap = rootPane.getActionMap();
        actionMap.put("cancel", new AbstractAction("cancel") {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
            
            private static final long serialVersionUID = 1L;
        });

        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        
        getRootPane().setDefaultButton(buttonOK);
        
        setLocationRelativeTo(parent);
        
        setControlStates();
    }

    private void editOffset() {
        if (objects.size() > 1) {
            return;
        }
        WPObject object = objects.iterator().next();
        Point3i offset = offsets.get(object);
        OffsetEditor dialog = new OffsetEditor(this, (offset != null) ? offset : new Point3i(), object, colourScheme);
        dialog.setVisible(true);
        if (! dialog.isCancelled()) {
            offset = dialog.getOffset();
            offsets.put(object, offset);
            String offsetStr = "<html><u>" + offset.x + ", " + offset.y + ", " + offset.z + "</u></html>";
            labelOffset.setText(offsetStr);
        }
    }

    protected void ok() {
        boolean singleSelection = objects.size() == 1;
        for (WPObject object: objects) {
            if (singleSelection && (! fieldName.getText().trim().isEmpty())) {
                object.setName(fieldName.getText().trim());
            }
            Map<String, Serializable> attributes = object.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            if (checkBoxFrequencyActive.isSelected()) {
                final int frequency = (Integer) spinnerFrequency.getValue();
                if (frequency != 100) {
                    attributes.put(ATTRIBUTE_FREQUENCY.key, frequency);
                } else {
                    attributes.remove(ATTRIBUTE_FREQUENCY.key);
                }
            }
            Point3i offset = offsets.get(object);
            if ((offset != null) && ((offset.x != 0) || (offset.y != 0) || (offset.z != 0))) {
                attributes.put(ATTRIBUTE_OFFSET.key, offset);
            } else {
                attributes.remove(ATTRIBUTE_OFFSET.key);
            }
            if ((! checkBoxRandomRotation.isMixed()) || (! checkBoxRandomMirroring.isMixed())) {
                // To make things simpler, always migrate the single attribute to the separate attributes first
                if (ATTRIBUTE_RANDOM_ROTATION.get(attributes)) {
                    attributes.put(ATTRIBUTE_RANDOM_ROTATION.key, false);
                    attributes.put(ATTRIBUTE_RANDOM_ROTATION_ONLY.key, true);
                    attributes.put(ATTRIBUTE_RANDOM_MIRRORING_ONLY.key, true);
                }
                if (! checkBoxRandomRotation.isMixed()) {
                    attributes.put(ATTRIBUTE_RANDOM_ROTATION_ONLY.key, checkBoxRandomRotation.isSelected());
                }
                if (! checkBoxRandomMirroring.isMixed()) {
                    attributes.put(ATTRIBUTE_RANDOM_MIRRORING_ONLY.key, checkBoxRandomMirroring.isSelected());
                }
            }
            if (! checkBoxOnAir.isMixed()) {
                attributes.put(ATTRIBUTE_NEEDS_FOUNDATION.key, ! checkBoxOnAir.isSelected());
            }
            if (! checkBoxUnderLava.isMixed()) {
                attributes.put(ATTRIBUTE_SPAWN_IN_LAVA.key, checkBoxUnderLava.isSelected());
            }
            if (! checkBoxUnderWater.isMixed()) {
                attributes.put(ATTRIBUTE_SPAWN_IN_WATER.key, checkBoxUnderWater.isSelected());
            }
            if (! checkBoxOnSolidLand.isMixed()) {
                attributes.put(ATTRIBUTE_SPAWN_ON_LAND.key, checkBoxOnSolidLand.isSelected());
            }
            if (! checkBoxOnWater.isMixed()) {
                attributes.put(ATTRIBUTE_SPAWN_ON_WATER.key, checkBoxOnWater.isSelected());
            }
            if (! checkBoxCollideWithFloor.isMixed()) {
                attributes.put(ATTRIBUTE_SPAWN_ON_WATER_NO_COLLIDE.key, ! checkBoxCollideWithFloor.isSelected());
            }
            if (! checkBoxOnLava.isMixed()) {
                attributes.put(ATTRIBUTE_SPAWN_ON_LAVA.key, checkBoxOnLava.isSelected());
            }
            if (singleSelection || comboBoxCollisionMode.getSelectedIndex() > 0) {
                attributes.put(ATTRIBUTE_COLLISION_MODE.key, comboBoxCollisionMode.getSelectedIndex() + (singleSelection ? 1 : 0));
            }
            if (singleSelection || comboBoxUndergroundMode.getSelectedIndex() > 0) {
                attributes.put(ATTRIBUTE_UNDERGROUND_MODE.key, comboBoxUndergroundMode.getSelectedIndex() + (singleSelection ? 1 : 0));
            }
            if (singleSelection || comboBoxLeafDecayMode.getSelectedIndex() > 0) {
                attributes.put(ATTRIBUTE_LEAF_DECAY_MODE.key, comboBoxLeafDecayMode.getSelectedIndex() + (singleSelection ? 1 : 0));
            }
            if (singleSelection || comboBoxWaterlogging.getSelectedIndex() > 0) {
                attributes.put(ATTRIBUTE_MANAGE_WATERLOGGED.key, ((comboBoxWaterlogging.getSelectedIndex() - (singleSelection ? 0 : 1)) == 0));
            }
            if (singleSelection) {
                attributes.remove(ATTRIBUTE_REPLACE_WITH_AIR.key);
                if (checkBoxReplace.isSelected()) {
                    attributes.put(ATTRIBUTE_REPLACE_WITH_AIR_MATERIAL.key, (Material) comboBoxReplacedMaterial.getSelectedItem());
                } else {
                    attributes.remove(ATTRIBUTE_REPLACE_WITH_AIR_MATERIAL.key);
                }
            }
            if (! checkBoxExtendFoundation.isMixed()) {
                attributes.put(ATTRIBUTE_EXTEND_FOUNDATION.key, checkBoxExtendFoundation.isSelected());
            }
            if (radioButtonPlaceOnTerrain.isSelected() || radioButtonPlaceAtFixedHeight.isSelected()) {
                final int verticalOffset = (int) spinnerVerticalOffset.getValue();
                if (verticalOffset != 0) {
                    attributes.put(ATTRIBUTE_VERTICAL_OFFSET.key, verticalOffset);
                } else {
                    attributes.remove(ATTRIBUTE_VERTICAL_OFFSET.key);
                }
            }
            if (radioButtonPlaceOnTerrain.isSelected()) {
                attributes.remove(ATTRIBUTE_HEIGHT_MODE.key);
            } else if (radioButtonPlaceAtFixedHeight.isSelected()) {
                attributes.put(ATTRIBUTE_HEIGHT_MODE.key, HEIGHT_MODE_FIXED);
            }
            if (checkBoxRandomVariationActive.isSelected()) {
                final int variation = (Integer) spinnerRandomVariation.getValue();
                if (variation > 0) {
                    attributes.put(ATTRIBUTE_Y_VARIATION.key, variation);
                } else {
                    attributes.remove(ATTRIBUTE_Y_VARIATION.key);
                }
            }
            if (! attributes.isEmpty()) {
                object.setAttributes(attributes);
            } else {
                object.setAttributes(null);
            }
        }
        super.ok();
    }

    private void autoOffset() {
        boolean singleSelection = objects.size() == 1;
        for (WPObject object: objects) {
            Point3i offset = object.guestimateOffset();
            if (offset == null) {
                // This object has size zero or consists of nothing but air!
                offsets.clear();
                if (singleSelection) {
                    labelOffset.setText(org.pepsoft.worldpainter.WPI18n.s("ui.html.htmlUUHtml"));
                }
            } else {
                offsets.put(object, offset);
                if (singleSelection) {
                    String offsetStr = "<html><u>" + offset.x + ", " + offset.y + ", " + offset.z + "</u></html>";
                    labelOffset.setText(offsetStr);
                }
            }
        }
        if (! singleSelection) {
            JOptionPane.showMessageDialog(this, objects.size() + org.pepsoft.worldpainter.WPI18n.s("ui.frag.offsetsAutoset"), org.pepsoft.worldpainter.WPI18n.s("information"), JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void resetOffset() {
        offsets.clear();
        boolean singleSelection = objects.size() == 1;
        if (singleSelection) {
            labelOffset.setText(org.pepsoft.worldpainter.WPI18n.s("ui.html.htmlUUHtml"));
        } else {
            JOptionPane.showMessageDialog(this, objects.size() + org.pepsoft.worldpainter.WPI18n.s("ui.frag.offsetsReset"), org.pepsoft.worldpainter.WPI18n.s("information"), JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void setControlStates() {
        comboBoxReplacedMaterial.setEnabled(checkBoxReplace.isSelected());
        checkBoxCollideWithFloor.setEnabled(checkBoxOnWater.isSelected() || checkBoxOnWater.isTristateMode());
        spinnerVerticalOffset.setEnabled(radioButtonPlaceAtFixedHeight.isSelected());
        if (radioButtonPlaceOnTerrain.isSelected()) {
            labelVerticalOffset.setText(org.pepsoft.worldpainter.WPI18n.s("ui.height.heightAboveTerrain"));
        } else if (radioButtonPlaceAtFixedHeight.isSelected()) {
            labelVerticalOffset.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.absoluteHeight"));
        }
        spinnerVerticalOffset.setEnabled(radioButtonPlaceOnTerrain.isSelected() || radioButtonPlaceAtFixedHeight.isSelected());
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        labelFile = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        labelOffset = new javax.swing.JLabel();
        buttonOffsetAuto = new javax.swing.JButton();
        buttonOffsetReset = new javax.swing.JButton();
        buttonCancel = new javax.swing.JButton();
        buttonOK = new javax.swing.JButton();
        fieldName = new javax.swing.JTextField();
        jPanel1 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        spinnerFrequency = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        comboBoxCollisionMode = new javax.swing.JComboBox();
        jLabel7 = new javax.swing.JLabel();
        comboBoxUndergroundMode = new javax.swing.JComboBox();
        jLabel8 = new javax.swing.JLabel();
        checkBoxRandomRotation = new org.pepsoft.worldpainter.util.TristateCheckBox();
        checkBoxOnSolidLand = new org.pepsoft.worldpainter.util.TristateCheckBox();
        checkBoxOnAir = new org.pepsoft.worldpainter.util.TristateCheckBox();
        checkBoxOnWater = new org.pepsoft.worldpainter.util.TristateCheckBox();
        checkBoxUnderWater = new org.pepsoft.worldpainter.util.TristateCheckBox();
        checkBoxUnderLava = new org.pepsoft.worldpainter.util.TristateCheckBox();
        checkBoxOnLava = new org.pepsoft.worldpainter.util.TristateCheckBox();
        checkBoxFrequencyActive = new javax.swing.JCheckBox();
        jLabel9 = new javax.swing.JLabel();
        comboBoxLeafDecayMode = new javax.swing.JComboBox();
        checkBoxReplace = new javax.swing.JCheckBox();
        checkBoxExtendFoundation = new org.pepsoft.worldpainter.util.TristateCheckBox();
        comboBoxReplacedMaterial = new javax.swing.JComboBox<>();
        checkBoxCollideWithFloor = new org.pepsoft.worldpainter.util.TristateCheckBox();
        checkBoxRandomMirroring = new org.pepsoft.worldpainter.util.TristateCheckBox();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        radioButtonPlaceOnTerrain = new javax.swing.JRadioButton();
        radioButtonPlaceAtFixedHeight = new javax.swing.JRadioButton();
        spinnerVerticalOffset = new javax.swing.JSpinner();
        jLabel12 = new javax.swing.JLabel();
        spinnerRandomVariation = new javax.swing.JSpinner();
        jLabel13 = new javax.swing.JLabel();
        checkBoxRandomVariationActive = new javax.swing.JCheckBox();
        labelVerticalOffset = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();
        comboBoxWaterlogging = new javax.swing.JComboBox<>();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(org.pepsoft.worldpainter.WPI18n.s("ui.dialog.editObjectAttributes.title"));

        jLabel1.setText(org.pepsoft.worldpainter.WPI18n.s("ui.label.name"));

        jLabel2.setText(org.pepsoft.worldpainter.WPI18n.s("ui.label.file"));

        labelFile.setText("jLabel3");

        jLabel3.setText(org.pepsoft.worldpainter.WPI18n.s("ui.label.offset"));

        labelOffset.setForeground(new java.awt.Color(0, 0, 255));
        labelOffset.setText(org.pepsoft.worldpainter.WPI18n.s("ui.html.htmlUOffsetU"));
        labelOffset.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.field.clickToEditThe"));
        labelOffset.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        labelOffset.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                labelOffsetMouseClicked(evt);
            }
        });

        buttonOffsetAuto.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.auto"));
        buttonOffsetAuto.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.text.thisWillTryTo"));
        buttonOffsetAuto.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        buttonOffsetAuto.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonOffsetAutoActionPerformed(evt);
            }
        });

        buttonOffsetReset.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.zero"));
        buttonOffsetReset.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.text.thisWillSetThe"));
        buttonOffsetReset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonOffsetResetActionPerformed(evt);
            }
        });

        buttonCancel.setText(org.pepsoft.worldpainter.WPI18n.s("ui.button.cancel"));
        buttonCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonCancelActionPerformed(evt);
            }
        });

        buttonOK.setText(org.pepsoft.worldpainter.WPI18n.s("ui.button.ok"));
        buttonOK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonOKActionPerformed(evt);
            }
        });

        fieldName.setColumns(20);
        fieldName.setText("jTextField1");

        jPanel1.setLayout(new java.awt.BorderLayout());

        jLabel4.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.relativeFrequency"));
        jLabel4.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.text.theFrequencyOfThis"));

        spinnerFrequency.setModel(new javax.swing.SpinnerNumberModel(100, 1, 9999, 1));
        spinnerFrequency.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.text.theFrequencyOfThis"));

        jLabel5.setText("%");

        jLabel6.setLabelFor(comboBoxCollisionMode);
        jLabel6.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.collideWith"));
        jLabel6.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.html.htmlDeterminesWhichExisting"));

        comboBoxCollisionMode.setModel(new javax.swing.DefaultComboBoxModel(new String[] { org.pepsoft.worldpainter.WPI18n.s("ui.option.noChange"), org.pepsoft.worldpainter.WPI18n.s("ui.option.anyBlocks"), org.pepsoft.worldpainter.WPI18n.s("ui.option.solidBlocks"), org.pepsoft.worldpainter.WPI18n.s("ui.option.nothing") }));
        comboBoxCollisionMode.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.html.htmlDeterminesWhichExisting"));

        jLabel7.setLabelFor(comboBoxUndergroundMode);
        jLabel7.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.replaceUndergroundBlocks"));
        jLabel7.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.field.determinesWhetherExistingUnderground"));

        comboBoxUndergroundMode.setModel(new javax.swing.DefaultComboBoxModel(new String[] { org.pepsoft.worldpainter.WPI18n.s("ui.option.noChange"), org.pepsoft.worldpainter.WPI18n.s("ui.option.always"), org.pepsoft.worldpainter.WPI18n.s("ui.option.ifObjectBlockIsSolid"), org.pepsoft.worldpainter.WPI18n.s("ui.option.ifExistingBlockIsAir") }));
        comboBoxUndergroundMode.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.field.determinesWhetherExistingUnderground"));

        jLabel8.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.spawn"));

        checkBoxRandomRotation.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.randomRotation"));

        checkBoxOnSolidLand.setText(org.pepsoft.worldpainter.WPI18n.s("ui.label.onSolidLand"));

        checkBoxOnAir.setText(org.pepsoft.worldpainter.WPI18n.s("ui.label.onAir"));

        checkBoxOnWater.setText(org.pepsoft.worldpainter.WPI18n.s("ui.label.onWater"));
        checkBoxOnWater.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxOnWaterActionPerformed(evt);
            }
        });

        checkBoxUnderWater.setText(org.pepsoft.worldpainter.WPI18n.s("ui.text.underWater"));

        checkBoxUnderLava.setText(org.pepsoft.worldpainter.WPI18n.s("ui.text.underLava"));

        checkBoxOnLava.setText(org.pepsoft.worldpainter.WPI18n.s("ui.label.onLava"));

        checkBoxFrequencyActive.setSelected(true);
        checkBoxFrequencyActive.setText(" ");
        checkBoxFrequencyActive.setEnabled(false);
        checkBoxFrequencyActive.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxFrequencyActiveActionPerformed(evt);
            }
        });

        jLabel9.setLabelFor(comboBoxLeafDecayMode);
        jLabel9.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.leafBlocksShould"));

        comboBoxLeafDecayMode.setModel(new javax.swing.DefaultComboBoxModel(new String[] { org.pepsoft.worldpainter.WPI18n.s("ui.option.noChange"), org.pepsoft.worldpainter.WPI18n.s("ui.option.behaveAsExported"), org.pepsoft.worldpainter.WPI18n.s("ui.option.decay"), org.pepsoft.worldpainter.WPI18n.s("ui.option.notDecay") }));

        checkBoxReplace.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.replaceWithAir"));
        checkBoxReplace.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxReplaceActionPerformed(evt);
            }
        });

        checkBoxExtendFoundation.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.extendFoundationToGround"));

        comboBoxReplacedMaterial.setEnabled(false);

        checkBoxCollideWithFloor.setSelected(true);
        checkBoxCollideWithFloor.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.collideWithFloor"));
        checkBoxCollideWithFloor.setEnabled(false);

        checkBoxRandomMirroring.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.randomMirroring"));

        jLabel10.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/pepsoft/worldpainter/icons/information.png"))); // NOI18N
        jLabel10.setLabelFor(checkBoxRandomMirroring);
        jLabel10.setText(" ");
        jLabel10.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.html.htmlMirroringWorksBy"));

        jLabel11.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.placement"));

        buttonGroup1.add(radioButtonPlaceOnTerrain);
        radioButtonPlaceOnTerrain.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.relativeToTerrain"));
        radioButtonPlaceOnTerrain.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                radioButtonPlaceOnTerrainActionPerformed(evt);
            }
        });

        buttonGroup1.add(radioButtonPlaceAtFixedHeight);
        radioButtonPlaceAtFixedHeight.setText(org.pepsoft.worldpainter.WPI18n.s("ui.label.fixedHeight"));
        radioButtonPlaceAtFixedHeight.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                radioButtonPlaceAtFixedHeightActionPerformed(evt);
            }
        });

        spinnerVerticalOffset.setModel(new javax.swing.SpinnerNumberModel(0, -383, 383, 1));
        spinnerVerticalOffset.setEnabled(false);

        jLabel12.setLabelFor(spinnerRandomVariation);
        jLabel12.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.randomYVariation"));

        spinnerRandomVariation.setModel(new javax.swing.SpinnerNumberModel(0, 0, 383, 1));

        jLabel13.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/pepsoft/worldpainter/icons/information.png"))); // NOI18N
        jLabel13.setText(" ");
        jLabel13.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.field.distributedEquallyAboveAnd"));

        checkBoxRandomVariationActive.setSelected(true);
        checkBoxRandomVariationActive.setText(" ");
        checkBoxRandomVariationActive.setEnabled(false);
        checkBoxRandomVariationActive.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxRandomVariationActiveActionPerformed(evt);
            }
        });

        labelVerticalOffset.setLabelFor(spinnerVerticalOffset);
        labelVerticalOffset.setText(org.pepsoft.worldpainter.WPI18n.s("ui.height.heightAboveTerrain"));

        jLabel14.setText(org.pepsoft.worldpainter.WPI18n.s("ui.field.waterloggingShould"));

        comboBoxWaterlogging.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { org.pepsoft.worldpainter.WPI18n.s("ui.option.noChange"), org.pepsoft.worldpainter.WPI18n.s("ui.option.managedByWorldPainter"), org.pepsoft.worldpainter.WPI18n.s("ui.option.behaveAsExported") }));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(fieldName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel3)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(labelOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(buttonOffsetAuto)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(buttonOffsetReset))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel4)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(checkBoxFrequencyActive)
                                .addGap(0, 0, 0)
                                .addComponent(spinnerFrequency, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, 0)
                                .addComponent(jLabel5))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel6)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(comboBoxCollisionMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel7)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(comboBoxUndergroundMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(checkBoxRandomRotation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel8)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(checkBoxOnSolidLand, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(checkBoxUnderWater, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(checkBoxUnderLava, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(checkBoxOnAir, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(checkBoxOnWater, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(checkBoxOnLava, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(21, 21, 21)
                                        .addComponent(checkBoxCollideWithFloor, javax.swing.GroupLayout.PREFERRED_SIZE, 121, javax.swing.GroupLayout.PREFERRED_SIZE))))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel9)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(comboBoxLeafDecayMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(checkBoxReplace)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(comboBoxReplacedMaterial, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(checkBoxExtendFoundation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(checkBoxRandomMirroring, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel10))
                            .addComponent(jLabel11)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel12)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(checkBoxRandomVariationActive)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(spinnerRandomVariation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel13))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(labelVerticalOffset)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(spinnerVerticalOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel14)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(comboBoxWaterlogging, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(18, 18, 18)
                        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 256, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(buttonOK)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonCancel))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(labelFile))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(radioButtonPlaceOnTerrain)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(radioButtonPlaceAtFixedHeight)))
                        .addGap(0, 371, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(labelFile))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel1)
                            .addComponent(fieldName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel3)
                            .addComponent(labelOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(buttonOffsetAuto)
                            .addComponent(buttonOffsetReset))
                        .addGap(18, 18, 18)
                        .addComponent(jLabel11)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(radioButtonPlaceOnTerrain)
                            .addComponent(radioButtonPlaceAtFixedHeight))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(spinnerVerticalOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(labelVerticalOffset))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel12)
                            .addComponent(spinnerRandomVariation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel13)
                            .addComponent(checkBoxRandomVariationActive))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel4)
                            .addComponent(spinnerFrequency, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel5)
                            .addComponent(checkBoxFrequencyActive))
                        .addGap(18, 18, 18)
                        .addComponent(checkBoxRandomRotation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(checkBoxRandomMirroring, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel10))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel8)
                            .addComponent(checkBoxOnSolidLand, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(checkBoxOnAir, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(checkBoxUnderWater, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(checkBoxOnWater, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(checkBoxCollideWithFloor, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(checkBoxUnderLava, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(checkBoxOnLava, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel6)
                            .addComponent(comboBoxCollisionMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel7)
                            .addComponent(comboBoxUndergroundMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel9)
                            .addComponent(comboBoxLeafDecayMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel14)
                            .addComponent(comboBoxWaterlogging, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(checkBoxReplace)
                            .addComponent(comboBoxReplacedMaterial, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addComponent(checkBoxExtendFoundation, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buttonCancel)
                    .addComponent(buttonOK))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void labelOffsetMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_labelOffsetMouseClicked
        editOffset();
    }//GEN-LAST:event_labelOffsetMouseClicked

    private void buttonCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonCancelActionPerformed
        cancel();
    }//GEN-LAST:event_buttonCancelActionPerformed

    private void buttonOffsetAutoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonOffsetAutoActionPerformed
        autoOffset();
    }//GEN-LAST:event_buttonOffsetAutoActionPerformed

    private void buttonOffsetResetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonOffsetResetActionPerformed
        resetOffset();
    }//GEN-LAST:event_buttonOffsetResetActionPerformed

    private void buttonOKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonOKActionPerformed
        ok();
    }//GEN-LAST:event_buttonOKActionPerformed

    private void checkBoxFrequencyActiveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxFrequencyActiveActionPerformed
        spinnerFrequency.setEnabled(checkBoxFrequencyActive.isSelected());
    }//GEN-LAST:event_checkBoxFrequencyActiveActionPerformed

    private void checkBoxReplaceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxReplaceActionPerformed
        setControlStates();
    }//GEN-LAST:event_checkBoxReplaceActionPerformed

    private void checkBoxOnWaterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxOnWaterActionPerformed
        setControlStates();
    }//GEN-LAST:event_checkBoxOnWaterActionPerformed

    private void radioButtonPlaceOnTerrainActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_radioButtonPlaceOnTerrainActionPerformed
        setControlStates();
    }//GEN-LAST:event_radioButtonPlaceOnTerrainActionPerformed

    private void radioButtonPlaceAtFixedHeightActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_radioButtonPlaceAtFixedHeightActionPerformed
        setControlStates();
    }//GEN-LAST:event_radioButtonPlaceAtFixedHeightActionPerformed

    private void checkBoxRandomVariationActiveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxRandomVariationActiveActionPerformed
        spinnerRandomVariation.setEnabled(checkBoxRandomVariationActive.isSelected());
    }//GEN-LAST:event_checkBoxRandomVariationActiveActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton buttonCancel;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton buttonOK;
    private javax.swing.JButton buttonOffsetAuto;
    private javax.swing.JButton buttonOffsetReset;
    private org.pepsoft.worldpainter.util.TristateCheckBox checkBoxCollideWithFloor;
    private org.pepsoft.worldpainter.util.TristateCheckBox checkBoxExtendFoundation;
    private javax.swing.JCheckBox checkBoxFrequencyActive;
    private org.pepsoft.worldpainter.util.TristateCheckBox checkBoxOnAir;
    private org.pepsoft.worldpainter.util.TristateCheckBox checkBoxOnLava;
    private org.pepsoft.worldpainter.util.TristateCheckBox checkBoxOnSolidLand;
    private org.pepsoft.worldpainter.util.TristateCheckBox checkBoxOnWater;
    private org.pepsoft.worldpainter.util.TristateCheckBox checkBoxRandomMirroring;
    private org.pepsoft.worldpainter.util.TristateCheckBox checkBoxRandomRotation;
    private javax.swing.JCheckBox checkBoxRandomVariationActive;
    private javax.swing.JCheckBox checkBoxReplace;
    private org.pepsoft.worldpainter.util.TristateCheckBox checkBoxUnderLava;
    private org.pepsoft.worldpainter.util.TristateCheckBox checkBoxUnderWater;
    private javax.swing.JComboBox comboBoxCollisionMode;
    private javax.swing.JComboBox comboBoxLeafDecayMode;
    private javax.swing.JComboBox<Material> comboBoxReplacedMaterial;
    private javax.swing.JComboBox comboBoxUndergroundMode;
    private javax.swing.JComboBox<String> comboBoxWaterlogging;
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
    private javax.swing.JPanel jPanel1;
    private javax.swing.JLabel labelFile;
    private javax.swing.JLabel labelOffset;
    private javax.swing.JLabel labelVerticalOffset;
    private javax.swing.JRadioButton radioButtonPlaceAtFixedHeight;
    private javax.swing.JRadioButton radioButtonPlaceOnTerrain;
    private javax.swing.JSpinner spinnerFrequency;
    private javax.swing.JSpinner spinnerRandomVariation;
    private javax.swing.JSpinner spinnerVerticalOffset;
    // End of variables declaration//GEN-END:variables

    private final Collection<WPObject> objects;
    private final File file;
    private final Map<WPObject, Point3i> offsets = new HashMap<>();
    private final ColourScheme colourScheme;

    private static final long serialVersionUID = 1L;
}
