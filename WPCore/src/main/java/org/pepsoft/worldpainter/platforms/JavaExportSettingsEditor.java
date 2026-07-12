/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.platforms;

import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.exporting.ExportSettings;
import org.pepsoft.worldpainter.exporting.ExportSettingsEditor;

import static org.pepsoft.worldpainter.Platform.Capability.NAME_BASED;
import static org.pepsoft.worldpainter.Platform.Capability.PRECALCULATED_LIGHT;
import static org.pepsoft.worldpainter.platforms.JavaExportSettings.FloatMode.*;

/**
 *
 * @author Pepijn
 */
public class JavaExportSettingsEditor extends ExportSettingsEditor {

    /**
     * Creates new form Java1_15PostProcessorSettings
     */
    public JavaExportSettingsEditor(Platform platform) {
        this.platform = platform;
        initComponents();
    }

    @Override
    public void setExportSettings(ExportSettings exportSettings) {
        JavaExportSettings javaSettings = (JavaExportSettings) exportSettings;
        switch (javaSettings.waterMode) {
            case LEAVE_FLOATING:
                radioButtonWaterFloat.setSelected(true);
                break;
            case DROP:
                radioButtonWaterDrop.setSelected(true);
                break;
            default:
                throw new IllegalArgumentException("Invalid water mode " + javaSettings.waterMode);
        }
        switch (javaSettings.lavaMode) {
            case LEAVE_FLOATING:
                radioButtonLavaFloat.setSelected(true);
                break;
            case DROP:
                radioButtonLavaDrop.setSelected(true);
                break;
            default:
                throw new IllegalArgumentException("Invalid lava mode " + javaSettings.lavaMode);
        }
        switch (javaSettings.sandMode) {
            case LEAVE_FLOATING:
                radioButtonSandFloat.setSelected(true);
                break;
            case DROP:
                radioButtonSandDrop.setSelected(true);
                break;
            case SUPPORT:
                radioButtonSandSupport.setSelected(true);
                break;
        }
        switch (javaSettings.gravelMode) {
            case LEAVE_FLOATING:
                radioButtonGravelFloat.setSelected(true);
                break;
            case DROP:
                radioButtonGravelDrop.setSelected(true);
                break;
            case SUPPORT:
                radioButtonGravelSupport.setSelected(true);
                break;
        }
        switch (javaSettings.cementMode) {
            case LEAVE_FLOATING:
                radioButtonCementFloat.setSelected(true);
                break;
            case DROP:
                radioButtonCementDrop.setSelected(true);
                break;
            case SUPPORT:
                radioButtonCementSupport.setSelected(true);
                break;
        }
        checkBoxWaterFlow.setSelected(javaSettings.flowWater);
        checkBoxLavaFlow.setSelected(javaSettings.flowLava);
        checkBoxSkyLight.setSelected(javaSettings.calculateSkyLight);
        checkBoxBlockLight.setSelected(javaSettings.calculateBlockLight);
        checkBoxLeafDistance.setSelected(javaSettings.calculateLeafDistance);
        checkBoxRemoveFloatingLeaves.setSelected(javaSettings.removeFloatingLeaves);
        checkBoxMakeAllLeavesPersistent.setSelected(javaSettings.makeAllLeavesPersistent);
        checkBoxRemovePlants.setSelected(javaSettings.isRemovePlants());
        setControlStates();
    }

    @Override
    public JavaExportSettings getExportSettings() {
        return new JavaExportSettings(
                radioButtonWaterFloat.isSelected() ? LEAVE_FLOATING : DROP,
                radioButtonLavaFloat.isSelected() ? LEAVE_FLOATING : DROP,
                radioButtonSandFloat.isSelected() ? LEAVE_FLOATING : (radioButtonSandSupport.isSelected() ? SUPPORT : DROP),
                radioButtonGravelFloat.isSelected() ? LEAVE_FLOATING : (radioButtonGravelSupport.isSelected() ? SUPPORT : DROP),
                radioButtonCementFloat.isSelected() ? LEAVE_FLOATING : (radioButtonCementSupport.isSelected() ? SUPPORT : DROP),
                checkBoxWaterFlow.isSelected(),
                checkBoxLavaFlow.isSelected(),
                checkBoxSkyLight.isSelected(),
                checkBoxBlockLight.isSelected(),
                checkBoxLeafDistance.isSelected(),
                checkBoxLeafDistance.isSelected() && checkBoxRemoveFloatingLeaves.isSelected(),
                checkBoxMakeAllLeavesPersistent.isSelected(),
                checkBoxRemovePlants.isSelected());
    }

    private void setControlStates() {
        checkBoxSkyLight.setEnabled(platform.capabilities.contains(PRECALCULATED_LIGHT));
        checkBoxBlockLight.setEnabled(platform.capabilities.contains(PRECALCULATED_LIGHT));
        checkBoxLeafDistance.setEnabled(platform.capabilities.contains(NAME_BASED));
        checkBoxRemoveFloatingLeaves.setEnabled(platform.capabilities.contains(NAME_BASED) && checkBoxLeafDistance.isSelected());
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The
     * content of this method is always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        buttonGroup2 = new javax.swing.ButtonGroup();
        buttonGroup3 = new javax.swing.ButtonGroup();
        buttonGroup4 = new javax.swing.ButtonGroup();
        buttonGroup5 = new javax.swing.ButtonGroup();
        jLabel1 = new javax.swing.JLabel();
        radioButtonWaterFloat = new javax.swing.JRadioButton();
        radioButtonWaterDrop = new javax.swing.JRadioButton();
        checkBoxWaterFlow = new javax.swing.JCheckBox();
        jLabel2 = new javax.swing.JLabel();
        radioButtonLavaFloat = new javax.swing.JRadioButton();
        radioButtonLavaDrop = new javax.swing.JRadioButton();
        checkBoxLavaFlow = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        radioButtonGravelFloat = new javax.swing.JRadioButton();
        radioButtonGravelSupport = new javax.swing.JRadioButton();
        radioButtonGravelDrop = new javax.swing.JRadioButton();
        jLabel4 = new javax.swing.JLabel();
        radioButtonCementFloat = new javax.swing.JRadioButton();
        radioButtonCementSupport = new javax.swing.JRadioButton();
        radioButtonCementDrop = new javax.swing.JRadioButton();
        radioButtonSandFloat = new javax.swing.JRadioButton();
        jLabel5 = new javax.swing.JLabel();
        radioButtonSandSupport = new javax.swing.JRadioButton();
        radioButtonSandDrop = new javax.swing.JRadioButton();
        checkBoxLeafDistance = new javax.swing.JCheckBox();
        jLabel6 = new javax.swing.JLabel();
        checkBoxRemoveFloatingLeaves = new javax.swing.JCheckBox();
        checkBoxSkyLight = new javax.swing.JCheckBox();
        jLabel7 = new javax.swing.JLabel();
        checkBoxBlockLight = new javax.swing.JCheckBox();
        checkBoxMakeAllLeavesPersistent = new javax.swing.JCheckBox();
        checkBoxRemovePlants = new javax.swing.JCheckBox();
        jLabel8 = new javax.swing.JLabel();

        jLabel1.setText(org.pepsoft.worldpainter.WPI18n.s("wp.water.a306efea8b"));

        buttonGroup1.add(radioButtonWaterFloat);
        radioButtonWaterFloat.setText(org.pepsoft.worldpainter.WPI18n.s("wp.leave.floating.48e326abb8"));

        buttonGroup1.add(radioButtonWaterDrop);
        radioButtonWaterDrop.setText(org.pepsoft.worldpainter.WPI18n.s("wp.drop.6e9d25362c"));

        checkBoxWaterFlow.setText(org.pepsoft.worldpainter.WPI18n.s("wp.make.unbounded.water.flow.ee3d4bbbef"));

        jLabel2.setText(org.pepsoft.worldpainter.WPI18n.s("wp.lava.2ffeadf328"));

        buttonGroup2.add(radioButtonLavaFloat);
        radioButtonLavaFloat.setText(org.pepsoft.worldpainter.WPI18n.s("wp.leave.floating.48e326abb8"));

        buttonGroup2.add(radioButtonLavaDrop);
        radioButtonLavaDrop.setText(org.pepsoft.worldpainter.WPI18n.s("wp.drop.6e9d25362c"));

        checkBoxLavaFlow.setText(org.pepsoft.worldpainter.WPI18n.s("wp.make.unbounded.lava.flow.7ec061ca82"));

        jLabel3.setText(org.pepsoft.worldpainter.WPI18n.s("wp.gravel.16fef3b022"));

        buttonGroup3.add(radioButtonGravelFloat);
        radioButtonGravelFloat.setText(org.pepsoft.worldpainter.WPI18n.s("wp.leave.floating.48e326abb8"));

        buttonGroup3.add(radioButtonGravelSupport);
        radioButtonGravelSupport.setText(org.pepsoft.worldpainter.WPI18n.s("wp.support.with.stone.f668903390"));

        buttonGroup3.add(radioButtonGravelDrop);
        radioButtonGravelDrop.setText(org.pepsoft.worldpainter.WPI18n.s("wp.drop.6e9d25362c"));

        jLabel4.setText(org.pepsoft.worldpainter.WPI18n.s("wp.cement.553513360b"));

        buttonGroup4.add(radioButtonCementFloat);
        radioButtonCementFloat.setText(org.pepsoft.worldpainter.WPI18n.s("wp.leave.floating.48e326abb8"));

        buttonGroup4.add(radioButtonCementSupport);
        radioButtonCementSupport.setText(org.pepsoft.worldpainter.WPI18n.s("wp.support.with.stone.f668903390"));

        buttonGroup4.add(radioButtonCementDrop);
        radioButtonCementDrop.setText(org.pepsoft.worldpainter.WPI18n.s("wp.drop.6e9d25362c"));

        buttonGroup5.add(radioButtonSandFloat);
        radioButtonSandFloat.setText(org.pepsoft.worldpainter.WPI18n.s("wp.leave.floating.48e326abb8"));

        jLabel5.setText(org.pepsoft.worldpainter.WPI18n.s("wp.sand.b56bb34bfc"));

        buttonGroup5.add(radioButtonSandSupport);
        radioButtonSandSupport.setText(org.pepsoft.worldpainter.WPI18n.s("wp.support.with.sandstone.99317bc538"));

        buttonGroup5.add(radioButtonSandDrop);
        radioButtonSandDrop.setText(org.pepsoft.worldpainter.WPI18n.s("wp.drop.6e9d25362c"));

        checkBoxLeafDistance.setText(org.pepsoft.worldpainter.WPI18n.s("wp.calculate.distance.property.b6e48c09b3"));
        checkBoxLeafDistance.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkBoxLeafDistanceActionPerformed(evt);
            }
        });

        jLabel6.setText(org.pepsoft.worldpainter.WPI18n.s("wp.leaves.46d06d859f"));

        checkBoxRemoveFloatingLeaves.setText(org.pepsoft.worldpainter.WPI18n.s("wp.remove.floating.leaf.blocks.30d94e4e74"));

        checkBoxSkyLight.setText(org.pepsoft.worldpainter.WPI18n.s("wp.calculate.sky.light.c715e9f0cc"));

        jLabel7.setText(org.pepsoft.worldpainter.WPI18n.s("wp.light.a5277579c2"));

        checkBoxBlockLight.setText(org.pepsoft.worldpainter.WPI18n.s("wp.calculate.block.light.1e458b6b0e"));

        checkBoxMakeAllLeavesPersistent.setText(org.pepsoft.worldpainter.WPI18n.s("wp.html.make.i.all.66fdfb4239"));

        checkBoxRemovePlants.setText(org.pepsoft.worldpainter.WPI18n.s("wp.remove.from.invalid.blocks.3a5e5bf4b4"));

        jLabel8.setText(org.pepsoft.worldpainter.WPI18n.s("wp.plants.f2848999af"));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4)
                    .addComponent(jLabel5)
                    .addComponent(jLabel6)
                    .addComponent(jLabel7)
                    .addComponent(jLabel8))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(checkBoxRemovePlants)
                    .addComponent(checkBoxMakeAllLeavesPersistent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(checkBoxLeafDistance)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(checkBoxSkyLight)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(checkBoxBlockLight))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(radioButtonSandFloat)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(radioButtonSandSupport)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(radioButtonSandDrop))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(radioButtonCementFloat)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(radioButtonCementSupport)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(radioButtonCementDrop))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(radioButtonLavaFloat)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(radioButtonLavaDrop))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(radioButtonWaterFloat)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(radioButtonWaterDrop))
                    .addComponent(checkBoxWaterFlow)
                    .addComponent(checkBoxLavaFlow)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(radioButtonGravelFloat)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(radioButtonGravelSupport)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(radioButtonGravelDrop))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addComponent(checkBoxRemoveFloatingLeaves)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(radioButtonWaterFloat)
                    .addComponent(radioButtonWaterDrop))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(checkBoxWaterFlow)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(radioButtonLavaFloat)
                    .addComponent(radioButtonLavaDrop))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(checkBoxLavaFlow)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(radioButtonSandFloat)
                    .addComponent(jLabel5)
                    .addComponent(radioButtonSandSupport)
                    .addComponent(radioButtonSandDrop))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(radioButtonGravelFloat)
                    .addComponent(jLabel3)
                    .addComponent(radioButtonGravelSupport)
                    .addComponent(radioButtonGravelDrop))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(radioButtonCementFloat)
                    .addComponent(jLabel4)
                    .addComponent(radioButtonCementSupport)
                    .addComponent(radioButtonCementDrop))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBoxSkyLight)
                    .addComponent(jLabel7)
                    .addComponent(checkBoxBlockLight))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBoxLeafDistance)
                    .addComponent(jLabel6))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(checkBoxRemoveFloatingLeaves)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(checkBoxMakeAllLeavesPersistent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkBoxRemovePlants)
                    .addComponent(jLabel8))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void checkBoxLeafDistanceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkBoxLeafDistanceActionPerformed
        setControlStates();
    }//GEN-LAST:event_checkBoxLeafDistanceActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.ButtonGroup buttonGroup2;
    private javax.swing.ButtonGroup buttonGroup3;
    private javax.swing.ButtonGroup buttonGroup4;
    private javax.swing.ButtonGroup buttonGroup5;
    private javax.swing.JCheckBox checkBoxBlockLight;
    private javax.swing.JCheckBox checkBoxLavaFlow;
    private javax.swing.JCheckBox checkBoxLeafDistance;
    private javax.swing.JCheckBox checkBoxMakeAllLeavesPersistent;
    private javax.swing.JCheckBox checkBoxRemoveFloatingLeaves;
    private javax.swing.JCheckBox checkBoxRemovePlants;
    private javax.swing.JCheckBox checkBoxSkyLight;
    private javax.swing.JCheckBox checkBoxWaterFlow;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JRadioButton radioButtonCementDrop;
    private javax.swing.JRadioButton radioButtonCementFloat;
    private javax.swing.JRadioButton radioButtonCementSupport;
    private javax.swing.JRadioButton radioButtonGravelDrop;
    private javax.swing.JRadioButton radioButtonGravelFloat;
    private javax.swing.JRadioButton radioButtonGravelSupport;
    private javax.swing.JRadioButton radioButtonLavaDrop;
    private javax.swing.JRadioButton radioButtonLavaFloat;
    private javax.swing.JRadioButton radioButtonSandDrop;
    private javax.swing.JRadioButton radioButtonSandFloat;
    private javax.swing.JRadioButton radioButtonSandSupport;
    private javax.swing.JRadioButton radioButtonWaterDrop;
    private javax.swing.JRadioButton radioButtonWaterFloat;
    // End of variables declaration//GEN-END:variables

    private final Platform platform;
}