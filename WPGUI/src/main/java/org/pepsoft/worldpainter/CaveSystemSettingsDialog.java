/*
 * WorldPainter Languages, an unofficial localization fork of WorldPainter
 * (https://github.com/saplome/WorldPainter-LANGUAGES).
 * Copyright © 2026 saplome
 * Licensed under the GNU General Public License, version 3.
 */
package org.pepsoft.worldpainter;

import org.pepsoft.worldpainter.layers.exporters.CaveSystemExporter.CaveSystemSettings;

import javax.swing.*;
import java.awt.*;

public final class CaveSystemSettingsDialog extends WorldPainterDialog {
    public CaveSystemSettingsDialog(Window parent, CaveSystemSettings source) {
        super(parent);
        settings = source.clone();
        setTitle(text("settings.title"));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        createControls();
        load(settings);

        final JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(text("tab.general"), tab(generalPanel(), "hint.general"));
        tabs.addTab(text("tab.density"), tab(densityPanel(), "hint.density"));
        tabs.addTab(text("tab.chambers"), tab(chambersPanel(), "hint.chambers"));
        tabs.addTab(text("tab.tunnels"), tab(tunnelsPanel(), "hint.tunnels"));
        tabs.addTab(text("tab.boundaries"), tab(boundariesPanel(), "hint.boundaries"));
        tabs.addTab(text("tab.biomes"), tab(biomesPanel(), "hint.biomes"));
        tabs.addTab(text("tab.liquids"), tab(liquidsPanel(), "hint.liquids"));

        final JButton defaults = new JButton(text("defaults"));
        defaults.addActionListener(e -> load(new CaveSystemSettings()));
        final JButton ok = new JButton(WPI18n.s("ui.button.ok"));
        ok.addActionListener(e -> { save(); ok(); });
        final JButton cancel = new JButton(WPI18n.s("ui.button.cancel"));
        cancel.addActionListener(e -> cancel());
        getRootPane().setDefaultButton(ok);

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        buttons.add(defaults);
        buttons.add(ok);
        buttons.add(cancel);
        final JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        final JPanel versionPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        versionPanel.add(new JLabel(text("generationVersion")));
        versionPanel.add(generationVersion);
        content.add(versionPanel, BorderLayout.NORTH);
        content.add(tabs, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        scaleToUI();
        setPreferredSize(new java.awt.Dimension(780, 720));
        pack();
        setMinimumSize(new java.awt.Dimension(680, 560));
        setLocationRelativeTo(parent);
    }

    public CaveSystemSettings getSettings() {
        return settings;
    }

    private void createControls() {
        generationVersion = new JComboBox<>(new String[] {
                text("versionL200"), text("versionL201Experimental") });
        applyEverywhere = check("applyEverywhere");
        customMinimumY = check("customMinimumY");
        minimumLevel = spin(0, 15, 1);
        minimumY = spin(-2048, 2048, 1);
        surfaceBreaking = check("surfaceBreaking");
        leaveWater = check("leaveWater");
        fixedWaterLevel = check("fixedWaterLevel");
        waterLevel = spin(-2048, 2048, 1);
        floodWithLava = check("floodWithLava");

        rotation = check("domainRotation");
        warpStrength = spin(0, 64, 1);
        warpScale = spin(32, 256, 4);
        detail = spin(0, 200, 5);
        union = spin(0, 100, 1);
        overallDensity = spin(0, 200, 5);
        caveSpacing = spin(50, 300, 5);

        cheeseFreq = spin(0, 200, 5);
        cheeseSize = spin(50, 200, 5);
        cheeseH = spin(40, 250, 5);
        cheeseV = spin(40, 250, 5);
        grandFreq = spin(0, 200, 5);
        grandSize = spin(50, 250, 5);
        grandH = spin(50, 250, 5);
        grandV = spin(50, 250, 5);
        megaFrequency = spin(0, 100, 1);
        megaH = spin(40, 200, 5);
        megaV = spin(40, 200, 5);
        megaTallChance = spin(0, 100, 1);

        spaghettiFreq = spin(0, 200, 5);
        spaghettiWidth = spin(40, 220, 5);
        backboneFreq = spin(0, 200, 5);
        backboneWidth = spin(40, 220, 5);
        noodleFreq = spin(0, 200, 5);
        noodleWidth = spin(40, 220, 5);

        bottom = spin(0, 128, 1);
        surface = spin(0, 128, 1);
        fade = spin(0, 64, 1);
        boundaryWarp = spin(0, 32, 1);
        openings = spin(0, 100, 1);
        openingStrength = spin(0, 200, 5);
        openingDepth = spin(4, 64, 1);
        openingScale = spin(64, 400, 4);
        openingSoftness = spin(0, 100, 1);
        openCheese = check("openCheese");
        openGrand = check("openGrand");
        openSpaghetti = check("openSpaghetti");
        openBackbone = check("openBackbone");
        openNoodles = check("openNoodles");

        lushCaves = check("lushCaves");
        lushRarity = spin(0, 250, 5);
        lushRegionScale = spin(5, 200, 5);
        megaLushChance = spin(0, 100, 1);
        enhancedLush = check("enhancedLush");
        lushPoolFrequency = spin(0, 100, 1);
        lushPoolSpacing = spin(12, 48, 1);
        lushPoolMinRadius = spin(2, 12, 1);
        lushPoolMaxRadius = spin(2, 16, 1);
        lushPoolDryChance = spin(0, 100, 1);

        dripstoneCaves = check("dripstoneCaves");
        dripstoneRarity = spin(0, 250, 5);
        dripstoneRegionScale = spin(5, 200, 5);
        megaDripstoneChance = spin(0, 100, 1);
        mixedPatchChance = spin(0, 100, 1);
        enhancedDripstone = check("enhancedDripstone");
        dripstonePatchCoverage = spin(0, 100, 1);
        smallDripstoneFrequency = spin(0, 100, 1);
        largeDripstoneFrequency = spin(0, 100, 1);
        largeDripstoneSpacing = spin(16, 64, 1);
        largeDripstoneMaxRadius = spin(4, 32, 1);
        largeDripstoneSearchHeight = spin(24, 192, 4);

        waterFreq = spin(0, 100, 1);
        waterScale = spin(64, 400, 4);
        waterChambers = check("waterInChambers");
        lavaFreq = spin(0, 100, 1);
        lavaScale = spin(64, 400, 4);
        lavaHeight = spin(5, 64, 1);
        lavaBackbone = check("lavaInBackbone");
        lavaChambers = check("lavaInChambers");

        fixedWaterLevel.addActionListener(e -> updateEnabledState());
        customMinimumY.addActionListener(e -> updateEnabledState());
        lushCaves.addActionListener(e -> updateEnabledState());
        dripstoneCaves.addActionListener(e -> updateEnabledState());
        enhancedLush.addActionListener(e -> updateEnabledState());
        enhancedDripstone.addActionListener(e -> updateEnabledState());
    }

    private JPanel generalPanel() {
        final JPanel p = grid(); int r = 0;
        r = section(p, r, "section.layerApplication");
        r = row(p, r, "minimumLevel", minimumLevel, text("levelUnit"));
        r = box(p, r, applyEverywhere);
        r = box(p, r, customMinimumY);
        r = row(p, r, "minimumY", minimumY, text("blocks"));
        r = section(p, r, "section.surfaceAndFluids");
        r = box(p, r, surfaceBreaking);
        r = box(p, r, leaveWater);
        r = box(p, r, fixedWaterLevel);
        r = row(p, r, "waterLevel", waterLevel, "Y");
        box(p, r, floodWithLava);
        return p;
    }

    private JPanel densityPanel() {
        final JPanel p = grid(); int r = 0;
        r = section(p, r, "section.densityField");
        r = box(p, r, rotation);
        r = row(p, r, "warpStrength", warpStrength, text("blocks"));
        r = row(p, r, "warpScale", warpScale, text("blocks"));
        r = row(p, r, "detailStrength", detail, "%");
        r = row(p, r, "unionSmoothness", union, "%");
        r = row(p, r, "overallDensity", overallDensity, "%");
        row(p, r, "caveSpacing", caveSpacing, "%");
        return p;
    }

    private JPanel chambersPanel() {
        final JPanel p = grid(); int r = 0;
        r = section(p, r, "section.cheese");
        r = row(p, r, "cheeseFrequency", cheeseFreq, "%");
        r = row(p, r, "cheeseSize", cheeseSize, "%");
        r = row(p, r, "cheeseHorizontalScale", cheeseH, "%");
        r = row(p, r, "cheeseVerticalScale", cheeseV, "%");
        r = section(p, r, "section.grandNoise");
        r = row(p, r, "grandFrequency", grandFreq, "%");
        r = row(p, r, "grandSize", grandSize, "%");
        r = row(p, r, "grandHorizontalScale", grandH, "%");
        r = row(p, r, "grandVerticalScale", grandV, "%");
        r = section(p, r, "section.megacaverns");
        r = row(p, r, "megaFrequency", megaFrequency, "%");
        r = row(p, r, "megaHorizontalScale", megaH, "%");
        r = row(p, r, "megaVerticalScale", megaV, "%");
        row(p, r, "megaTallChance", megaTallChance, "%");
        return p;
    }

    private JPanel tunnelsPanel() {
        final JPanel p = grid(); int r = 0;
        r = section(p, r, "section.spaghetti");
        r = row(p, r, "spaghettiFrequency", spaghettiFreq, "%");
        r = row(p, r, "spaghettiWidth", spaghettiWidth, "%");
        r = section(p, r, "section.backbone");
        r = row(p, r, "backboneFrequency", backboneFreq, "%");
        r = row(p, r, "backboneWidth", backboneWidth, "%");
        r = section(p, r, "section.noodles");
        r = row(p, r, "noodleFrequency", noodleFreq, "%");
        row(p, r, "noodleWidth", noodleWidth, "%");
        return p;
    }

    private JPanel boundariesPanel() {
        final JPanel p = grid(); int r = 0;
        r = section(p, r, "section.boundaries");
        r = row(p, r, "bottomClearance", bottom, text("blocks"));
        r = row(p, r, "surfaceClearance", surface, text("blocks"));
        r = row(p, r, "boundaryFade", fade, text("blocks"));
        r = row(p, r, "boundaryWarp", boundaryWarp, text("blocks"));
        r = section(p, r, "section.openings");
        r = row(p, r, "surfaceOpeningFrequency", openings, "%");
        r = row(p, r, "surfaceOpeningStrength", openingStrength, "%");
        r = row(p, r, "surfaceOpeningDepth", openingDepth, text("blocks"));
        r = row(p, r, "surfaceOpeningScale", openingScale, text("blocks"));
        r = row(p, r, "surfaceOpeningSoftness", openingSoftness, "%");
        r = box(p, r, openCheese);
        r = box(p, r, openGrand);
        r = box(p, r, openSpaghetti);
        r = box(p, r, openBackbone);
        box(p, r, openNoodles);
        return p;
    }

    private JPanel biomesPanel() {
        final JPanel p = grid(); int r = 0;
        r = section(p, r, "section.lush");
        r = box(p, r, lushCaves);
        r = row(p, r, "lushRarity", lushRarity, text("thresholdUnit"));
        r = row(p, r, "lushRegionScale", lushRegionScale, "%");
        r = row(p, r, "megaLushChance", megaLushChance, "%");
        r = box(p, r, enhancedLush);
        r = row(p, r, "lushPoolFrequency", lushPoolFrequency, "%");
        r = row(p, r, "lushPoolSpacing", lushPoolSpacing, text("blocks"));
        r = row(p, r, "lushPoolMinRadius", lushPoolMinRadius, text("blocks"));
        r = row(p, r, "lushPoolMaxRadius", lushPoolMaxRadius, text("blocks"));
        r = row(p, r, "lushPoolDryChance", lushPoolDryChance, "%");
        r = section(p, r, "section.dripstone");
        r = box(p, r, dripstoneCaves);
        r = row(p, r, "dripstoneRarity", dripstoneRarity, text("thresholdUnit"));
        r = row(p, r, "dripstoneRegionScale", dripstoneRegionScale, "%");
        r = row(p, r, "megaDripstoneChance", megaDripstoneChance, "%");
        r = row(p, r, "mixedPatchChance", mixedPatchChance, "%");
        r = box(p, r, enhancedDripstone);
        r = row(p, r, "dripstonePatchCoverage", dripstonePatchCoverage, "%");
        r = row(p, r, "smallDripstoneFrequency", smallDripstoneFrequency, "%");
        r = row(p, r, "largeDripstoneFrequency", largeDripstoneFrequency, "%");
        r = row(p, r, "largeDripstoneSpacing", largeDripstoneSpacing, text("blocks"));
        r = row(p, r, "largeDripstoneMaxRadius", largeDripstoneMaxRadius, text("blocks"));
        row(p, r, "largeDripstoneSearchHeight", largeDripstoneSearchHeight, text("blocks"));
        return p;
    }

    private JPanel liquidsPanel() {
        final JPanel p = grid(); int r = 0;
        r = section(p, r, "section.aquifers");
        r = row(p, r, "aquiferFrequency", waterFreq, "%");
        r = row(p, r, "aquiferScale", waterScale, text("blocks"));
        r = box(p, r, waterChambers);
        r = section(p, r, "section.lava");
        r = row(p, r, "lavaFrequency", lavaFreq, "%");
        r = row(p, r, "lavaScale", lavaScale, text("blocks"));
        r = row(p, r, "lavaZoneHeight", lavaHeight, text("blocks"));
        r = box(p, r, lavaBackbone);
        box(p, r, lavaChambers);
        return p;
    }

    private JComponent tab(JPanel panel, String hintKey) {
        final JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        final JLabel hint = new JLabel("<html><body style='width:610px'>" + text(hintKey) + "</body></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        wrapper.add(hint, BorderLayout.NORTH);
        wrapper.add(panel, BorderLayout.CENTER);
        final JScrollPane scroll = new JScrollPane(wrapper);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private void load(CaveSystemSettings s) {
        generationVersion.setSelectedIndex(s.getGenerationVersion());
        set(minimumLevel, s.getMinimumLevel());
        applyEverywhere.setSelected(s.getMinimumLevel() > 0);
        customMinimumY.setSelected(s.getMinimumY() != Integer.MIN_VALUE);
        set(minimumY, customMinimumY.isSelected() ? s.getMinimumY() : -64);
        surfaceBreaking.setSelected(s.isSurfaceBreaking());
        leaveWater.setSelected(s.isLeaveWater());
        fixedWaterLevel.setSelected(s.getWaterLevel() != Integer.MIN_VALUE);
        set(waterLevel, fixedWaterLevel.isSelected() ? s.getWaterLevel() : 54);
        floodWithLava.setSelected(s.isFloodWithLava());
        rotation.setSelected(s.isDomainRotation());
        set(warpStrength,s.getWarpStrength()); set(warpScale,s.getWarpScale()); set(detail,s.getDetailStrength());
        set(union,s.getUnionSmoothness()); set(overallDensity,s.getOverallDensity()); set(caveSpacing,s.getCaveSpacing());
        set(cheeseFreq,s.getCheeseFrequency()); set(cheeseSize,s.getCheeseSize()); set(cheeseH,s.getCheeseHorizontalScale()); set(cheeseV,s.getCheeseVerticalScale());
        set(grandFreq,s.getGrandFrequency()); set(grandSize,s.getGrandSize()); set(grandH,s.getGrandHorizontalScale()); set(grandV,s.getGrandVerticalScale());
        set(megaFrequency,s.getMegaFrequency()); set(megaH,s.getMegaHorizontalScale()); set(megaV,s.getMegaVerticalScale()); set(megaTallChance,s.getMegaTallChance());
        set(spaghettiFreq,s.getSpaghettiFrequency()); set(spaghettiWidth,s.getSpaghettiWidth()); set(backboneFreq,s.getBackboneFrequency()); set(backboneWidth,s.getBackboneWidth()); set(noodleFreq,s.getNoodleFrequency()); set(noodleWidth,s.getNoodleWidth());
        set(bottom,s.getBottomClearance()); set(surface,s.getSurfaceClearance()); set(fade,s.getBoundaryFade()); set(boundaryWarp,s.getBoundaryWarp());
        set(openings,s.getSurfaceOpeningFrequency()); set(openingStrength,s.getSurfaceOpeningStrength()); set(openingDepth,s.getSurfaceOpeningDepth()); set(openingScale,s.getSurfaceOpeningScale()); set(openingSoftness,s.getSurfaceOpeningSoftness());
        openCheese.setSelected(s.isOpenCheese()); openGrand.setSelected(s.isOpenGrand()); openSpaghetti.setSelected(s.isOpenSpaghetti()); openBackbone.setSelected(s.isOpenBackbone()); openNoodles.setSelected(s.isOpenNoodles());
        lushCaves.setSelected(s.isLushCaves()); set(lushRarity,s.getLushRarity()); set(lushRegionScale,s.getLushRegionScale()); set(megaLushChance,s.getMegaLushChance()); enhancedLush.setSelected(s.isEnhancedLushFeatures());
        set(lushPoolFrequency,s.getLushPoolFrequency()); set(lushPoolSpacing,s.getLushPoolSpacing()); set(lushPoolMinRadius,s.getLushPoolMinRadius()); set(lushPoolMaxRadius,s.getLushPoolMaxRadius()); set(lushPoolDryChance,s.getLushPoolDryChance());
        dripstoneCaves.setSelected(s.isDripstoneCaves()); set(dripstoneRarity,s.getDripstoneRarity()); set(dripstoneRegionScale,s.getDripstoneRegionScale()); set(megaDripstoneChance,s.getMegaDripstoneChance()); set(mixedPatchChance,s.getMixedPatchChance()); enhancedDripstone.setSelected(s.isEnhancedDripstoneFeatures());
        set(dripstonePatchCoverage,s.getDripstonePatchCoverage()); set(smallDripstoneFrequency,s.getSmallDripstoneFrequency()); set(largeDripstoneFrequency,s.getLargeDripstoneFrequency()); set(largeDripstoneSpacing,s.getLargeDripstoneSpacing()); set(largeDripstoneMaxRadius,s.getLargeDripstoneMaxRadius()); set(largeDripstoneSearchHeight,s.getLargeDripstoneSearchHeight());
        set(waterFreq,s.getWaterFrequency()); set(waterScale,s.getWaterScale()); waterChambers.setSelected(s.isWaterInChambers());
        set(lavaFreq,s.getLavaFrequency()); set(lavaScale,s.getLavaScale()); set(lavaHeight,s.getLavaZoneHeight()); lavaBackbone.setSelected(s.isLavaInBackbone()); lavaChambers.setSelected(s.isLavaInChambers());
        updateEnabledState();
    }

    private void save() {
        settings.setGenerationVersion(generationVersion.getSelectedIndex());
        settings.setMinimumLevel(applyEverywhere.isSelected() ? Math.max(1, value(minimumLevel)) : 0);
        settings.setMinimumY(customMinimumY.isSelected() ? value(minimumY) : Integer.MIN_VALUE);
        settings.setSurfaceBreaking(surfaceBreaking.isSelected()); settings.setLeaveWater(leaveWater.isSelected());
        settings.setWaterLevel(fixedWaterLevel.isSelected() ? value(waterLevel) : Integer.MIN_VALUE); settings.setFloodWithLava(floodWithLava.isSelected());
        settings.setDomainRotation(rotation.isSelected()); settings.setWarpStrength(value(warpStrength)); settings.setWarpScale(value(warpScale)); settings.setDetailStrength(value(detail)); settings.setUnionSmoothness(value(union)); settings.setOverallDensity(value(overallDensity)); settings.setCaveSpacing(value(caveSpacing));
        settings.setCheeseFrequency(value(cheeseFreq)); settings.setCheeseSize(value(cheeseSize)); settings.setCheeseHorizontalScale(value(cheeseH)); settings.setCheeseVerticalScale(value(cheeseV));
        settings.setGrandFrequency(value(grandFreq)); settings.setGrandSize(value(grandSize)); settings.setGrandHorizontalScale(value(grandH)); settings.setGrandVerticalScale(value(grandV));
        settings.setMegaFrequency(value(megaFrequency)); settings.setMegaHorizontalScale(value(megaH)); settings.setMegaVerticalScale(value(megaV)); settings.setMegaTallChance(value(megaTallChance));
        settings.setSpaghettiFrequency(value(spaghettiFreq)); settings.setSpaghettiWidth(value(spaghettiWidth)); settings.setBackboneFrequency(value(backboneFreq)); settings.setBackboneWidth(value(backboneWidth)); settings.setNoodleFrequency(value(noodleFreq)); settings.setNoodleWidth(value(noodleWidth));
        settings.setBottomClearance(value(bottom)); settings.setSurfaceClearance(value(surface)); settings.setBoundaryFade(value(fade)); settings.setBoundaryWarp(value(boundaryWarp));
        settings.setSurfaceOpeningFrequency(value(openings)); settings.setSurfaceOpeningStrength(value(openingStrength)); settings.setSurfaceOpeningDepth(value(openingDepth)); settings.setSurfaceOpeningScale(value(openingScale)); settings.setSurfaceOpeningSoftness(value(openingSoftness));
        settings.setOpenCheese(openCheese.isSelected()); settings.setOpenGrand(openGrand.isSelected()); settings.setOpenSpaghetti(openSpaghetti.isSelected()); settings.setOpenBackbone(openBackbone.isSelected()); settings.setOpenNoodles(openNoodles.isSelected());
        settings.setLushCaves(lushCaves.isSelected()); settings.setLushRarity(value(lushRarity)); settings.setLushRegionScale(value(lushRegionScale)); settings.setMegaLushChance(value(megaLushChance)); settings.setEnhancedLushFeatures(enhancedLush.isSelected());
        settings.setLushPoolFrequency(value(lushPoolFrequency)); settings.setLushPoolSpacing(value(lushPoolSpacing)); settings.setLushPoolMinRadius(value(lushPoolMinRadius)); settings.setLushPoolMaxRadius(value(lushPoolMaxRadius)); settings.setLushPoolDryChance(value(lushPoolDryChance));
        settings.setDripstoneCaves(dripstoneCaves.isSelected()); settings.setDripstoneRarity(value(dripstoneRarity)); settings.setDripstoneRegionScale(value(dripstoneRegionScale)); settings.setMegaDripstoneChance(value(megaDripstoneChance)); settings.setMixedPatchChance(value(mixedPatchChance)); settings.setEnhancedDripstoneFeatures(enhancedDripstone.isSelected());
        settings.setDripstonePatchCoverage(value(dripstonePatchCoverage)); settings.setSmallDripstoneFrequency(value(smallDripstoneFrequency)); settings.setLargeDripstoneFrequency(value(largeDripstoneFrequency)); settings.setLargeDripstoneSpacing(value(largeDripstoneSpacing)); settings.setLargeDripstoneMaxRadius(value(largeDripstoneMaxRadius)); settings.setLargeDripstoneSearchHeight(value(largeDripstoneSearchHeight));
        settings.setWaterFrequency(value(waterFreq)); settings.setWaterScale(value(waterScale)); settings.setWaterInChambers(waterChambers.isSelected());
        settings.setLavaFrequency(value(lavaFreq)); settings.setLavaScale(value(lavaScale)); settings.setLavaZoneHeight(value(lavaHeight)); settings.setLavaInBackbone(lavaBackbone.isSelected()); settings.setLavaInChambers(lavaChambers.isSelected());
    }

    private void updateEnabledState() {
        minimumY.setEnabled(customMinimumY.isSelected());
        waterLevel.setEnabled(fixedWaterLevel.isSelected());
        setEnabled(lushCaves.isSelected(), lushRarity, lushRegionScale, megaLushChance, enhancedLush);
        setEnabled(lushCaves.isSelected() && enhancedLush.isSelected(), lushPoolFrequency, lushPoolSpacing, lushPoolMinRadius, lushPoolMaxRadius, lushPoolDryChance);
        setEnabled(dripstoneCaves.isSelected(), dripstoneRarity, dripstoneRegionScale, megaDripstoneChance, mixedPatchChance, enhancedDripstone);
        setEnabled(dripstoneCaves.isSelected() && enhancedDripstone.isSelected(), dripstonePatchCoverage, smallDripstoneFrequency, largeDripstoneFrequency, largeDripstoneSpacing, largeDripstoneMaxRadius, largeDripstoneSearchHeight);
    }

    private static void setEnabled(boolean enabled, JComponent... controls) {
        for (JComponent control: controls) control.setEnabled(enabled);
    }

    private static JPanel grid() {
        final JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(4, 8, 12, 8));
        return p;
    }

    private static GridBagConstraints constraints(int x, int y) {
        final GridBagConstraints c = new GridBagConstraints();
        c.gridx = x; c.gridy = y; c.insets = new Insets(3, 5, 3, 5);
        return c;
    }

    private static int section(JPanel p, int row, String key) {
        final GridBagConstraints c = constraints(0, row);
        c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        c.insets = new Insets(row == 0 ? 4 : 14, 4, 5, 4);
        final JLabel label = new JLabel(text(key));
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")));
        p.add(label, c);
        return row + 1;
    }

    private static int row(JPanel p, int row, String key, JSpinner spinner, String suffix) {
        GridBagConstraints c = constraints(0, row); c.anchor = GridBagConstraints.LINE_END;
        p.add(new JLabel(text(key)), c);
        c = constraints(1, row); c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
        p.add(spinner, c);
        c = constraints(2, row); c.anchor = GridBagConstraints.LINE_START;
        p.add(new JLabel(suffix), c);
        return row + 1;
    }

    private static int box(JPanel p, int row, JCheckBox box) {
        final GridBagConstraints c = constraints(0, row);
        c.gridwidth = 3; c.anchor = GridBagConstraints.LINE_START;
        p.add(box, c);
        return row + 1;
    }

    private static JCheckBox check(String key) { return new JCheckBox(text(key)); }
    private static JSpinner spin(int min, int max, int step) { return new JSpinner(new SpinnerNumberModel(min, min, max, step)); }
    private static void set(JSpinner spinner, int value) { spinner.setValue(value); }
    private static int value(JSpinner spinner) { return ((Number) spinner.getValue()).intValue(); }
    private static String text(String key) { return WPI18n.s("ui.caveSystem." + key); }

    private final CaveSystemSettings settings;
    private JComboBox<String> generationVersion;
    private JCheckBox applyEverywhere, customMinimumY, surfaceBreaking, leaveWater, fixedWaterLevel, floodWithLava;
    private JCheckBox rotation, openCheese, openGrand, openSpaghetti, openBackbone, openNoodles;
    private JCheckBox lushCaves, enhancedLush, dripstoneCaves, enhancedDripstone;
    private JCheckBox waterChambers, lavaBackbone, lavaChambers;
    private JSpinner minimumLevel, minimumY, waterLevel;
    private JSpinner warpStrength, warpScale, detail, union, overallDensity, caveSpacing;
    private JSpinner cheeseFreq, cheeseSize, cheeseH, cheeseV, grandFreq, grandSize, grandH, grandV;
    private JSpinner megaFrequency, megaH, megaV, megaTallChance;
    private JSpinner spaghettiFreq, spaghettiWidth, backboneFreq, backboneWidth, noodleFreq, noodleWidth;
    private JSpinner bottom, surface, fade, boundaryWarp, openings, openingStrength, openingDepth, openingScale, openingSoftness;
    private JSpinner lushRarity, lushRegionScale, megaLushChance, lushPoolFrequency, lushPoolSpacing, lushPoolMinRadius, lushPoolMaxRadius, lushPoolDryChance;
    private JSpinner dripstoneRarity, dripstoneRegionScale, megaDripstoneChance, mixedPatchChance, dripstonePatchCoverage, smallDripstoneFrequency, largeDripstoneFrequency, largeDripstoneSpacing, largeDripstoneMaxRadius, largeDripstoneSearchHeight;
    private JSpinner waterFreq, waterScale, lavaFreq, lavaScale, lavaHeight;
    private static final long serialVersionUID = 1L;
}
