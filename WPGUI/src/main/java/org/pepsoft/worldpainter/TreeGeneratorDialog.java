/*
 * This file is part of WorldPainter Languages, an unofficial localization
 * fork of WorldPainter (https://github.com/saplome/WorldPainter-LANGUAGES).
 *
 * Copyright © 2026 saplome. Written in 2026 for WorldPainter Languages;
 * it is not part of the original WorldPainter by pepsoft.org.
 *
 * Licensed under the GNU General Public License, version 3.
 * See the LICENSE file for details.
 */

package org.pepsoft.worldpainter;

import org.jnbt.*;
import org.pepsoft.minecraft.Entity;
import org.pepsoft.minecraft.Material;
import org.pepsoft.minecraft.TileEntity;
import org.pepsoft.util.AttributeKey;
import org.pepsoft.worldpainter.objects.WPObject;
import org.pepsoft.worldpainter.util.FileFilter;
import org.pepsoft.worldpainter.util.FileUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.imageio.ImageIO;
import javax.vecmath.Point3i;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static java.util.Collections.emptyList;
import static org.pepsoft.worldpainter.WPI18n.s;

/** Pure Java port of the browser tree generator. */
public final class TreeGeneratorDialog extends WorldPainterDialog {
    public TreeGeneratorDialog(Window parent) {
        super(parent, false);
        setTitle(s("ui.treeGenerator.title"));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        settings = Settings.defaults(Preset.OAK);
        buildUi();
        installListeners();
        updateControlAvailability();
        regenerate();
        setSize(1320, 800);
        setMinimumSize(new java.awt.Dimension(1000, 650));
        setLocationRelativeTo(parent);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.setPreferredSize(new java.awt.Dimension(230, 500));
        left.add(new JLabel(s("ui.treeGenerator.species")), BorderLayout.NORTH);
        speciesList = new JList<>(Preset.values());
        speciesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        speciesList.setSelectedValue(Preset.OAK, true);
        speciesList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Preset p) setText(p.displayName());
                return this;
            }
        });
        left.add(new JScrollPane(speciesList), BorderLayout.CENTER);
        JPanel seedPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        seedPanel.setBorder(BorderFactory.createTitledBorder(s("ui.treeGenerator.variation")));
        seedField = spinner(0, 999999999, 42817, 1);
        variation = slider(0, 100, 72);
        JButton random = new JButton(s("ui.treeGenerator.newVariant"));
        random.addActionListener(e -> { seedField.setValue(new Random().nextInt(1_000_000)); regenerate(); });
        seedPanel.add(labelled(s("ui.treeGenerator.seed"), seedField));
        seedPanel.add(labelled(s("ui.treeGenerator.seedVariation"), variation));
        seedPanel.add(random);
        left.add(seedPanel, BorderLayout.SOUTH);
        root.add(left, BorderLayout.WEST);

        preview = new TreePreviewPanel(App.getInstance().getColourScheme());
        preview.setBorder(BorderFactory.createLoweredBevelBorder());
        JPanel centre = new JPanel(new BorderLayout(4, 4));
        centre.add(preview, BorderLayout.CENTER);
        status = new JLabel(" ");
        centre.add(status, BorderLayout.SOUTH);
        root.add(centre, BorderLayout.CENTER);

        tabs = new JTabbedPane();
        tabs.setPreferredSize(new java.awt.Dimension(410, 500));
        tabs.addTab(s("ui.treeGenerator.treeSettings"), new JScrollPane(makeTreeSettingsPanel()));
        tabs.addTab(s("ui.treeGenerator.mushroomSettings"), new JScrollPane(makeMushroomPanel()));
        root.add(tabs, BorderLayout.EAST);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton resetView = new JButton(s("ui.treeGenerator.resetView"));
        resetView.addActionListener(e -> preview.resetView());
        JButton export = new JButton(s("ui.treeGenerator.exportSchem"));
        export.addActionListener(e -> exportSchem());
        JButton close = new JButton(s("ui.treeGenerator.close"));
        close.addActionListener(e -> dispose());
        bottom.add(resetView); bottom.add(export); bottom.add(close);
        root.add(bottom, BorderLayout.SOUTH);

    }

    private JPanel makeTreeSettingsPanel() {
        JPanel p=verticalPanel();p.add(makeTreePanel());p.add(Box.createVerticalStrut(8));p.add(makeFoliagePanel());return p;
    }

    private JPanel makeTreePanel() {
        JPanel p=verticalPanel();
        height=spinner(8,240,24,1);radius=spinner(.75,18,2.25,.25);taper=slider(0,100,58);bend=slider(0,220,32);lean=spinner(-65,65,0,1);leanDir=spinner(0,359,45,1);
        rootCount=spinner(0,36,7,1);rootLength=slider(10,500,100);rootThickness=slider(10,180,62);rootAngle=spinner(-45,20,-10,1);rootDepth=slider(0,100,25);
        forkCount=spinner(0,12,0,1);forkHeight=slider(10,90,52);boughLength=slider(20,180,68);boughThickness=slider(10,100,44);boughTaper=slider(0,100,72);boughAngle=spinner(-20,80,32,1);
        branches=spinner(1,72,11,1);levels=spinner(1,5,2,1);branchStart=slider(5,90,38);branchLength=slider(20,300,100);branchThickness=slider(10,140,90);branchTaper=slider(0,100,72);branchAngle=spinner(-45,85,23,1);
        branchPattern=new JComboBox<>(BranchPattern.values());crownForm=new JComboBox<>(Preset.OAK.forms());
        compactTracks(height,radius,taper,bend,lean,leanDir,rootCount,rootLength,rootThickness,rootAngle,rootDepth,forkCount,forkHeight,boughLength,boughThickness,boughTaper,boughAngle,branches,levels,branchStart,branchLength,branchThickness,branchTaper,branchAngle);
        p.add(section("ui.treeGenerator.section.trunk",new Object[][]{{"ui.treeGenerator.treeForm",crownForm},{"ui.treeGenerator.trunkHeight",height},{"ui.treeGenerator.trunkThickness",radius},{"ui.treeGenerator.trunkTaper",taper},{"ui.treeGenerator.trunkCurve",bend},{"ui.treeGenerator.trunkLean",lean},{"ui.treeGenerator.trunkDirection",leanDir}}));
        p.add(Box.createVerticalStrut(8));p.add(section("ui.treeGenerator.section.roots",new Object[][]{{"ui.treeGenerator.rootCount",rootCount},{"ui.treeGenerator.rootLength",rootLength},{"ui.treeGenerator.rootThickness",rootThickness},{"ui.treeGenerator.rootAngle",rootAngle},{"ui.treeGenerator.rootDepth",rootDepth}}));
        p.add(Box.createVerticalStrut(8));p.add(section("ui.treeGenerator.section.boughs",new Object[][]{{"ui.treeGenerator.boughCount",forkCount},{"ui.treeGenerator.boughStart",forkHeight},{"ui.treeGenerator.boughLength",boughLength},{"ui.treeGenerator.boughThickness",boughThickness},{"ui.treeGenerator.boughTaper",boughTaper},{"ui.treeGenerator.boughAngle",boughAngle}}));
        p.add(Box.createVerticalStrut(8));p.add(section("ui.treeGenerator.section.branches",new Object[][]{{"ui.treeGenerator.branchPattern",branchPattern},{"ui.treeGenerator.branchCount",branches},{"ui.treeGenerator.branchLevels",levels},{"ui.treeGenerator.branchStart",branchStart},{"ui.treeGenerator.branchLength",branchLength},{"ui.treeGenerator.branchThickness",branchThickness},{"ui.treeGenerator.branchTaper",branchTaper},{"ui.treeGenerator.branchAngle",branchAngle}}));
        return p;
    }

    private JPanel makeFoliagePanel() {
        JPanel p=verticalPanel();leafSize=spinner(1.25,21,3.25,.25);density=slider(5,100,78);leafRough=slider(0,100,45);canopyWidth=slider(20,300,100);canopyHeight=slider(20,300,100);
        wood=new JComboBox<>(Wood.values());trunkBlock=new JComboBox<>(TrunkBlock.values());leaves=new JComboBox<>(Leaves.values());
        compactTracks(leafSize,density,leafRough,canopyWidth,canopyHeight);
        p.add(section("ui.treeGenerator.section.foliage",new Object[][]{{"ui.treeGenerator.canopyWidth",canopyWidth},{"ui.treeGenerator.canopyHeight",canopyHeight},{"ui.treeGenerator.foliageSize",leafSize},{"ui.treeGenerator.foliageDensity",density},{"ui.treeGenerator.foliageRoughness",leafRough}}));
        p.add(Box.createVerticalStrut(8));p.add(section("ui.treeGenerator.section.materials",new Object[][]{{"ui.treeGenerator.wood",wood},{"ui.treeGenerator.trunkBlock",trunkBlock},{"ui.treeGenerator.leaves",leaves}}));return p;
    }

    private JPanel section(String title,Object[][] rows){JPanel p=verticalPanel();p.setBorder(BorderFactory.createTitledBorder(s(title)));addRows(p,rows);return p;}

    private JPanel makeMushroomPanel() {
        JPanel p=verticalPanel();mushroomForm=new JComboBox<>(MushroomForm.values());mushHeight=spinner(5,120,13,1);mushRadius=spinner(.75,15,1.25,.25);mushCurve=slider(0,300,8);
        mushTaper=slider(0,100,25);mushLean=spinner(-80,80,2,1);mushLeanDir=spinner(0,359,40,1);mushFlare=slider(0,300,20);capRadius=spinner(2.5,48,6.25,.25);
        capHeight=slider(10,600,100);capThickness=spinner(1,15,2,1);capEdge=slider(-300,300,0);capBump=slider(0,300,15);capDepression=slider(0,300,0);
        capAsym=slider(0,200,0);capOffset=slider(0,300,0);capWaves=spinner(0,24,0,1);capWaveStrength=slider(0,300,0);capRoughness=slider(0,100,8);
        JPanel stem=verticalPanel(),cap=verticalPanel();stem.setBorder(BorderFactory.createTitledBorder(s("ui.treeGenerator.section.stem")));cap.setBorder(BorderFactory.createTitledBorder(s("ui.treeGenerator.section.cap")));
        addRows(stem,new Object[][]{{"ui.treeGenerator.mushroomForm",mushroomForm},{"ui.treeGenerator.mushHeight",mushHeight},{"ui.treeGenerator.mushRadius",mushRadius},
                {"ui.treeGenerator.mushCurve",mushCurve},{"ui.treeGenerator.taper",mushTaper},{"ui.treeGenerator.lean",mushLean},{"ui.treeGenerator.leanDirection",mushLeanDir},{"ui.treeGenerator.baseFlare",mushFlare}});
        addRows(cap,new Object[][]{{"ui.treeGenerator.capRadius",capRadius},{"ui.treeGenerator.capHeight",capHeight},{"ui.treeGenerator.capThickness",capThickness},
                {"ui.treeGenerator.edgeLift",capEdge},{"ui.treeGenerator.capBump",capBump},{"ui.treeGenerator.capDepression",capDepression},{"ui.treeGenerator.capAsymmetry",capAsym},
                {"ui.treeGenerator.capOffset",capOffset},{"ui.treeGenerator.capWaves",capWaves},{"ui.treeGenerator.waveStrength",capWaveStrength},{"ui.treeGenerator.capRoughness",capRoughness}});
        p.add(stem);p.add(Box.createVerticalStrut(8));p.add(cap);return p;
    }

    private void installListeners() {
        speciesList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) { applyPreset(speciesList.getSelectedValue()); regenerate(); } });
        ChangeListener change = e -> {if(!loading){updateControlAvailability();scheduleRegeneration();}};
        for (NumericControl control:allNumbers()) control.addChangeListener(change);
        for (JComboBox<?> cb : List.of(branchPattern, trunkBlock, wood, leaves)) cb.addActionListener(e -> scheduleRegeneration());
        crownForm.addActionListener(e -> { if (!loading) { applyCrownForm((CrownForm)crownForm.getSelectedItem()); scheduleRegeneration(); } });
        mushroomForm.addActionListener(e -> { if (!loading) { applyMushroomForm((MushroomForm)mushroomForm.getSelectedItem()); scheduleRegeneration(); } });
    }

    private void updateControlAvailability(){
        Preset preset=speciesList.getSelectedValue();if(preset==null)return;boolean palm=preset==Preset.PALM,tree=!preset.mushroom;
        tabs.setEnabledAt(0,tree);tabs.setEnabledAt(1,preset.mushroom);if(tabs.getSelectedIndex()!=(preset.mushroom?1:0))tabs.setSelectedIndex(preset.mushroom?1:0);
        setAvailable(crownForm,tree,null);
        setAvailable(rootCount,tree,null);boolean hasRoots=tree&&i(rootCount)>0;for(JComponent c:List.of(rootLength,rootThickness,rootAngle,rootDepth))setAvailable(c,hasRoots,s("ui.treeGenerator.requiresRoots"));
        setAvailable(forkCount,tree&&!palm,s("ui.treeGenerator.notUsedByPreset"));boolean hasBoughs=tree&&!palm&&i(forkCount)>0;for(JComponent c:List.of(forkHeight,boughLength,boughThickness,boughTaper,boughAngle))setAvailable(c,hasBoughs,s("ui.treeGenerator.requiresBoughs"));
        for(JComponent c:List.of(branchPattern,levels,branchStart))setAvailable(c,tree&&!palm,s("ui.treeGenerator.notUsedByPreset"));
    }
    private static void setAvailable(JComponent component,boolean enabled,String reason){component.setEnabled(enabled);component.setToolTipText(enabled?null:reason);for(Component child:component.getComponents())if(child instanceof JComponent c)setAvailable(c,enabled,reason);}

    private void scheduleRegeneration() {
        if (loading) return;
        if (timer == null) timer = new javax.swing.Timer(120, e -> regenerate());
        timer.setRepeats(false); timer.restart();
    }

    private void applyPreset(Preset p) {
        if(p==null)return;loading=true;Settings d=Settings.defaults(p);crownForm.setModel(new DefaultComboBoxModel<>(p.forms()));
        set(height,d.height);set(radius,d.radius);set(taper,d.taper);set(bend,d.bend);set(lean,Math.toDegrees(d.lean));set(leanDir,Math.toDegrees(d.leanDir));set(crownForm,CrownForm.NATURAL);
        set(rootCount,d.rootCount);set(rootLength,d.rootLength);set(rootThickness,d.rootThickness);set(rootAngle,Math.toDegrees(d.rootAngle));set(rootDepth,d.rootDepth);
        set(forkCount,d.forkCount);set(forkHeight,d.forkHeight);set(boughLength,d.boughLength);set(boughThickness,d.boughThickness);set(boughTaper,d.boughTaper);set(boughAngle,Math.toDegrees(d.boughAngle));
        set(branchPattern,d.pattern);set(branches,d.branches);set(levels,d.levels);set(branchStart,d.branchStart);set(branchLength,d.branchLength);set(branchThickness,d.branchThickness);set(branchTaper,d.branchTaper);set(branchAngle,Math.toDegrees(d.branchAngle));
        set(canopyWidth,d.canopyWidth);set(canopyHeight,d.canopyHeight);set(leafSize,d.leafSize);set(density,d.density);set(leafRough,d.leafRough);set(wood,d.wood);set(trunkBlock,d.trunkBlock);set(leaves,d.leaves);
        set(mushroomForm,MushroomForm.NATURAL);set(mushHeight,d.mushHeight);set(mushRadius,d.mushRadius);set(mushCurve,d.mushCurve);set(mushTaper,d.mushTaper);set(mushLean,Math.toDegrees(d.mushLean));set(mushLeanDir,Math.toDegrees(d.mushLeanDir));set(mushFlare,d.mushFlare);set(capRadius,d.capRadius);set(capHeight,d.capHeight);set(capThickness,d.capThickness);set(capEdge,d.capEdge);set(capBump,d.capBump);set(capDepression,d.capDepression);set(capAsym,d.capAsym);set(capOffset,d.capOffset);set(capWaves,d.capWaves);set(capWaveStrength,d.capWaveStrength);set(capRoughness,d.capRoughness);
        loading=false;updateControlAvailability();
    }

    private void applyCrownForm(CrownForm form) {
        Preset preset=speciesList.getSelectedValue();if(form==null||preset==null||preset.mushroom)return;Settings d=Settings.withCrownForm(preset,form);loading=true;
        set(height,d.height);set(radius,d.radius);set(taper,d.taper);set(bend,d.bend);set(rootCount,d.rootCount);set(rootLength,d.rootLength);set(rootThickness,d.rootThickness);set(rootAngle,Math.toDegrees(d.rootAngle));set(rootDepth,d.rootDepth);set(forkCount,d.forkCount);set(forkHeight,d.forkHeight);set(boughLength,d.boughLength);set(boughThickness,d.boughThickness);set(boughTaper,d.boughTaper);set(boughAngle,Math.toDegrees(d.boughAngle));set(branchPattern,d.pattern);set(branches,d.branches);set(levels,d.levels);set(branchStart,d.branchStart);set(branchLength,d.branchLength);set(branchThickness,d.branchThickness);set(branchTaper,d.branchTaper);set(branchAngle,Math.toDegrees(d.branchAngle));set(canopyWidth,d.canopyWidth);set(canopyHeight,d.canopyHeight);set(leafSize,d.leafSize);set(density,d.density);set(leafRough,d.leafRough);loading=false;updateControlAvailability();
    }

    private void applyMushroomForm(MushroomForm form){
        Preset preset=speciesList.getSelectedValue();if(form==null||preset==null||!preset.mushroom)return;Settings d=Settings.withMushroomForm(preset,form);loading=true;
        set(mushHeight,d.mushHeight);set(mushRadius,d.mushRadius);set(mushCurve,d.mushCurve);set(mushTaper,d.mushTaper);set(mushFlare,d.mushFlare);set(capRadius,d.capRadius);set(capHeight,d.capHeight);set(capThickness,d.capThickness);set(capEdge,d.capEdge);set(capBump,d.capBump);set(capDepression,d.capDepression);set(capAsym,d.capAsym);set(capOffset,d.capOffset);set(capWaves,d.capWaves);set(capWaveStrength,d.capWaveStrength);set(capRoughness,d.capRoughness);
        loading=false;
    }

    private void regenerate() {
        if (timer != null) timer.stop();
        readSettings();
        try {
            object = Generator.generate(settings);
            preview.setObject(object);
            Point3i d = object.getDimensions();
            int woodCount=0, leafCount=0;
            for (Material m : object.blocks.values()) { if (isLeaves(m)) leafCount++; else woodCount++; }
            status.setText(s("ui.treeGenerator.stats") + ": " + object.blocks.size() + "  |  " + woodCount + " / " + leafCount + "  |  " + d.x + "×" + d.y + "×" + d.z);
        } catch (RuntimeException ex) {
            status.setText(ex.getMessage());
        }
    }

    private void readSettings() {
        settings = Settings.defaults(speciesList.getSelectedValue());
        settings.seed=i(seedField);settings.seedVariation=variation.getValue()/100.0;settings.form=(CrownForm)crownForm.getSelectedItem();settings.mushroomForm=(MushroomForm)mushroomForm.getSelectedItem();
        settings.height=i(height);settings.radius=d(radius);settings.taper=taper.getValue()/100.0;settings.bend=bend.getValue()/100.0;settings.lean=Math.toRadians(d(lean));settings.leanDir=Math.toRadians(d(leanDir));
        settings.rootCount=i(rootCount);settings.rootLength=rootLength.getValue()/100.0;settings.rootThickness=rootThickness.getValue()/100.0;settings.rootAngle=Math.toRadians(d(rootAngle));settings.rootDepth=rootDepth.getValue()/100.0;
        settings.forkCount=i(forkCount);settings.forkHeight=forkHeight.getValue()/100.0;settings.boughLength=boughLength.getValue()/100.0;settings.boughThickness=boughThickness.getValue()/100.0;settings.boughTaper=boughTaper.getValue()/100.0;settings.boughAngle=Math.toRadians(d(boughAngle));
        settings.pattern=(BranchPattern)branchPattern.getSelectedItem();settings.branches=i(branches);settings.levels=i(levels);settings.branchStart=branchStart.getValue()/100.0;settings.branchLength=branchLength.getValue()/100.0;settings.branchThickness=branchThickness.getValue()/100.0;settings.branchTaper=branchTaper.getValue()/100.0;settings.branchAngle=Math.toRadians(d(branchAngle));
        settings.canopyWidth=canopyWidth.getValue()/100.0;settings.canopyHeight=canopyHeight.getValue()/100.0;settings.leafSize=d(leafSize);settings.density=density.getValue()/100.0;settings.leafRough=leafRough.getValue()/100.0;settings.wood=(Wood)wood.getSelectedItem();settings.trunkBlock=(TrunkBlock)trunkBlock.getSelectedItem();settings.leaves=(Leaves)leaves.getSelectedItem();
        settings.mushHeight=i(mushHeight); settings.mushRadius=d(mushRadius); settings.mushCurve=mushCurve.getValue()/100.0; settings.mushTaper=mushTaper.getValue()/100.0;
        settings.mushLean=Math.toRadians(d(mushLean)); settings.mushLeanDir=Math.toRadians(d(mushLeanDir)); settings.mushFlare=mushFlare.getValue()/100.0;
        settings.capRadius=d(capRadius); settings.capHeight=capHeight.getValue()/100.0; settings.capThickness=i(capThickness); settings.capEdge=capEdge.getValue()/100.0;
        settings.capBump=capBump.getValue()/100.0; settings.capDepression=capDepression.getValue()/100.0; settings.capAsym=capAsym.getValue()/100.0;
        settings.capOffset=capOffset.getValue()/100.0; settings.capWaves=i(capWaves); settings.capWaveStrength=capWaveStrength.getValue()/100.0; settings.capRoughness=capRoughness.getValue()/100.0;
    }

    private void exportSchem() {
        if (object == null) return;
        File file = FileUtils.selectFileForSave(this, s("ui.treeGenerator.exportSchem"), new File("tree-" + settings.preset.id + "-" + settings.seed + ".schem"), new FileFilter() {
            public boolean accept(File f){return f.isDirectory()||f.getName().toLowerCase().endsWith(".schem");}
            public String getDescription(){return "Sponge schematic (*.schem)";}
            public String getExtensions(){return "schem";}
        });
        if (file == null) return;
        if (!file.getName().toLowerCase().endsWith(".schem")) file = new File(file.getParentFile(), file.getName()+".schem");
        try { SchemWriter.write(object, file); JOptionPane.showMessageDialog(this, s("ui.treeGenerator.exported")); }
        catch (IOException ex) { JOptionPane.showMessageDialog(this, ex.toString(), s("ui.treeGenerator.error"), JOptionPane.ERROR_MESSAGE); }
    }

    private static JPanel verticalPanel(){JPanel p=new JPanel();p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(new EmptyBorder(6,6,6,6));return p;}
    private static void addRows(JPanel p,Object[][] rows){for(Object[] r:rows)p.add(labelled(s((String)r[0]),(JComponent)r[1]));p.add(Box.createVerticalGlue());}
    private static JPanel labelled(String text,JComponent c){JPanel p=new JPanel(new BorderLayout(6,2));p.setBorder(new EmptyBorder(3,0,3,0));p.add(new JLabel(text),BorderLayout.NORTH);p.add(c,BorderLayout.CENTER);p.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,p.getPreferredSize().height));return p;}
    private static NumericControl slider(int min,int max,int value){return new NumericControl(min,max,value,1,true);}
    private static void compactTracks(NumericControl... controls){for(NumericControl control:controls)control.setTrackWidth(210);}
    private static NumericControl spinner(double min,double max,double value,double step){return new NumericControl(min,max,value,step,false);}
    private static int i(NumericControl c){return (int)Math.round(c.getValue());}private static double d(NumericControl c){return c.getValue();}
    private static void set(NumericControl c,double value){c.setValue(c.percent?value*100:value);}private static void set(JComboBox<?> box,Object value){box.setSelectedItem(value);}
    private List<NumericControl> allNumbers(){return List.of(seedField,variation,height,radius,taper,bend,lean,leanDir,rootCount,rootLength,rootThickness,rootAngle,rootDepth,forkCount,forkHeight,boughLength,boughThickness,boughTaper,boughAngle,branches,levels,branchStart,branchLength,branchThickness,branchTaper,branchAngle,canopyWidth,canopyHeight,leafSize,density,leafRough,mushHeight,mushRadius,mushCurve,mushTaper,mushLean,mushLeanDir,mushFlare,capRadius,capHeight,capThickness,capEdge,capBump,capDepression,capAsym,capOffset,capWaves,capWaveStrength,capRoughness);}

    static final class NumericControl extends JPanel{
        private final double min,max,step;private final boolean integral,percent;private final JSlider slider;private final JTextField field=new JTextField(7);private final List<ChangeListener> listeners=new ArrayList<>();private boolean adjusting;private double value;
        NumericControl(double min,double max,double value,double step,boolean percent){super(new BorderLayout(6,0));this.min=min;this.max=max;this.step=step;this.percent=percent;integral=Math.abs(step-Math.rint(step))<1e-9;int positions=(int)Math.max(1,Math.min(4000,Math.ceil((max-min)/step)));slider=new JSlider(0,positions);slider.setPaintTicks(false);field.setHorizontalAlignment(JTextField.RIGHT);field.setToolTipText("Type a value and press Enter");add(slider,BorderLayout.CENTER);add(field,BorderLayout.EAST);slider.addChangeListener(e->{if(adjusting)return;double raw=min+(max-min)*slider.getValue()/slider.getMaximum();setValueInternal(snap(raw),true,false);});field.addActionListener(e->commitText());field.addFocusListener(new FocusAdapter(){@Override public void focusLost(FocusEvent e){commitText();}});field.addMouseWheelListener(e->{setValue(value-e.getPreciseWheelRotation()*step);});setValue(value);setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE,getPreferredSize().height));}
        double getValue(){return value;}void setValue(double value){setValueInternal(value,false,true);}void addChangeListener(ChangeListener listener){listeners.add(listener);}
        void setTrackWidth(int width){java.awt.Dimension size=slider.getPreferredSize();java.awt.Dimension compact=new java.awt.Dimension(width,size.height);slider.setMinimumSize(compact);slider.setPreferredSize(compact);slider.setMaximumSize(compact);remove(slider);JPanel holder=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));holder.setOpaque(false);holder.add(slider);add(holder,BorderLayout.CENTER);}
        private double snap(double value){if(!Double.isFinite(value))return this.value;double v=Math.max(min,Math.min(max,value));return Math.max(min,Math.min(max,Math.round((v-min)/step)*step+min));}
        private void setValueInternal(double next,boolean fire,boolean syncSlider){next=snap(next);if(Math.abs(next-value)<1e-9&&!field.getText().isEmpty())return;value=next;adjusting=true;if(syncSlider)slider.setValue((int)Math.round((value-min)/(max-min)*slider.getMaximum()));field.setText(format(value));adjusting=false;if(fire){ChangeEvent event=new ChangeEvent(this);for(ChangeListener listener:List.copyOf(listeners))listener.stateChanged(event);}}
        private void commitText(){try{String text=field.getText().trim().replace(',','.');setValueInternal(Double.parseDouble(text),true,true);}catch(NumberFormatException ex){field.setText(format(value));Toolkit.getDefaultToolkit().beep();}}
        private String format(double value){if(integral)return Long.toString(Math.round(value));String text=String.format(Locale.ROOT,"%.4f",value);while(text.contains(".")&&(text.endsWith("0")||text.endsWith(".")))text=text.substring(0,text.length()-1);return text;}
    }

    private static boolean isLeaves(Material m){return m.leafBlock||m.name.endsWith("_wart_block")||m.name.endsWith("mushroom_block")||m.name.endsWith("shroomlight");}
    private static boolean isTransparentFoliage(Material m){return m.leafBlock||m.name.endsWith("_leaves")||m.name.endsWith("_wart_block");}

    enum BranchPattern { GOLDEN, WHORLED, OPPOSITE, RANDOM; @Override public String toString(){return s("ui.treeGenerator.pattern."+name().toLowerCase());} }
    enum CrownForm { NATURAL,ROUND,OVAL,VASE,SPREADING,COLUMNAR,CONICAL,LAYERED,UMBRELLA,OPEN,MULTI_STEM,WEEPING,WINDSWEPT,ANCIENT,FANCY; @Override public String toString(){return s("ui.treeGenerator.form."+name().toLowerCase());} }
    enum MushroomForm { NATURAL,DOME,BELL,UMBRELLA,FLAT,FUNNEL,WAVY; @Override public String toString(){return s("ui.treeGenerator.mushroomForm."+name().toLowerCase());} }
    enum TrunkBlock { LOG,WOOD; @Override public String toString(){return s("ui.treeGenerator.trunk."+name().toLowerCase());} }
    enum Wood { OAK,BIRCH,SPRUCE,JUNGLE,ACACIA,DARK_OAK,MANGROVE,CHERRY,PALE_OAK,POPLAR; @Override public String toString(){return name().toLowerCase().replace('_',' ');} }
    enum Leaves { OAK,BIRCH,SPRUCE,JUNGLE,ACACIA,DARK_OAK,MANGROVE,CHERRY,PALE_OAK,AZALEA,RED_POPLAR,ORANGE_POPLAR,YELLOW_POPLAR; @Override public String toString(){return name().toLowerCase().replace('_',' ');} }
    enum Preset {
        OAK("oak",false),BIRCH("birch",false),PINE("pine",false),JUNGLE("jungle",false),ACACIA("acacia",false),DARK_OAK("darkoak",false),MANGROVE("mangrove",false),CHERRY("cherry",false),PALE_OAK("paleoak",false),AZALEA("azalea",false),POPLAR_RED("poplar_red",false),POPLAR_ORANGE("poplar_orange",false),POPLAR_YELLOW("poplar_yellow",false),PALM("palm",false),MUSHROOM_RED("mushroom_red",true),MUSHROOM_BROWN("mushroom_brown",true);
        final String id; final boolean mushroom; Preset(String id,boolean mushroom){this.id=id;this.mushroom=mushroom;} String displayName(){return s("ui.treeGenerator.preset."+id);}
        CrownForm[] forms(){return switch(this){
            case OAK -> new CrownForm[]{CrownForm.NATURAL,CrownForm.ROUND,CrownForm.SPREADING,CrownForm.OPEN,CrownForm.ANCIENT,CrownForm.FANCY};
            case BIRCH -> new CrownForm[]{CrownForm.NATURAL,CrownForm.OVAL,CrownForm.COLUMNAR,CrownForm.MULTI_STEM,CrownForm.WEEPING,CrownForm.WINDSWEPT};
            case PINE -> new CrownForm[]{CrownForm.NATURAL,CrownForm.CONICAL,CrownForm.LAYERED,CrownForm.COLUMNAR,CrownForm.UMBRELLA,CrownForm.WINDSWEPT};
            case JUNGLE -> new CrownForm[]{CrownForm.NATURAL,CrownForm.UMBRELLA,CrownForm.LAYERED,CrownForm.COLUMNAR,CrownForm.MULTI_STEM,CrownForm.ANCIENT};
            case ACACIA -> new CrownForm[]{CrownForm.NATURAL,CrownForm.UMBRELLA,CrownForm.SPREADING,CrownForm.OPEN,CrownForm.MULTI_STEM,CrownForm.WINDSWEPT};
            case DARK_OAK,PALE_OAK -> new CrownForm[]{CrownForm.NATURAL,CrownForm.ROUND,CrownForm.SPREADING,CrownForm.OPEN,CrownForm.ANCIENT,CrownForm.WINDSWEPT};
            case MANGROVE -> new CrownForm[]{CrownForm.NATURAL,CrownForm.SPREADING,CrownForm.MULTI_STEM,CrownForm.OPEN,CrownForm.WINDSWEPT,CrownForm.ANCIENT};
            case CHERRY -> new CrownForm[]{CrownForm.NATURAL,CrownForm.VASE,CrownForm.SPREADING,CrownForm.UMBRELLA,CrownForm.WEEPING,CrownForm.COLUMNAR};
            case AZALEA -> new CrownForm[]{CrownForm.NATURAL,CrownForm.ROUND,CrownForm.SPREADING,CrownForm.MULTI_STEM,CrownForm.OPEN,CrownForm.FANCY};
            case POPLAR_RED,POPLAR_ORANGE,POPLAR_YELLOW -> new CrownForm[]{CrownForm.NATURAL,CrownForm.COLUMNAR,CrownForm.OVAL,CrownForm.SPREADING,CrownForm.OPEN,CrownForm.WINDSWEPT};
            case PALM -> new CrownForm[]{CrownForm.NATURAL,CrownForm.SPREADING,CrownForm.COLUMNAR,CrownForm.OPEN,CrownForm.WINDSWEPT,CrownForm.ANCIENT};
            case MUSHROOM_RED,MUSHROOM_BROWN -> new CrownForm[]{CrownForm.NATURAL};
        };}
    }

    static final class Settings {
        Preset preset; int seed=42817,height,branches,levels,stemCount=1,forkCount=0,rootCount=7,mushHeight=13,capThickness=2,capWaves;
        double seedVariation=.72,radius,bend,spread,leafSize,density,roots,taper=.5,branchStart=.38,branchLength=1,branchLift,branchDroop,crownShape=1,leafRough=.45,rootLength=1,lean,leanDir=Math.toRadians(45),forkHeight=.55,asymmetry=.2;
        double rootThickness=.62,rootAngle=Math.toRadians(-10),rootDepth=.25,boughLength=.68,boughThickness=.44,boughTaper=.72,boughAngle=Math.toRadians(32),branchThickness=.90,branchTaper=.72,branchAngle=Math.toRadians(23),canopyWidth=1,canopyHeight=1;
        double mushRadius=1.25,mushCurve=.08,mushTaper=.25,mushLean=Math.toRadians(2),mushLeanDir=Math.toRadians(40),mushFlare=.2,capRadius=6.25,capHeight=1,capEdge,capBump=.15,capDepression,capAsym,capOffset,capWaveStrength,capRoughness=.08;
        CrownForm form=CrownForm.NATURAL; MushroomForm mushroomForm=MushroomForm.NATURAL; BranchPattern pattern=BranchPattern.GOLDEN; Wood wood=Wood.OAK; TrunkBlock trunkBlock=TrunkBlock.LOG; Leaves leaves=Leaves.OAK;
        void deriveControls(){rootThickness=Math.max(.10,Math.min(1.8,.30+roots*.55));rootAngle=Math.toRadians(-10);rootDepth=.25;boughLength=.68;boughThickness=.44;boughTaper=.72;boughAngle=Math.toRadians(32);branchThickness=.90;branchTaper=.72;branchAngle=Math.atan(Math.max(-.85,Math.min(6,.42+branchLift)));canopyWidth=Math.max(.2,spread);canopyHeight=Math.max(.2,crownShape);}
        static Settings withCrownForm(Preset p,CrownForm form){Settings s=defaults(p);s.form=form;switch(form){
            case NATURAL -> {}
            case ROUND -> {s.spread=.92;s.branches=15;s.levels=3;s.branchStart=.30;s.branchLift=.14;s.branchDroop=-.08;s.crownShape=.92;s.asymmetry=.14;s.pattern=BranchPattern.GOLDEN;}
            case OVAL -> {s.spread=.72;s.branches=15;s.levels=3;s.branchStart=.28;s.branchLift=.28;s.branchDroop=-.05;s.crownShape=1.25;s.asymmetry=.12;s.pattern=BranchPattern.GOLDEN;}
            case VASE -> {s.spread=1.08;s.branches=11;s.levels=3;s.branchStart=.30;s.branchLift=.72;s.branchDroop=.02;s.crownShape=.90;s.forkCount=2;s.forkHeight=.42;s.asymmetry=.18;s.pattern=BranchPattern.GOLDEN;}
            case SPREADING -> {s.spread=1.36;s.branches=13;s.levels=3;s.branchStart=.26;s.branchLift=.02;s.branchDroop=-.16;s.crownShape=.58;s.asymmetry=.24;s.pattern=BranchPattern.GOLDEN;}
            case COLUMNAR -> {s.spread=.42;s.branches=16;s.levels=2;s.branchStart=.24;s.branchLength=.78;s.branchLift=.42;s.branchDroop=-.02;s.crownShape=1.42;s.asymmetry=.08;s.pattern=BranchPattern.GOLDEN;}
            case CONICAL -> {s.spread=.64;s.branches=20;s.levels=2;s.branchStart=.17;s.branchLift=.16;s.branchDroop=-.08;s.crownShape=1.15;s.asymmetry=.07;s.pattern=BranchPattern.WHORLED;}
            case LAYERED -> {s.spread=.88;s.branches=20;s.levels=2;s.branchStart=.20;s.branchLift=.02;s.branchDroop=-.12;s.crownShape=.72;s.density=.68;s.leafRough=.32;s.asymmetry=.10;s.pattern=BranchPattern.WHORLED;}
            case UMBRELLA -> {s.spread=1.34;s.branches=9;s.levels=2;s.branchStart=.48;s.branchLift=.34;s.branchDroop=-.12;s.crownShape=.48;s.asymmetry=.20;s.pattern=BranchPattern.GOLDEN;}
            case OPEN -> {s.spread=1.10;s.branches=8;s.levels=2;s.branchStart=.34;s.branchLift=.18;s.branchDroop=-.10;s.crownShape=.82;s.density=.54;s.leafRough=.68;s.asymmetry=.38;s.pattern=BranchPattern.RANDOM;}
            case MULTI_STEM -> {s.stemCount=3;s.forkCount=0;s.spread=.88;s.branches=11;s.levels=2;s.branchStart=.38;s.branchLift=.24;s.crownShape=1.02;s.asymmetry=.24;s.radius=Math.max(.75,s.radius*.78);s.pattern=BranchPattern.GOLDEN;}
            case WEEPING -> {s.spread=1.02;s.branches=15;s.levels=3;s.branchStart=.40;s.branchLift=.12;s.branchDroop=-.78;s.crownShape=1.05;s.asymmetry=.22;s.pattern=BranchPattern.GOLDEN;}
            case WINDSWEPT -> {s.spread=1.08;s.branches=10;s.levels=2;s.branchStart=.32;s.branchLift=.08;s.branchDroop=-.18;s.crownShape=.80;s.asymmetry=.86;s.bend=.70;s.lean=Math.toRadians(18);s.pattern=BranchPattern.RANDOM;}
            case ANCIENT -> {s.height=Math.max(10,(int)Math.round(s.height*.88));s.radius=Math.min(6,s.radius*1.5);s.spread=Math.min(1.6,s.spread*1.34);s.branches=17;s.levels=3;s.forkCount=2;s.forkHeight=.40;s.roots=.96;s.rootCount=11;s.rootLength=1.25;s.crownShape=.84;s.asymmetry=.50;s.pattern=BranchPattern.RANDOM;}
            case FANCY -> {s.spread=1.12;s.branches=16;s.levels=3;s.forkCount=2;s.forkHeight=.48;s.branchLift=.22;s.branchDroop=-.14;s.crownShape=1.00;s.asymmetry=.30;s.pattern=BranchPattern.GOLDEN;}
        }tuneSpeciesForm(s,p,form);s.deriveControls();return s;}
        private static void tuneSpeciesForm(Settings s,Preset p,CrownForm form){
            switch(p){
                case OAK -> {switch(form){case ROUND->{s.height=22;s.radius=2.45;s.spread=1.00;s.crownShape=.92;s.branches=16;}case SPREADING->{s.height=20;s.radius=2.65;s.spread=1.42;s.branchStart=.31;s.crownShape=.58;s.branches=14;}case OPEN->{s.height=23;s.spread=1.16;s.branches=9;s.levels=2;s.density=.54;s.asymmetry=.42;}case ANCIENT->{s.height=21;s.radius=3.7;s.spread=1.52;s.forkCount=3;s.rootCount=12;s.asymmetry=.58;}case FANCY->{s.height=28;s.radius=2.35;s.forkCount=3;s.forkHeight=.44;s.branches=17;}default->{}}}
                case BIRCH -> {switch(form){case OVAL->{s.height=31;s.radius=1.25;s.spread=.68;s.crownShape=1.32;s.branchStart=.42;}case COLUMNAR->{s.height=35;s.radius=1.15;s.spread=.38;s.branches=18;s.crownShape=1.55;}case MULTI_STEM->{s.height=24;s.radius=1.05;s.stemCount=4;s.spread=.82;s.branchStart=.34;}case WEEPING->{s.height=28;s.radius=1.30;s.spread=.92;s.branchDroop=-.96;s.crownShape=1.18;}case WINDSWEPT->{s.height=26;s.radius=1.25;s.spread=.82;s.asymmetry=.92;s.lean=Math.toRadians(14);}default->{}}}
                case PINE -> {switch(form){case CONICAL->{s.height=36;s.radius=1.85;s.spread=.58;s.branches=23;s.branchStart=.13;s.crownShape=1.28;}case LAYERED->{s.height=34;s.radius=1.9;s.spread=.78;s.branches=24;s.density=.64;s.crownShape=.78;}case COLUMNAR->{s.height=38;s.radius=1.65;s.spread=.36;s.branches=22;s.crownShape=1.55;}case UMBRELLA->{s.height=27;s.radius=2.05;s.spread=1.28;s.branchStart=.60;s.branches=11;s.crownShape=.42;}case WINDSWEPT->{s.height=29;s.radius=1.75;s.spread=.82;s.branches=15;s.asymmetry=.94;s.lean=Math.toRadians(17);}default->{}}}
                case JUNGLE -> {switch(form){case UMBRELLA->{s.height=43;s.radius=3.5;s.spread=1.32;s.branchStart=.58;s.branches=11;s.crownShape=.48;}case LAYERED->{s.height=39;s.radius=3.3;s.spread=1.02;s.branches=22;s.crownShape=.74;}case COLUMNAR->{s.height=46;s.radius=2.8;s.spread=.52;s.branches=16;s.crownShape=1.42;}case MULTI_STEM->{s.height=31;s.radius=2.25;s.stemCount=3;s.spread=1.08;s.rootCount=10;}case ANCIENT->{s.height=40;s.radius=5.2;s.spread=1.48;s.rootCount=14;s.forkCount=3;s.asymmetry=.55;}default->{}}}
                case ACACIA -> {switch(form){case UMBRELLA->{s.height=19;s.radius=1.8;s.spread=1.48;s.branchStart=.52;s.branches=9;s.crownShape=.38;}case SPREADING->{s.height=17;s.radius=1.9;s.spread=1.55;s.branches=12;s.crownShape=.50;}case OPEN->{s.height=20;s.radius=1.65;s.spread=1.28;s.branches=7;s.density=.48;s.asymmetry=.50;}case MULTI_STEM->{s.height=15;s.radius=1.25;s.stemCount=4;s.spread=1.18;}case WINDSWEPT->{s.height=18;s.radius=1.7;s.spread=1.30;s.asymmetry=.98;s.lean=Math.toRadians(20);}default->{}}}
                case DARK_OAK,PALE_OAK -> {switch(form){case ROUND->{s.height=20;s.radius=3.45;s.spread=1.06;s.branches=17;s.crownShape=.88;}case SPREADING->{s.height=18;s.radius=3.7;s.spread=1.42;s.crownShape=.58;s.branches=15;}case OPEN->{s.height=21;s.radius=3.0;s.spread=1.16;s.branches=10;s.density=.57;s.asymmetry=.45;}case ANCIENT->{s.height=18;s.radius=5.0;s.spread=1.52;s.rootCount=12;s.forkCount=3;s.asymmetry=.62;}case WINDSWEPT->{s.height=19;s.radius=3.2;s.spread=1.20;s.asymmetry=.95;s.lean=Math.toRadians(17);}default->{}}}
                case MANGROVE -> {switch(form){case SPREADING->{s.height=18;s.radius=2.3;s.spread=1.48;s.crownShape=.50;s.rootCount=10;}case MULTI_STEM->{s.height=17;s.radius=1.65;s.stemCount=4;s.spread=1.28;s.rootCount=12;}case OPEN->{s.height=20;s.radius=2.0;s.spread=1.22;s.branches=8;s.density=.52;s.rootCount=8;}case WINDSWEPT->{s.height=19;s.radius=2.1;s.spread=1.30;s.asymmetry=.94;s.lean=Math.toRadians(16);s.rootCount=10;}case ANCIENT->{s.height=23;s.radius=3.5;s.spread=1.46;s.rootCount=16;s.rootLength=1.36;s.branches=17;}default->{}}}
                case CHERRY -> {switch(form){case VASE->{s.height=22;s.radius=1.9;s.spread=1.08;s.forkCount=3;s.forkHeight=.38;s.branchLift=.78;}case SPREADING->{s.height=20;s.radius=2.0;s.spread=1.40;s.crownShape=.52;s.branches=13;}case UMBRELLA->{s.height=19;s.radius=2.05;s.spread=1.42;s.branchStart=.52;s.crownShape=.42;}case WEEPING->{s.height=21;s.radius=1.9;s.spread=1.22;s.branchDroop=-1.02;s.crownShape=1.18;}case COLUMNAR->{s.height=26;s.radius=1.65;s.spread=.48;s.branches=16;s.crownShape=1.45;}default->{}}}
                case AZALEA -> {switch(form){case ROUND->{s.height=12;s.radius=1.55;s.spread=.98;s.branches=14;s.crownShape=.88;}case SPREADING->{s.height=10;s.radius=1.45;s.spread=1.34;s.crownShape=.54;}case MULTI_STEM->{s.height=11;s.radius=1.0;s.stemCount=5;s.spread=1.06;}case OPEN->{s.height=13;s.radius=1.35;s.spread=1.12;s.branches=7;s.density=.56;}case FANCY->{s.height=14;s.radius=1.45;s.forkCount=3;s.spread=1.18;s.branches=15;}default->{}}}
                case POPLAR_RED,POPLAR_ORANGE,POPLAR_YELLOW -> {switch(form){case COLUMNAR->{s.height=36;s.radius=1.8;s.spread=.34;s.branches=19;s.crownShape=1.62;}case OVAL->{s.height=33;s.radius=2.0;s.spread=.66;s.branches=16;s.crownShape=1.28;}case SPREADING->{s.height=29;s.radius=2.3;s.spread=1.12;s.branches=14;s.crownShape=.72;}case OPEN->{s.height=32;s.radius=2.05;s.spread=.92;s.branches=9;s.density=.52;s.asymmetry=.42;}case WINDSWEPT->{s.height=30;s.radius=2.0;s.spread=.82;s.asymmetry=.90;s.lean=Math.toRadians(14);}default->{}}}
                case PALM -> {switch(form){case SPREADING->{s.height=27;s.radius=1.65;s.branches=18;s.spread=1.42;s.crownShape=.58;}case COLUMNAR->{s.height=36;s.radius=1.45;s.branches=11;s.spread=.74;s.crownShape=1.25;s.bend=.35;}case OPEN->{s.height=30;s.radius=1.55;s.branches=9;s.spread=1.25;s.density=.62;}case WINDSWEPT->{s.height=28;s.radius=1.55;s.branches=13;s.spread=1.30;s.asymmetry=.96;s.lean=Math.toRadians(18);s.bend=.92;}case ANCIENT->{s.height=34;s.radius=2.25;s.branches=20;s.spread=1.38;s.rootCount=7;s.bend=.78;}default->{}}}
                case MUSHROOM_RED,MUSHROOM_BROWN -> {}
            }
        }
        static Settings withMushroomForm(Preset p,MushroomForm form){Settings s=defaults(p);s.mushroomForm=form;switch(form){
            case NATURAL -> {}
            case DOME -> {s.capHeight=1.10;s.capThickness=2;s.capEdge=-.08;s.capBump=.12;s.capDepression=0;s.capWaves=0;}
            case BELL -> {s.capHeight=1.42;s.capThickness=2;s.capEdge=-.30;s.capBump=.18;s.capDepression=0;s.capWaves=0;s.mushTaper=.35;}
            case UMBRELLA -> {s.capHeight=.62;s.capThickness=2;s.capEdge=-.18;s.capBump=.16;s.capDepression=0;s.capWaves=2;s.capWaveStrength=.10;}
            case FLAT -> {s.capHeight=.34;s.capThickness=1;s.capEdge=-.04;s.capBump=.05;s.capDepression=0;s.capWaves=1;}
            case FUNNEL -> {s.capHeight=.48;s.capThickness=2;s.capEdge=.28;s.capBump=0;s.capDepression=.72;s.capWaves=3;s.capWaveStrength=.12;}
            case WAVY -> {s.capHeight=.48;s.capThickness=2;s.capEdge=.02;s.capBump=.08;s.capDepression=.10;s.capWaves=6;s.capWaveStrength=.48;s.capRoughness=.24;s.capAsym=.12;}
        }return s;}
        static Settings defaults(Preset p){Settings s=new Settings();s.preset=p;
            switch(p){
                case OAK->{s.height=24;s.radius=2.25;s.bend=.32;s.branches=11;s.spread=.82;s.levels=2;s.leafSize=3.25;s.density=.78;s.roots=.58;}
                case BIRCH->{s.height=27;s.radius=1.35;s.bend=.18;s.branches=10;s.spread=.55;s.levels=2;s.leafSize=2.3;s.density=.67;s.roots=.25;s.wood=Wood.BIRCH;s.leaves=Leaves.BIRCH;}
                case PINE->{s.height=32;s.radius=1.75;s.bend=.12;s.branches=18;s.spread=.46;s.levels=2;s.leafSize=2.1;s.density=.83;s.roots=.35;s.wood=Wood.SPRUCE;s.leaves=Leaves.SPRUCE;s.pattern=BranchPattern.WHORLED;}
                case JUNGLE->{s.height=36;s.radius=3.25;s.bend=.24;s.branches=12;s.spread=.86;s.levels=3;s.leafSize=3.4;s.density=.81;s.roots=.76;s.wood=Wood.JUNGLE;s.leaves=Leaves.JUNGLE;}
                case ACACIA->{s.height=18;s.radius=1.75;s.bend=.52;s.branches=7;s.spread=1.18;s.levels=2;s.leafSize=2.5;s.density=.65;s.roots=.48;s.wood=Wood.ACACIA;s.leaves=Leaves.ACACIA;}
                case DARK_OAK->{s.height=21;s.radius=3.25;s.bend=.38;s.branches=14;s.spread=1;s.levels=3;s.leafSize=3.6;s.density=.88;s.roots=.72;s.wood=Wood.DARK_OAK;s.leaves=Leaves.DARK_OAK;}
                case MANGROVE->{s.height=21;s.radius=2.15;s.bend=.48;s.branches=13;s.spread=1.18;s.levels=2;s.branchStart=.44;s.branchLength=1.06;s.branchLift=.18;s.branchDroop=-.12;s.leafSize=3.2;s.density=.86;s.roots=1;s.rootCount=8;s.rootLength=1.18;s.taper=.42;s.crownShape=.68;s.asymmetry=.28;s.wood=Wood.MANGROVE;s.leaves=Leaves.MANGROVE;}
                case CHERRY->{s.height=22;s.radius=2;s.bend=.36;s.branches=12;s.spread=1.05;s.levels=3;s.leafSize=3;s.density=.85;s.roots=.42;s.wood=Wood.CHERRY;s.leaves=Leaves.CHERRY;}
                case PALE_OAK->{s.height=20;s.radius=3;s.bend=.46;s.branches=13;s.spread=.98;s.levels=3;s.leafSize=3.5;s.density=.84;s.roots=.68;s.wood=Wood.PALE_OAK;s.leaves=Leaves.PALE_OAK;}
                case AZALEA->{s.height=13;s.radius=1.5;s.bend=.55;s.branches=9;s.spread=.92;s.levels=2;s.leafSize=2.65;s.density=.88;s.roots=.48;s.leaves=Leaves.AZALEA;s.leafRough=.68;}
                case POPLAR_RED,POPLAR_ORANGE,POPLAR_YELLOW->{s.height=30;s.radius=2;s.bend=.25;s.branches=13;s.spread=.67;s.levels=2;s.leafSize=2.7;s.density=.76;s.roots=.4;s.wood=Wood.POPLAR;s.leaves=p==Preset.POPLAR_RED?Leaves.RED_POPLAR:p==Preset.POPLAR_ORANGE?Leaves.ORANGE_POPLAR:Leaves.YELLOW_POPLAR;}
                case PALM->{s.height=29;s.radius=1.6;s.bend=.72;s.branches=15;s.spread=1.18;s.levels=1;s.branchStart=.82;s.branchLength=1.08;s.branchLift=.18;s.branchDroop=-.62;s.leafSize=3.2;s.density=.88;s.roots=.22;s.rootCount=4;s.rootLength=.72;s.taper=.62;s.asymmetry=.18;s.wood=Wood.JUNGLE;s.leaves=Leaves.JUNGLE;}
                case MUSHROOM_RED->{s.height=13;s.radius=1.3;s.bend=.07;s.branches=3;s.spread=.7;s.levels=1;s.leafSize=4;s.density=1;s.roots=0;s.mushHeight=13;s.mushRadius=1.35;s.mushTaper=.18;s.mushFlare=.34;s.capRadius=6.5;s.capHeight=1.05;s.capThickness=2;s.capEdge=-.08;s.capBump=.12;s.capRoughness=.08;}
                case MUSHROOM_BROWN->{s.height=10;s.radius=1.15;s.bend=.06;s.branches=3;s.spread=.95;s.levels=1;s.leafSize=4.5;s.density=1;s.roots=0;s.mushHeight=10;s.mushRadius=1.15;s.mushTaper=.28;s.mushFlare=.22;s.capRadius=8.25;s.capHeight=.34;s.capThickness=1;s.capEdge=-.04;s.capBump=.04;s.capAsym=.08;s.capWaves=2;s.capWaveStrength=.12;s.capRoughness=.12;}
            }s.deriveControls();return s;}
    }

    static final class Generator {
        static GeneratedObject generate(Settings s){Builder b=new Builder(s);Rand r=new Rand(s.seed),v=new Rand(s.seed^0x9e3779b9);if(s.preset.mushroom)return mushroom(s,b,r,v);if(s.preset==Preset.PALM)return palm(s,b,r,v);if(s.preset==Preset.MANGROVE)return mangrove(s,b,r,v);return tree(s,b,r,v);}
        private static GeneratedObject tree(Settings s,Builder b,Rand r,Rand v){double sv=s.seedVariation;int h=Math.max(7,(int)Math.round(s.height*(1+j(v,.19,sv))));double rad=Math.max(.7,s.radius*(1+j(v,.13,sv)));double bend=Math.max(0,s.bend*(1+j(v,.65,sv))+Math.abs(j(v,.1,sv)));int bc=Math.max(3,(int)Math.round(s.branches*(1+j(v,.3,sv))));double spread=Math.max(.22,s.canopyWidth*(1+j(v,.12,sv))),leaf=Math.min(Math.max(1.25,s.leafSize*(1+j(v,.14,sv))),Math.max(3.5,h*.16));double start=clamp(s.branchStart+j(v,.07,sv),.08,.88),bl=Math.max(.20,s.branchLength*(1+j(v,.16,sv))),lift=Math.tan(s.branchAngle),droop=s.branchDroop+j(v,.18,sv),crown=Math.max(.2,s.canopyHeight*(1+j(v,.14,sv))),rootLen=Math.max(.1,s.rootLength*(1+j(v,.14,sv))),lean=s.lean+j(v,.2,sv),leanDir=s.leanDir+j(v,Math.PI,sv),asym=clamp(s.asymmetry+Math.abs(j(v,.5,sv)),0,1),rough=clamp(s.leafRough+j(v,.22,sv),0,1);int rootN=Math.max(0,(int)Math.round(s.rootCount+j(v,2.5,sv))),forks=s.forkCount,stems=s.stemCount;if(forks>0&&sv>.45&&v.next()<.18)forks=Math.min(3,forks+1);if(sv>.7&&v.next()<.12)stems=Math.min(4,stems+1);
            List<Vec> trunk=new ArrayList<>();Vec p=new Vec(0,0,0);trunk.add(p);double dx=(r.next()-.5)*bend,dz=(r.next()-.5)*bend,lstep=Math.tan(lean),lx=Math.cos(leanDir)*lstep,lz=Math.sin(leanDir)*lstep;for(int y=1;y<=h;y++){dx=(dx+(r.next()-.5)*bend*.10)*.92;dz=(dz+(r.next()-.5)*bend*.10)*.92;Vec q=new Vec(p.x+dx*.12+lx,y,p.z+dz*.12+lz);double rr=rad*Math.pow(1-y/(double)h,.25+s.taper*1.2)+.35;b.tube(p,q,rr+.15,rr,Kind.WOOD,r,.9,rough);p=q;trunk.add(p);}int rc=rootN;for(int n=0;n<rc;n++){double a=n/(double)Math.max(1,rc)*Math.PI*2+r.next()*.32,len=Math.max(2.5,h*.16)*rootLen*(.72+r.next()*.42);Vec rootStart=new Vec(0,.5,0);double rootUp=Math.tan(s.rootAngle)-s.rootDepth*.28;grow(b,rootStart,a,len,Math.max(.55,rad*s.rootThickness),1,r,new Opt(1,rootUp,-s.rootDepth*.38,.45,0,1,.68,s.form),rough,rootStart,len*1.04);}
            double flatten=((s.preset==Preset.ACACIA||s.preset==Preset.CHERRY)?.48:s.preset==Preset.PINE?1.25:s.preset==Preset.AZALEA?.68:.78)*crown;flatten*=switch(s.form){case ROUND->1.02;case OVAL,COLUMNAR->1.24;case VASE->.82;case SPREADING,UMBRELLA,LAYERED->.66;case CONICAL->1.18;case OPEN,WINDSWEPT->.82;case MULTI_STEM->1.05;case WEEPING->1.18;default->1.0;};for(int k=1;k<stems;k++){double sa=leanDir+Math.PI*2*k/stems,so=Math.max(.8,rad*.58),stemLen=h*(.66+r.next()*.16);Vec stemStart=new Vec(Math.cos(sa)*so,0,Math.sin(sa)*so);grow(b,stemStart,sa,stemLen,rad*.68,Math.min(2,s.levels),r,new Opt(.34,1.25,-.05,.45+bend,leaf*.84,flatten,.72,s.form),rough,stemStart,stemLen*.78);}for(int k=0;k<forks;k++){int idx=Math.min(h-2,(int)(h*(s.forkHeight+(r.next()-.5)*.06))),remaining=Math.max(3,h-idx);double forkLen=Math.max(3,remaining*s.boughLength*(.88+r.next()*.20)),forkRadius=Math.max(.55,rad*s.boughThickness*(.90+r.next()*.16));Vec forkStart=trunk.get(Math.max(0,idx));double boughUp=Math.tan(s.boughAngle);grow(b,forkStart,leanDir+Math.PI+(k-(forks-1)/2.0)*1.02+(r.next()-.5)*.24,forkLen,forkRadius,Math.min(2,s.levels),r,new Opt(1,boughUp,-.08,.40+bend,leaf*.82,flatten,s.boughTaper,s.form),rough,forkStart,forkLen*1.02);}
            boolean tiered=s.preset==Preset.PINE||s.form==CrownForm.CONICAL||s.form==CrownForm.LAYERED;
            for(int n=0;n<bc;n++){double t=tiered?.18+n/(double)Math.max(1,bc-1)*.70:s.preset==Preset.BIRCH?.44+n/(double)Math.max(1,bc-1)*.48:s.preset==Preset.JUNGLE?.52+n/(double)Math.max(1,bc-1)*.42:.38+n/(double)Math.max(1,bc-1)*.54;if(s.pattern==BranchPattern.RANDOM)t=start+r.next()*(.95-start);if(s.pattern==BranchPattern.WHORLED)t=start+Math.floor(n/4.0)/Math.max(1,Math.ceil(bc/4.0)-1)*(.94-start);if(s.pattern==BranchPattern.OPPOSITE)t=start+Math.floor(n/2.0)/Math.max(1,Math.ceil(bc/2.0)-1)*(.94-start);if(s.form==CrownForm.VASE)t=.32+n/(double)Math.max(1,bc-1)*.58;if(s.form==CrownForm.UMBRELLA)t=.54+n/(double)Math.max(1,bc-1)*.38;if(s.form==CrownForm.SPREADING)t=.28+n/(double)Math.max(1,bc-1)*.52;if(s.form==CrownForm.COLUMNAR)t=.22+n/(double)Math.max(1,bc-1)*.72;t=Math.max(start,t);Vec at=trunk.get(Math.min(h-1,(int)(t*h)));double az=s.pattern==BranchPattern.WHORLED?(n%4)*Math.PI/2+Math.floor(n/4.0)*.36:s.pattern==BranchPattern.OPPOSITE?(n%2)*Math.PI+Math.floor(n/2.0)*.58:s.pattern==BranchPattern.RANDOM?r.next()*Math.PI*2:n*2.39996+(r.next()-.5)*.75;double len=h*branchLengthFactor(s,t,spread)*(spread*(.72+r.next()*.42))*bl*(1+Math.cos(az-leanDir)*asym*.5),maxLen=h*switch(s.form){case VASE,SPREADING,ANCIENT->.48;case UMBRELLA,WINDSWEPT,FANCY->.44;case COLUMNAR->.24;default->.40;};len=Math.min(len,maxLen);if(s.preset==Preset.ACACIA||s.preset==Preset.CHERRY)len*=1.22;if(s.preset==Preset.DARK_OAK||s.preset==Preset.PALE_OAK)len*=1.14;if(s.preset==Preset.BIRCH)len*=.72;if(s.preset==Preset.JUNGLE)len*=1.08;len=Math.min(len,maxLen);double up=lift;if(s.preset==Preset.BIRCH)up+=.12;if(tiered)up-=.06;if(s.form==CrownForm.VASE)up+=.22;if(s.form==CrownForm.SPREADING||s.form==CrownForm.UMBRELLA)up-=.18;double dr=(s.preset==Preset.CHERRY?-.28:s.preset==Preset.ACACIA?.02:-.08)+droop;if(s.form==CrownForm.WEEPING)dr=Math.min(dr,-.72);grow(b,at,az,len,Math.max(.55,rad*(1-t)*s.branchThickness),s.levels,r,new Opt(tiered?.95:1,up,dr,.6+bend,leaf*(tiered?.78:1),flatten,s.branchTaper,s.form),rough,at,len*1.08);}if(tiered){b.sphere(p.x,p.y,p.z,leaf*.70,Kind.LEAVES,r,.92*crown,rough);}else{int topBase=Math.max(1,Math.min(h-2,(int)Math.round(h*.80))),leaders=3;double leaderLen=Math.min(h*.22,Math.max(4.0,leaf*3.2)),leaderHorizontal=s.form==CrownForm.COLUMNAR?.30:.58;for(int n=0;n<leaders;n++){Vec at=trunk.get(Math.min(h-1,topBase+n*Math.max(1,(h-topBase)/Math.max(1,leaders))));double az=n*Math.PI*2/leaders+leanDir*.35;grow(b,at,az,leaderLen*(.82+r.next()*.18),Math.max(.55,rad*.30),1,r,new Opt(leaderHorizontal,.56,-.06,.34+bend*.35,leaf*.72,flatten,.72,s.form),rough,at,leaderLen*1.02);}b.sphere(p.x,p.y,p.z,leaf*.56,Kind.LEAVES,r,(s.preset==Preset.ACACIA?.45:.72)*crown,rough);}if(s.density<1)b.thinLeaves(r,s.density);return b.build();}
        private static GeneratedObject mangrove(Settings s,Builder b,Rand r,Rand v){
            double sv=s.seedVariation;int h=Math.max(11,(int)Math.round(s.height*(1+j(v,.14,sv)))),rootN=Math.max(4,Math.min(20,(int)Math.round(s.rootCount+j(v,2.0,sv)))),branchN=Math.max(7,Math.min(30,(int)Math.round(s.branches*(1+j(v,.20,sv)))));double rad=Math.max(.85,s.radius*(1+j(v,.12,sv))),bend=Math.max(.08,s.bend*(1+j(v,.35,sv))),rough=clamp(s.leafRough+j(v,.18,sv),0,1),spread=Math.max(.55,s.canopyWidth*(1+j(v,.14,sv))),crown=Math.max(.35,s.canopyHeight*(1+j(v,.12,sv))),leaf=Math.max(1.8,s.leafSize*(1+j(v,.14,sv))),rootHub=Math.max(3.5,Math.min(h*.34,h*(.22+.04*r.next()))),rootReach=Math.max(3.5,(h*.18+rad*1.4)*s.rootLength),lean=s.lean+j(v,.13,sv),dir=s.leanDir+j(v,.75,sv);
            Vec hub=new Vec(0,rootHub,0);b.sphere(hub.x,hub.y,hub.z,Math.max(1.0,rad*.72),Kind.WOOD,r,.82,.06);
            int mainRoots=Math.min(4,rootN);for(int n=0;n<rootN;n++){boolean main=n<mainRoots;double az=main?n*Math.PI*.5+.18:(n-mainRoots)*Math.PI*2/Math.max(1,rootN-mainRoots)+.18+Math.PI/Math.max(2,rootN-mainRoots),startY=rootHub*(main?.92:.64)+r.next()*.35,reach=rootReach*(main?(1.00+.10*r.next()):(.62+.20*r.next())),side=(r.next()-.5)*reach*(main?.07:.13);Vec start=new Vec(Math.cos(az)*rad*.24,startY,Math.sin(az)*rad*.24),shoulder=new Vec(Math.cos(az)*reach*.25-Math.sin(az)*side,startY-.45-r.next()*.30,Math.sin(az)*reach*.25+Math.cos(az)*side),knee=new Vec(Math.cos(az)*reach*.64-Math.sin(az)*side,startY*(main?.36:.28),Math.sin(az)*reach*.64+Math.cos(az)*side),foot=new Vec(Math.cos(az)*reach-Math.sin(az)*side*.30,-.12-s.rootDepth*.35*r.next(),Math.sin(az)*reach+Math.cos(az)*side*.30);double rootRadius=Math.max(.56,rad*s.rootThickness*(main?.88:.58));b.tube(start,shoulder,rootRadius,rootRadius*.92,Kind.ROOT,r,.90,.025);b.tube(shoulder,knee,rootRadius*.92,rootRadius*.70,Kind.ROOT,r,.88,.03);b.tube(knee,foot,rootRadius*.70,.50,Kind.ROOT,r,.86,.035);if(!main&&n%2==0){double forkAz=az+(r.next()>.5?1:-1)*(.34+.18*r.next());Vec fork=new Vec(foot.x+Math.cos(forkAz)*reach*.22,foot.y-.03,foot.z+Math.sin(forkAz)*reach*.22);b.tube(knee,fork,rootRadius*.48,.42,Kind.ROOT,r,.84,.04);}}
            b.tube(new Vec(0,rootHub*.78,0),new Vec(0,-.10,0),Math.max(.65,rad*.48),.58,Kind.ROOT,r,.86,.04);
            List<Vec> trunk=new ArrayList<>();Vec p=hub;trunk.add(p);double dx=(r.next()-.5)*bend,dz=(r.next()-.5)*bend,lstep=Math.tan(lean),lx=Math.cos(dir)*lstep,lz=Math.sin(dir)*lstep;int trunkSteps=Math.max(5,(int)Math.round(h-rootHub));for(int i=1;i<=trunkSteps;i++){double t=i/(double)trunkSteps;dx=(dx+(r.next()-.5)*bend*.16)*.90;dz=(dz+(r.next()-.5)*bend*.16)*.90;Vec q=new Vec(p.x+dx*.17+lx,p.y+1,p.z+dz*.17+lz);double rr=rad*Math.pow(1-t,.32+s.taper*.65)+.42;b.tube(p,q,rr+.10,rr,Kind.WOOD,r,.90,rough*.45);p=q;trunk.add(p);}
            int leaders=Math.max(2,s.stemCount>1?s.stemCount:2+(branchN>=12?1:0));for(int n=0;n<leaders;n++){int index=Math.max(1,Math.min(trunk.size()-2,(int)Math.round(trunkSteps*(.48+n*.10))));Vec at=trunk.get(index);double az=dir+n*Math.PI*2/leaders+(r.next()-.5)*.55,len=h*(.24+.08*r.next())*s.branchLength;grow(b,at,az,len,Math.max(.62,rad*(.42-.05*n)),Math.min(2,s.levels),r,new Opt(.78,.46,-.06,.55+bend,leaf*.92,.58*crown,s.branchTaper,s.form),rough,at,len*1.16);}
            for(int n=0;n<branchN;n++){double tier=.42+n/(double)Math.max(1,branchN-1)*.52;int index=Math.max(1,Math.min(trunk.size()-1,(int)Math.round(trunkSteps*tier)));Vec at=trunk.get(index);double az=n*2.399963229728653+(r.next()-.5)*.72,len=h*(.20+.13*(1-tier))*spread*s.branchLength*(.82+r.next()*.34),up=.18+s.branchAngle*.18-(tier-.42)*.18,drop=-.10-s.branchDroop*.18-(1-tier)*.08;grow(b,at,az,len,Math.max(.55,rad*(1-tier)*s.branchThickness),Math.min(3,s.levels),r,new Opt(1,up,drop,.68+bend,leaf,.54*crown,s.branchTaper,s.form),rough,at,len*1.14);if(n%3==0){Vec curtain=new Vec(at.x+Math.cos(az)*len*.72,at.y+.08,at.z+Math.sin(az)*len*.72);int drops=1+(int)Math.round((1+r.next()*3)*s.density);for(int d=1;d<=drops;d++)b.put(curtain.x+(r.next()-.5)*.8,curtain.y-d,curtain.z+(r.next()-.5)*.8,Kind.LEAVES);}}
            for(int n=0;n<4;n++){double az=n*Math.PI*.5+.38,reach=leaf*(.55+.18*r.next());b.sphere(p.x+Math.cos(az)*reach,p.y-.15+r.next()*.7,p.z+Math.sin(az)*reach,leaf*(.62+.14*r.next()),Kind.LEAVES,r,.52*crown,rough);}
            if(s.density<1)b.thinLeaves(r,Math.max(.45,s.density));return b.build();
        }
        private static void grow(Builder b,Vec start,double az,double len,double rad,int level,Rand r,Opt o,double rough,Vec envelope,double maxReach){Vec p=start;double x=Math.cos(az)*o.horizontal,y=o.up,z=Math.sin(az)*o.horizontal;int steps=Math.max(3,(int)Math.round(len/2)),count=steps+1;Vec[]pts=new Vec[count];pts[0]=p;double limit=Math.max(1,maxReach-o.leaf*.58);for(int i=1;i<=steps;i++){double t=i/(double)steps,tw=(r.next()-.5)*o.wander;x+=Math.cos(az+tw)*.06;z+=Math.sin(az+tw)*.06;y+=o.droop/steps;double m=Math.sqrt(x*x+y*y+z*z);Vec q=new Vec(p.x+x/m*len/steps,p.y+y/m*len/steps,p.z+z/m*len/steps),rel=new Vec(q.x-envelope.x,0,q.z-envelope.z);double reach=Math.hypot(rel.x,rel.z);if(reach>limit)q=new Vec(envelope.x+rel.x/reach*limit,q.y,envelope.z+rel.z/reach*limit);b.tube(p,q,rad*(1-(i-1)/(double)steps*o.taper),rad*(1-t*o.taper),Kind.WOOD,r,.9,rough);p=q;pts[i]=p;}if(level>1){int kids=2+(int)(r.next()*2);for(int j=0;j<kids;j++){Vec at=pts[(int)(steps*(.45+r.next()*.45))];grow(b,at,az+(j>0?1:-1)*(1+r.next()*.8),len*(.32+r.next()*.13),Math.max(.55,rad*.52),level-1,r,new Opt(o.horizontal*.9,o.up*.75,o.droop*1.2,o.wander*1.25,o.leaf*.78,o.flatten,o.taper,o.form),rough,envelope,maxReach);}}int clusters=2+(int)(r.next()*3);for(int j=0;j<clusters;j++){int pi=Math.max(1,steps-(int)Math.round(j*steps*.16));Vec ep=pts[pi];if(o.leaf>0)b.sphere(ep.x+(r.next()-.5)*1.2,ep.y+(r.next()-.5)*.7,ep.z+(r.next()-.5)*1.2,o.leaf*(.58+r.next()*.24),Kind.LEAVES,r,o.flatten,rough);}if(o.form==CrownForm.WEEPING&&o.leaf>0&&len>5){int drops=2+(int)(r.next()*3);for(int d=1;d<=drops;d++)b.sphere(p.x+(r.next()-.5)*.7,p.y-d*1.35,p.z+(r.next()-.5)*.7,o.leaf*.42,Kind.LEAVES,r,1.18,rough*.8);}}
        private static double branchLengthFactor(Settings s,double t,double spread){return switch(s.form){case CONICAL,LAYERED->(1-t)*.48+.09;case COLUMNAR->.34;case VASE->.22+.34*t;case SPREADING->.31;case UMBRELLA->.28;case OPEN->.24;case WEEPING->.25;default->s.preset==Preset.PINE?(1-t)*.42+.10:.20+.20*spread;};}
        private static GeneratedObject palm(Settings s,Builder b,Rand r,Rand v){
            double sv=s.seedVariation;int h=Math.max(9,(int)Math.round(s.height*(1+j(v,.14,sv)))),fronds=Math.max(7,Math.min(28,(int)Math.round(s.branches*(1+j(v,.16,sv)))));double rad=Math.max(.75,s.radius*(1+j(v,.10,sv))),bend=Math.max(0,s.bend*(1+j(v,.30,sv))),lean=s.lean+j(v,.08,sv),dir=s.leanDir+j(v,.42,sv),leaf=Math.max(1.8,s.leafSize*(1+j(v,.13,sv))),frondLen=Math.min(h*.48,Math.max(6,h*.235*s.canopyWidth*s.branchLength));
            Vec p=new Vec(0,0,0);for(int iy=1;iy<=h;iy++){double t=iy/(double)h,leanOffset=Math.tan(lean)*iy,arc=bend*h*(.085*t*t+.035*Math.sin(t*Math.PI)),offset=leanOffset+arc;Vec q=new Vec(Math.cos(dir)*offset,iy,Math.sin(dir)*offset);double baseFlare=1+.30*Math.max(0,1-t*5),rr=rad*(1-s.taper*.16*t)*baseFlare+.18;b.tube(p,q,rr+.08,rr,Kind.WOOD,r,.94,.05);p=q;}
            int roots=Math.max(0,s.rootCount);for(int n=0;n<roots;n++){double a=n*Math.PI*2/Math.max(1,roots)+r.next()*.35,len=(2.5+h*.12)*s.rootLength*(.78+r.next()*.34),drop=Math.tan(s.rootAngle)*len-s.rootDepth*len*.22;Vec end=new Vec(Math.cos(a)*len,.35+drop,Math.sin(a)*len);b.tube(new Vec(0,.35,0),end,Math.max(.55,rad*s.rootThickness),.55,Kind.WOOD,r,.82,.05);}
            int collarBases=Math.max(5,Math.min(10,fronds/2));for(int n=0;n<collarBases;n++){double a=n*2.399963229728653,drop=.35+(n%3)*.24,len=Math.max(1.2,rad*(1.05+(n%2)*.22));Vec base=new Vec(p.x,p.y-drop,p.z),tip=new Vec(base.x+Math.cos(a)*len,base.y-.35-r.next()*.25,base.z+Math.sin(a)*len);b.tube(base,tip,Math.max(.55,rad*.42),.45,Kind.WOOD,r,.88,.06);}
            for(int n=0;n<fronds;n++){double age=n/(double)Math.max(1,fronds-1),az=n*2.399963229728653+(r.next()-.5)*.20,baseDrop=age*(1.15+s.canopyHeight*.35),baseReach=Math.min(rad*.62,.45+age*.45),lenFactor=.64+.36*Math.pow(Math.sin(Math.PI*(.12+.78*age)),.62),len=frondLen*lenFactor*(.91+r.next()*.18),lift=.72-.48*age+Math.tan(s.branchAngle)*.16+s.canopyHeight*.035,droop=.04+.56*age+Math.max(0,-s.branchDroop)*.24+r.next()*.08;Vec base=new Vec(p.x+Math.cos(az)*baseReach,p.y-baseDrop,p.z+Math.sin(az)*baseReach);palmFrond(b,base,az,len,leaf,lift,droop,s.branchThickness,s.branchTaper,s.leafRough,age,r);}
            int spears=Math.max(2,Math.min(4,fronds/6));for(int n=0;n<spears;n++){double az=n*Math.PI*2/spears+.55,len=frondLen*(.48+r.next()*.10);palmFrond(b,new Vec(p.x,p.y-.10,p.z),az,len,leaf*.72,.90,.015,s.branchThickness*.86,s.branchTaper,s.leafRough,0,r);}
            b.sphere(p.x,p.y-.35,p.z,Math.max(.9,rad*.72),Kind.WOOD,r,.72,.08);if(s.density<1)b.thinLeaves(r,Math.max(.50,s.density));return b.build();
        }
        private static void palmFrond(Builder b,Vec crown,double az,double len,double leaf,double lift,double droop,double thickness,double taper,double foliageRoughness,double age,Rand r){
            int steps=Math.max(9,(int)Math.round(len*1.12));Vec prev=crown;for(int i=1;i<=steps;i++){double t=i/(double)steps,horizontal=len*t,vertical=len*(lift*t-(lift+droop)*t*t),side=Math.sin(t*Math.PI)*(r.next()-.5)*(.18+.18*foliageRoughness);Vec cur=new Vec(crown.x+Math.cos(az)*horizontal-Math.sin(az)*side,crown.y+vertical,crown.z+Math.sin(az)*horizontal+Math.cos(az)*side);double previous=(i-1.0)/steps,ra=Math.max(.48,thickness*(1-previous*taper*.88)),rb=Math.max(.42,thickness*(1-t*taper*.88));b.tube(prev,cur,ra,rb,Kind.WOOD,r,.90,.035);if(t>.16){b.palmTopCover(prev,cur,r,foliageRoughness);double u=(t-.16)/.84,envelope=Math.pow(Math.sin(Math.PI*u),.66),width=leaf*(.28+.58*envelope)*(1-.18*t),back=leaf*(.05+.10*u),down=leaf*(.02+.10*age+.08*u),stagger=((i&1)==0?1:-1)*leaf*.06;Vec left=new Vec(cur.x-Math.sin(az)*(width+stagger)-Math.cos(az)*back,cur.y-down,cur.z+Math.cos(az)*(width+stagger)-Math.sin(az)*back),right=new Vec(cur.x+Math.sin(az)*(width-stagger)-Math.cos(az)*back,cur.y-down,cur.z-Math.cos(az)*(width-stagger)-Math.sin(az)*back);double pinnaBase=Math.max(.48,.66-.10*t),pinnaTip=Math.max(.38,.48-.08*t);b.tube(cur,left,pinnaBase,pinnaTip,Kind.LEAVES,r,.76,foliageRoughness);b.tube(cur,right,pinnaBase,pinnaTip,Kind.LEAVES,r,.76,foliageRoughness);}prev=cur;}
        }
        private static GeneratedObject mushroom(Settings s,Builder b,Rand r,Rand v){double sv=s.seedVariation;int h=Math.max(5,(int)Math.round(s.mushHeight*(1+j(v,.22,sv))));double rad=Math.max(.7,s.mushRadius*(1+j(v,.18,sv))),leaf=Math.max(2.5,s.capRadius*(1+j(v,.28,sv))),ang=s.mushLeanDir+j(v,Math.PI,sv),lean=s.mushLean+j(v,.24,sv),bend=Math.max(0,s.mushCurve*(1+j(v,.8,sv))+Math.abs(j(v,.18,sv))),taper=clamp(s.mushTaper+j(v,.22,sv),0,.85),flare=Math.max(0,s.mushFlare+j(v,.35,sv));Vec p=new Vec(0,0,0);double x=0,z=0;for(int y=1;y<=h;y++){x+=Math.cos(ang)*Math.tan(lean)+(r.next()-.5)*bend*.14;z+=Math.sin(ang)*Math.tan(lean)+(r.next()-.5)*bend*.14;Vec q=new Vec(x,y,z);double boost=1+flare*Math.max(0,1-y/Math.max(2,h*.24)),rr=rad*(1-taper*y/(double)h*.55)*boost;b.tube(p,q,Math.max(.7,rr+.08),Math.max(.65,rr),Kind.WOOD,r,.9,0);p=q;}x+=Math.cos(ang)*s.capOffset*leaf*.55;z+=Math.sin(ang)*s.capOffset*leaf*.55;b.cap(x,h,z,leaf,s.preset==Preset.MUSHROOM_RED,s,r);return b.build();}
        private static double j(Rand r,double amount,double sv){return (r.next()*2-1)*amount*sv;} private static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
    }

    record Vec(double x,double y,double z){} record Opt(double horizontal,double up,double droop,double wander,double leaf,double flatten,double taper,CrownForm form){} enum Kind{WOOD,ROOT,LEAVES} record Cell(Kind kind,int axis){}
    static final class Rand {int a;Rand(int a){this.a=a;}double next(){a+=0x6D2B79F5;int t=a;t=(t^(t>>>15))*(1|t);t^=t+(t^(t>>>7))*(61|t);return ((t^(t>>>14))&0xffffffffL)/4294967296.0;}}
    static final class Builder {
        final Settings s; final Map<Long,Cell> blocks=new HashMap<>(); Builder(Settings s){this.s=s;}
        void put(double x,double y,double z,Kind k){put(x,y,z,k,2);}
        void put(double x,double y,double z,Kind k,int axis){int xx=(int)Math.round(x),yy=(int)Math.round(z),zz=(int)Math.round(y);long q=key(xx,yy,zz);Cell existing=blocks.get(q);if(k==Kind.WOOD){if(existing==null||existing.kind!=Kind.WOOD)blocks.put(q,new Cell(k,axis));}else if(k==Kind.ROOT){if(existing==null||existing.kind==Kind.LEAVES)blocks.put(q,new Cell(k,axis));}else if(existing==null)blocks.put(q,new Cell(k,axis));}
        void putCap(double x,double y,double z){int xx=(int)Math.round(x),yy=(int)Math.round(z),zz=(int)Math.round(y);blocks.put(key(xx,yy,zz),new Cell(Kind.LEAVES,2));}
        void sphere(double cx,double cy,double cz,double r,Kind kind,Rand random,double flatten,double rough){sphere(cx,cy,cz,r,kind,random,flatten,rough,2);}
        void sphere(double cx,double cy,double cz,double r,Kind kind,Rand random,double flatten,double rough,int axis){int q=(int)Math.ceil(r);for(int x=-q;x<=q;x++)for(int y=-q;y<=q;y++)for(int z=-q;z<=q;z++){double d=(x*x+z*z)/(r*r)+(y*y)/(r*r*flatten*flatten);if(d<=1){if(kind==Kind.LEAVES){double keep=Math.max(.04,Math.min(.98,.46+(.72-d)*1.24-rough*.20));if(random.next()>keep)continue;}put(cx+x,cy+y,cz+z,kind,axis);}}}
        void tube(Vec a,Vec b,double ra,double rb,Kind kind,Rand r,double flatten,double rough){double dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;double ax=Math.abs(dx),ay=Math.abs(dy),az=Math.abs(dz);int axis=(ay>=ax&&ay>=az)?2:((ax>=az)?0:1);int n=Math.max(2,(int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz)*2));for(int i=0;i<=n;i++){double t=i/(double)n;sphere(a.x+dx*t,a.y+dy*t,a.z+dz*t,Math.max(.55,ra+(rb-ra)*t),kind,r,flatten,rough,axis);}}
        void palmTopCover(Vec a,Vec b,Rand r,double rough){double dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;int n=Math.max(2,(int)Math.ceil(Math.sqrt(dx*dx+dy*dy+dz*dz)*2));for(int i=0;i<=n;i++){if(r.next()<rough*.035)continue;double t=i/(double)n;put(a.x+dx*t,a.y+dy*t+.68,a.z+dz*t,Kind.LEAVES);}}
        void thinLeaves(Rand r,double density){Iterator<Map.Entry<Long,Cell>> it=blocks.entrySet().iterator();while(it.hasNext()){Map.Entry<Long,Cell> e=it.next();if(e.getValue().kind==Kind.LEAVES&&r.next()>density)it.remove();}}
        void cap(double cx,double cy,double cz,double rr,boolean red,Settings s,Rand r){
            MushroomForm form=s.mushroomForm==MushroomForm.NATURAL?(red?MushroomForm.DOME:MushroomForm.FLAT):s.mushroomForm;
            double rx=rr*(1+s.capAsym*.38),rz=rr*(1-s.capAsym*.20),hh=Math.max(1.5,rr*.70*s.capHeight);int qx=(int)Math.ceil(rx+2),qz=(int)Math.ceil(rz+2);double centreProfile=switch(form){case DOME->1;case BELL->1;case UMBRELLA->.80;case FLAT->.30;case FUNNEL->.10;case WAVY->.38;default->1;},centreTop=centreProfile*hh+s.capBump*hh*.35-s.capDepression*hh*.42,centreBottom=centreTop-Math.max(1,s.capThickness);for(int y=0;y<Math.ceil(centreBottom);y++)put(cx,cy+y,cz,Kind.WOOD);
            for(int x=-qx;x<=qx;x++)for(int z=-qz;z<=qz;z++){double a=Math.atan2(z,x),nr=Math.sqrt(x*x/(rx*rx)+z*z/(rz*rz)),wave=form==MushroomForm.WAVY?Math.sin(a*Math.max(4,s.capWaves))*s.capWaveStrength*.18:Math.sin(a*Math.max(1,s.capWaves))*s.capWaveStrength*.10,rim=1+wave;if(nr>rim)continue;double u=Math.min(1,nr/rim),profile=switch(form){case DOME->Math.sqrt(Math.max(0,1-u*u));case BELL->Math.pow(Math.max(0,1-u),.62);case UMBRELLA->.22+.58*Math.pow(Math.max(0,1-u*u),.42);case FLAT->.18+.12*(1-u*u);case FUNNEL->.10+.52*Math.pow(u,1.35);case WAVY->.20+.18*(1-u*u)+wave*.7;default->Math.sqrt(Math.max(0,1-u*u));};
                double sculpt=(s.capBump*hh*.35*Math.pow(1-u,2)-s.capDepression*hh*.42*Math.pow(1-u,2))+s.capEdge*hh*.22*Math.pow(u,3),top=profile*hh+sculpt,bottom=top-Math.max(1,s.capThickness);int y0=(int)Math.ceil(bottom),y1=(int)Math.floor(top);if(y1<y0)y1=y0;
                for(int y=y0;y<=y1;y++)if(r.next()>s.capRoughness*.055||u<.72)putCap(cx+x,cy+y,cz+z);
            }
            int crownY=(int)Math.round(centreTop);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x*x+z*z<=2)putCap(cx+x,cy+crownY,cz+z);
        }
        GeneratedObject build(){if(blocks.isEmpty())throw new IllegalStateException("Empty tree");int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;for(long k:blocks.keySet()){int x=x(k),y=y(k),z=z(k);minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}Map<Long,Material> out=new HashMap<>();for(var e:blocks.entrySet()){int x=x(e.getKey())-minX,y=y(e.getKey())-minY,z=z(e.getKey())-minZ;out.put(key(x,y,z),material(e.getValue(),x+minX,y+minY,z+minZ));}return new GeneratedObject(s.preset.displayName(),new Point3i(maxX-minX+1,maxY-minY+1,maxZ-minZ+1),new Point3i(minX,minY,minZ),out);}
        Material material(Cell cell,int x,int y,int z){Kind k=cell.kind;if(s.preset==Preset.MUSHROOM_RED||s.preset==Preset.MUSHROOM_BROWN){String n=k==Kind.WOOD?"mushroom_stem":s.preset==Preset.MUSHROOM_RED?"red_mushroom_block":"brown_mushroom_block";return mushroomMaterial(n,k,x,y,z);}if(k==Kind.WOOD)return mat(s.wood.name().toLowerCase()+(s.trunkBlock==TrunkBlock.WOOD?"_wood":"_log"),k,cell.axis);if(k==Kind.ROOT)return Material.get("minecraft:mangrove_roots",Map.of("waterlogged","false"));String n=s.leaves.name().toLowerCase();if(n.equals("azalea")&&hash(x,y,z)<.24)n="flowering_azalea";return mat(n+"_leaves",k,cell.axis);}
        Material mushroomMaterial(String n,Kind k,int x,int y,int z){Map<String,String> p=new LinkedHashMap<>();int[][]d={{0,0,-1},{1,0,0},{0,-1,0},{0,1,0},{0,0,1},{-1,0,0}};String[]names={"down","east","north","south","up","west"};for(int i=0;i<6;i++){Cell q=blocks.get(key(x+d[i][0],y+d[i][1],z+d[i][2]));boolean outer=k==Kind.WOOD||((i!=0)&&((q==null)||(q.kind!=Kind.LEAVES)));p.put(names[i],Boolean.toString(outer));}return Material.get("minecraft:"+n,p);}
        static Material mat(String n,Kind k,int axis){Map<String,String> p=new LinkedHashMap<>();if(n.endsWith("_log"))p.put("axis",axis==0?"x":axis==1?"z":"y");if(n.endsWith("_leaves")){p.put("distance","1");p.put("persistent","true");}return Material.get("minecraft:"+n,p.isEmpty()?null:p);}
        static double hash(int x,int y,int z){int n=x*73856093^y*19349663^z*83492791;return (n&0xffffffffL)/4294967295.0;}
        static long key(int x,int y,int z){return ((long)(x+1048576)&0x1fffffL)<<42|((long)(y+1048576)&0x1fffffL)<<21|((long)(z+1048576)&0x1fffffL);}static int x(long k){return (int)((k>>>42)&0x1fffff)-1048576;}static int y(long k){return (int)((k>>>21)&0x1fffff)-1048576;}static int z(long k){return (int)(k&0x1fffff)-1048576;}
    }

    static final class GeneratedObject implements WPObject {
        private String name; final Point3i dimensions,offset; final Map<Long,Material> blocks; private Map<String,Serializable> attributes;
        GeneratedObject(String n,Point3i d,Point3i o,Map<Long,Material>b){name=n;dimensions=d;offset=o;blocks=Collections.unmodifiableMap(b);attributes=new HashMap<>();attributes.put(ATTRIBUTE_OFFSET.key,o);}
        public String getName(){return name;}public void setName(String n){name=n;}public Point3i getDimensions(){return new Point3i(dimensions);}public Point3i getOffset(){return new Point3i(offset);}public Material getMaterial(int x,int y,int z){return blocks.get(Builder.key(x,y,z));}public boolean getMask(int x,int y,int z){return blocks.containsKey(Builder.key(x,y,z));}public List<Entity> getEntities(){return null;}public List<TileEntity> getTileEntities(){return null;}public void prepareForExport(org.pepsoft.worldpainter.Dimension d){}public Map<String,Serializable> getAttributes(){return attributes;}public void setAttributes(Map<String,Serializable>a){attributes=a;}public <T extends Serializable>void setAttribute(AttributeKey<T>k,T v){if(v==null)attributes.remove(k.key);else attributes.put(k.key,v);}public GeneratedObject clone(){GeneratedObject o=new GeneratedObject(name,dimensions,offset,blocks);o.attributes=new HashMap<>(attributes);return o;}
    }

    /** Cached textured voxel renderer with a low-allocation 60 FPS interaction path. */
    static final class TreePreviewPanel extends JPanel {
        TreePreviewPanel(ColourScheme colours) {
            this.colours = colours;
            setOpaque(true);
            setDoubleBuffered(true);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            frameTimer = new javax.swing.Timer(16, e -> { if (renderRequested) repaint(); });
            frameTimer.setCoalesce(true);
            settleTimer = new javax.swing.Timer(140, e -> { interactive = false; markDirty(); });
            settleTimer.setRepeats(false);
            MouseAdapter mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow(); last = e.getPoint(); interactive = true;
                    dragMode = (SwingUtilities.isMiddleMouseButton(e)) ? DragMode.DOLLY
                            : ((SwingUtilities.isRightMouseButton(e) || e.isShiftDown()) ? DragMode.PAN : DragMode.ORBIT);
                    setCursor(Cursor.getPredefinedCursor(dragMode == DragMode.PAN ? Cursor.MOVE_CURSOR
                            : (dragMode == DragMode.DOLLY ? Cursor.N_RESIZE_CURSOR : Cursor.CROSSHAIR_CURSOR)));
                    markDirty();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (last == null) return;
                    int dx = (int) clamp(e.getX() - last.x, -120, 120), dy = (int) clamp(e.getY() - last.y, -120, 120);
                    if (dragMode == DragMode.PAN) {
                        panX += dx; panY += dy;
                    } else if (dragMode == DragMode.DOLLY) {
                        zoom = clamp(zoom * Math.exp(-dy * 0.012), MIN_ZOOM, MAX_ZOOM);
                    } else {
                        final double viewportSpan = clamp(getWidth() + getHeight(), 600.0, 1200.0);
                        yaw = normaliseAngle(yaw + Math.PI * 2.0 * dx / viewportSpan);
                        pitch = clamp(pitch + Math.PI * 2.0 * dy / viewportSpan, -MAX_PITCH, MAX_PITCH);
                    }
                    last = e.getPoint(); markDirty();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    last = null; dragMode = DragMode.ORBIT; interactive = true; settleTimer.restart();
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); markDirty();
                }
                @Override public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) resetView();
                }
                @Override public void mouseWheelMoved(MouseWheelEvent e) {
                    interactive = true;
                    double modifier = Math.abs(e.getPreciseWheelRotation()) >= 1.0 ? 1.0 : 0.25;
                    zoom = clamp(zoom * Math.exp(-e.getPreciseWheelRotation() * 0.12 * modifier), MIN_ZOOM, MAX_ZOOM);
                    settleTimer.restart(); markDirty();
                }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse); addMouseWheelListener(mouse);
        }

        @Override public void addNotify() { super.addNotify(); frameTimer.start(); }
        @Override public void removeNotify() { frameTimer.stop(); settleTimer.stop(); super.removeNotify(); }

        void setObject(GeneratedObject object) {
            this.object=object;faces.clear();visibleFaces.clear();points.clear();forceLod=object!=null&&object.blocks.size()>120000;
            if(object!=null){
                Map<String,Color>colourCache=new HashMap<>();
                int lodCell=forceLod?(int)clamp(Math.ceil(Math.cbrt(object.blocks.size()/55000.0)),2,8):1;
                Map<Long,LodBucket>lod=forceLod?new HashMap<>():null;
                for(Map.Entry<Long,Material>entry:object.blocks.entrySet()){
                    int x=Builder.x(entry.getKey()),y=Builder.y(entry.getKey()),z=Builder.z(entry.getKey());Material material=entry.getValue();boolean foliage=isTransparentFoliage(material);Color base=colourCache.computeIfAbsent(material.name,ignored->materialColour(material));
                    if(forceLod){int bx=x/lodCell,by=y/lodCell,bz=z/lodCell;long bucketKey=Builder.key(bx,by,bz);LodBucket bucket=lod.computeIfAbsent(bucketKey,ignored->new LodBucket(lodCell));bucket.add(x+.5,y+.5,z+.5,base.getRGB(),!foliage);}
                    else {points.add(new VoxelPoint(x+.5,y+.5,z+.5,base.getRGB(),1));int axis=axisFor(object,x,y,z,material);for(int d=0;d<DIRECTIONS.length;d++){int[]n=DIRECTIONS[d];Material neighbour=object.blocks.get(Builder.key(x+n[0],y+n[1],z+n[2]));if(neighbour==null||(foliage&&!neighbour.name.equals(material.name)))faces.add(new VoxelFace(x,y,z,d,axis,material,shade(base,SHADES[d])));}}
                }
                if(forceLod)for(LodBucket bucket:lod.values())points.add(bucket.point());
            }
            updateGridAnchor();
            panX = panY = 0; markDirty();
        }

        void resetView() { yaw = -0.65; pitch = 0.52; zoom = 1.0; panX = panY = 0; interactive = false; markDirty(); }
        private void markDirty() { renderRequested = true; if (!frameTimer.isRunning()) repaint(); }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
            if (cachedFrame == null || cachedFrame.getWidth() != w || cachedFrame.getHeight() != h || renderRequested) {
                renderRequested = false;
                cachedFrame=renderFrame(w,h,interactive||forceLod);
            }
            graphics.drawImage(cachedFrame, 0, 0, null);
        }

        private BufferedImage renderFrame(int width, int height, boolean fast) {
            BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = frame.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                Color bg = UIManager.getColor("Panel.background"); if (bg == null) bg = getBackground();
                g.setColor(bg); g.fillRect(0, 0, width, height);
                if(object==null||points.isEmpty())return frame;

                Point3i dim = object.dimensions;
                double ox = dim.x * .5, oy = dim.y * .5, oz = dim.z * .5;
                View view = fittedView(width, height, dim, ox, oy, oz);
                // The ground grid participates in the view order like Blockbench: behind the model from above,
                // in front of it from below. This prevents the model from incorrectly showing through the floor.
                boolean gridInFront = pitch < 0.0;
                if (!gridInFront) drawGrid(g, view, gridX, gridY, -oz, Math.max(dim.x, dim.y), false);
                if (fast) {
                    drawFastLod(frame, view, ox, oy, oz);
                    if (gridInFront) drawGrid(g, view, gridX, gridY, -oz, Math.max(dim.x, dim.y), true);
                    g.setColor(new Color(isDark(bg) ? 0xd8d8d8 : 0x333333));
                    g.drawString(s("ui.treeGenerator.previewHint"), 12, height - 12);
                    return frame;
                }

                double cosY = Math.cos(yaw), sinY = Math.sin(yaw), cosP = Math.cos(pitch), sinP = Math.sin(pitch);
                visibleFaces.clear();
                for (VoxelFace face : faces) {
                    int[] normal = DIRECTIONS[face.direction];
                    double normalDepth = (normal[0] * sinY + normal[1] * cosY) * cosP + normal[2] * sinP;
                    if (normalDepth <= 0.0001) continue;
                    double[][] vertices = FACE_VERTICES[face.direction]; double depth = 0;
                    for (int i = 0; i < 4; i++) {
                        double x = face.x + vertices[i][0] - ox, y = face.y + vertices[i][1] - oy, z = face.z + vertices[i][2] - oz;
                        double rx = x * cosY - y * sinY, depthY = x * sinY + y * cosY;
                        double screenY = z * cosP - depthY * sinP;
                        face.screenX[i] = (int)Math.round(view.cx + rx * view.scale);
                        face.screenY[i] = (int)Math.round(view.cy - screenY * view.scale);
                        depth += depthY * cosP + z * sinP;
                    }
                    face.depth = depth * .25; visibleFaces.add(face);
                }
                visibleFaces.sort(Comparator.comparingDouble(a -> a.depth));
                for (VoxelFace face : visibleFaces) {
                    if (!fast) {
                        BufferedImage texture=textureFor(face.material,face.direction,face.axis);
                        if(texture!=null){int turns=textureQuarterTurns(face.material,face.direction,face.axis);if(turns!=0)texture=rotatedTexture(texture,turns);drawTexture(g,face,texture,SHADES[face.direction]);}
                        else { g.setColor(face.fastColour); g.fillPolygon(face.screenX, face.screenY, 4); }
                    } else {
                        g.setColor(face.fastColour); g.fillPolygon(face.screenX, face.screenY, 4);
                    }
                }
                if (gridInFront) drawGrid(g, view, gridX, gridY, -oz, Math.max(dim.x, dim.y), true);
                g.setColor(new Color(isDark(bg) ? 0xd8d8d8 : 0x333333));
                g.drawString(s("ui.treeGenerator.previewHint"), 12, height - 12);
            } finally { g.dispose(); }
            return frame;
        }

        private void drawFastLod(BufferedImage frame, View view, double ox, double oy, double oz) {
            int width=frame.getWidth(),height=frame.getHeight(),length=width*height;
            if(fastDepth==null||fastDepth.length!=length)fastDepth=new float[length];
            Arrays.fill(fastDepth,Float.NEGATIVE_INFINITY);
            int[] pixels=((DataBufferInt)frame.getRaster().getDataBuffer()).getData();
            double cosY=Math.cos(yaw),sinY=Math.sin(yaw),cosP=Math.cos(pitch),sinP=Math.sin(pitch);
            // A voxel is not a screen-aligned point. Approximate its projected footprint so adjacent
            // blocks overlap cleanly while dragging instead of turning into disconnected stripes.
            double halfWidth=view.scale*.5*(Math.abs(cosY)+Math.abs(sinY));
            double halfHeight=view.scale*.5*(Math.abs(cosP)+Math.abs(sinP)*(Math.abs(sinY)+Math.abs(cosY)));
            int dirtyMinX=width,dirtyMaxX=-1,dirtyMinY=height,dirtyMaxY=-1;
            for(VoxelPoint point:points){
                double x=point.x-ox,y=point.y-oy,z=point.z-oz;
                double rx=x*cosY-y*sinY,depthY=x*sinY+y*cosY,screenY=z*cosP-depthY*sinP,depth=depthY*cosP+z*sinP;
                int sx=(int)Math.round(view.cx+rx*view.scale),sy=(int)Math.round(view.cy-screenY*view.scale);
                int radiusX=(int)clamp(Math.ceil(halfWidth*point.size-.35),1,8),radiusY=(int)clamp(Math.ceil(halfHeight*point.size-.35),1,8);
                int minX=Math.max(0,sx-radiusX),maxX=Math.min(width-1,sx+radiusX),minY=Math.max(0,sy-radiusY),maxY=Math.min(height-1,sy+radiusY);
                if(minX>maxX||minY>maxY)continue;
                dirtyMinX=Math.min(dirtyMinX,minX);dirtyMaxX=Math.max(dirtyMaxX,maxX);dirtyMinY=Math.min(dirtyMinY,minY);dirtyMaxY=Math.max(dirtyMaxY,maxY);
                for(int py=minY;py<=maxY;py++){int offset=py*width;for(int px=minX;px<=maxX;px++){int index=offset+px;if(depth>=fastDepth[index]){fastDepth[index]=(float)depth;pixels[index]=point.rgb;}}}
            }
            // A cheap depth/silhouette edge pass keeps the fast view readable without sorting or
            // texturing thousands of faces. It only scans the part of the frame touched by voxels.
            if(dirtyMaxX>=dirtyMinX&&dirtyMaxY>=dirtyMinY){
                int x0=Math.max(1,dirtyMinX),x1=Math.min(width-2,dirtyMaxX),y0=Math.max(1,dirtyMinY),y1=Math.min(height-2,dirtyMaxY);
                for(int py=y0;py<=y1;py++){int row=py*width;for(int px=x0;px<=x1;px++){int i=row+px;float d=fastDepth[i];if(d==Float.NEGATIVE_INFINITY)continue;
                    boolean edge=fastDepth[i-1]==Float.NEGATIVE_INFINITY||fastDepth[i+1]==Float.NEGATIVE_INFINITY||fastDepth[i-width]==Float.NEGATIVE_INFINITY||fastDepth[i+width]==Float.NEGATIVE_INFINITY
                            ||Math.abs(d-fastDepth[i-1])>.85f||Math.abs(d-fastDepth[i+1])>.85f||Math.abs(d-fastDepth[i-width])>.85f||Math.abs(d-fastDepth[i+width])>.85f;
                    if(edge){int c=pixels[i],r=(c>>16&255)*3/4,g=(c>>8&255)*3/4,b=(c&255)*3/4;pixels[i]=(c&0xff000000)|(r<<16)|(g<<8)|b;}
                }}
            }
        }

        private View fittedView(int width, int height, Point3i dim, double ox, double oy, double oz) {
            double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
            double cosY=Math.cos(yaw),sinY=Math.sin(yaw),cosP=Math.cos(pitch),sinP=Math.sin(pitch);
            for(VoxelPoint point:points){
                double x=point.x-ox,y=point.y-oy,z=point.z-oz;
                double rx=x*cosY-y*sinY,depthY=x*sinY+y*cosY,sy=-(z*cosP-depthY*sinP);
                minX=Math.min(minX,rx);maxX=Math.max(maxX,rx);minY=Math.min(minY,sy);maxY=Math.max(maxY,sy);
            }
            if(points.isEmpty()){minX=minY=-1;maxX=maxY=1;}
            minX-=.9;maxX+=.9;minY-=.9;maxY+=.9;
            double fit=Math.max(1.0,Math.min((width-72.0)/Math.max(1,maxX-minX),(height-86.0)/Math.max(1,maxY-minY)));
            double scale=fit*zoom;
            return new View(width*.5-(minX+maxX)*.5*scale+panX,(height-18)*.5-(minY+maxY)*.5*scale+panY,scale);
        }

        private void drawTexture(Graphics2D target, VoxelFace face, BufferedImage image, double shade) {
            Graphics2D g=(Graphics2D)target.create();
            try {
                g.clip(new Polygon(face.screenX,face.screenY,4));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                AffineTransform t=new AffineTransform((face.screenX[1]-face.screenX[0])/(double)image.getWidth(),(face.screenY[1]-face.screenY[0])/(double)image.getWidth(),(face.screenX[3]-face.screenX[0])/(double)image.getHeight(),(face.screenY[3]-face.screenY[0])/(double)image.getHeight(),face.screenX[0],face.screenY[0]);
                g.drawImage(image,t,null); g.setComposite(AlphaComposite.SrcAtop);
                if(shade<1){g.setColor(new Color(0,0,0,(int)clamp((1-shade)*180,0,150)));g.fillPolygon(face.screenX,face.screenY,4);}
                else if(shade>1){g.setColor(new Color(255,255,255,(int)clamp((shade-1)*150,0,80)));g.fillPolygon(face.screenX,face.screenY,4);}
            } finally {g.dispose();}
        }

        private BufferedImage textureFor(Material material,int direction,int logAxis){
            String key=material.name;int colon=key.indexOf(':');if(colon>=0)key=key.substring(colon+1);
            if(key.endsWith("_wood")) return textures.get(key.substring(0,key.length()-5)+"_log");
            if(key.equals("mangrove_roots"))return textures.get(direction>=4?"mangrove_roots_top":"mangrove_roots");
            if(key.endsWith("mushroom_block")){String[] names={"east","west","south","north","up","down"};Map<String,String> p=material.getProperties();if(direction==5||(p!=null&&"false".equals(p.get(names[direction]))))return textures.get("mushroom_block_inside");}
            int faceAxis=(direction<2)?0:((direction<4)?1:2);
            if((faceAxis==logAxis)&&key.endsWith("_log")){BufferedImage top=textures.get(key+"_top");if(top!=null)return top;}
            return textures.get(key);
        }

        private static int textureQuarterTurns(Material material,int direction,int logAxis){String key=material.name;if(!key.endsWith("_log"))return 0;int faceAxis=direction<2?0:direction<4?1:2;if(faceAxis==logAxis)return 0;int faceVAxis=faceAxis<2?2:1;return logAxis==faceVAxis?0:1;}
        private BufferedImage rotatedTexture(BufferedImage source,int turns){turns=((turns%4)+4)%4;if(turns==0)return source;BufferedImage[] variants=rotatedTextures.computeIfAbsent(source,k->new BufferedImage[4]);if(variants[turns]!=null)return variants[turns];int w=source.getWidth(),h=source.getHeight();BufferedImage out=new BufferedImage(turns%2==0?w:h,turns%2==0?h:w,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();if(turns==1){g.translate(h,0);g.rotate(Math.PI/2);}else if(turns==2){g.translate(w,h);g.rotate(Math.PI);}else{g.translate(0,w);g.rotate(-Math.PI/2);}g.drawImage(source,0,0,null);g.dispose();variants[turns]=out;return out;}

        private static int axisFor(GeneratedObject object,int x,int y,int z,Material material){
            Map<String,String> properties=material.getProperties();
            if(properties!=null){String axis=properties.get("axis");if("x".equals(axis))return 0;if("z".equals(axis))return 1;if("y".equals(axis))return 2;}
            if(!material.name.endsWith("_log"))return 2;
            int ax=same(object,x-1,y,z,material)+same(object,x+1,y,z,material),ay=same(object,x,y-1,z,material)+same(object,x,y+1,z,material),az=same(object,x,y,z-1,material)+same(object,x,y,z+1,material);
            return (ax>ay&&ax>az)?0:((ay>ax&&ay>az)?1:2);
        }
        private static int same(GeneratedObject object,int x,int y,int z,Material material){Material n=object.blocks.get(Builder.key(x,y,z));return n!=null&&n.name.equals(material.name)?1:0;}

        private static Map<String,BufferedImage> loadTextures(){
            Map<String,BufferedImage> result=new HashMap<>();
            for(String key:TEXTURE_KEYS){String resource="/org/pepsoft/worldpainter/trees/textures/"+key+".png";try(InputStream in=TreeGeneratorDialog.class.getResourceAsStream(resource)){if(in!=null){BufferedImage image=ImageIO.read(in);result.put(key,tintLeaves(key,image));}}catch(IOException ignored){}}
            return result;
        }
        private static BufferedImage tintLeaves(String key,BufferedImage source){
            int tint=switch(key){case "birch_leaves"->0x80a755;case "spruce_leaves"->0x619961;case "oak_leaves","jungle_leaves","acacia_leaves","dark_oak_leaves","mangrove_leaves"->0x77ab2f;default->-1;};
            if(tint<0||source==null)return source;BufferedImage out=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
            int tr=tint>>16&255,tg=tint>>8&255,tb=tint&255;
            for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++){int c=source.getRGB(x,y),a=c>>>24,r=c>>16&255,g=c>>8&255,b=c&255;out.setRGB(x,y,a<<24|(r*tr/255)<<16|(g*tg/255)<<8|b*tb/255);}return out;
        }
        private Color materialColour(Material material){try{return new Color(colours.getColour(material));}catch(RuntimeException ignored){return new Color(material.colour);}}
        private void updateGridAnchor(){
            if(object==null){gridX=gridY=0;return;}int minZ=Integer.MAX_VALUE,count=0;double sx=0,sy=0;
            for(Map.Entry<Long,Material> e:object.blocks.entrySet()){if(isLeaves(e.getValue()))continue;int z=Builder.z(e.getKey());if(z<minZ){minZ=z;count=0;sx=sy=0;}if(z<=minZ+1){sx+=Builder.x(e.getKey())+.5;sy+=Builder.y(e.getKey())+.5;count++;}}
            gridX=(count>0?sx/count:object.dimensions.x*.5)-object.dimensions.x*.5;gridY=(count>0?sy/count:object.dimensions.y*.5)-object.dimensions.y*.5;
        }
        private void drawGrid(Graphics2D g,View v,double anchorX,double anchorY,double groundZ,int objectSize,boolean foreground){
            Color fg=UIManager.getColor("Label.foreground");if(fg==null)fg=Color.GRAY;
            final int radius=(int)clamp(Math.ceil((objectSize*.5+8.0)/8.0)*8.0,16.0,96.0);
            final int minorStep=(v.scale>=6.0)?1:((v.scale>=3.0)?2:4),majorStep=8;
            Stroke oldStroke=g.getStroke();Font oldFont=g.getFont();Object oldAA=g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            for(int i=-radius;i<=radius;i+=minorStep){
                if(i==0)continue;boolean major=Math.floorMod(i,majorStep)==0;
                g.setStroke(new BasicStroke(major?1.25f:1.0f));
                g.setColor(new Color(fg.getRed(),fg.getGreen(),fg.getBlue(),major?(foreground?118:72):(foreground?58:30)));
                drawLine3D(g,anchorX+i,anchorY-radius,groundZ,anchorX+i,anchorY+radius,groundZ,v);
                drawLine3D(g,anchorX-radius,anchorY+i,groundZ,anchorX+radius,anchorY+i,groundZ,v);
            }
            g.setStroke(new BasicStroke(1.5f));g.setColor(new Color(fg.getRed(),fg.getGreen(),fg.getBlue(),foreground?145:92));
            drawLine3D(g,anchorX-radius,anchorY-radius,groundZ,anchorX+radius,anchorY-radius,groundZ,v);
            drawLine3D(g,anchorX+radius,anchorY-radius,groundZ,anchorX+radius,anchorY+radius,groundZ,v);
            drawLine3D(g,anchorX+radius,anchorY+radius,groundZ,anchorX-radius,anchorY+radius,groundZ,v);
            drawLine3D(g,anchorX-radius,anchorY+radius,groundZ,anchorX-radius,anchorY-radius,groundZ,v);
            g.setStroke(new BasicStroke(2.1f));
            g.setColor(new Color(235,70,70,205));drawLine3D(g,anchorX-radius,anchorY,groundZ,anchorX+radius,anchorY,groundZ,v);
            g.setColor(new Color(65,115,235,205));drawLine3D(g,anchorX,anchorY-radius,groundZ,anchorX,anchorY+radius,groundZ,v);
            Point origin=project3D(anchorX,anchorY,groundZ,v),north=project3D(anchorX,anchorY-radius,groundZ,v);
            g.setColor(new Color(245,245,245,210));g.fillOval(origin.x-3,origin.y-3,6,6);
            g.setColor(new Color(fg.getRed(),fg.getGreen(),fg.getBlue(),190));g.setFont(g.getFont().deriveFont(Font.BOLD,11f));
            g.drawString("N",north.x-4,north.y-7);
            g.setFont(oldFont);g.setStroke(oldStroke);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,oldAA);
        }
        private Point project3D(double x,double y,double z,View v){double cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);double rx=x*cy-y*sy,depthY=x*sy+y*cy;return new Point((int)Math.round(v.cx+rx*v.scale),(int)Math.round(v.cy-(z*cp-depthY*sp)*v.scale));}
        private void drawLine3D(Graphics2D g,double x1,double y1,double z1,double x2,double y2,double z2,View v){Point a=project3D(x1,y1,z1,v),b=project3D(x2,y2,z2,v);g.drawLine(a.x,a.y,b.x,b.y);}
        private static Color shade(Color c,double factor){return new Color((int)clamp(c.getRed()*factor,0,255),(int)clamp(c.getGreen()*factor,0,255),(int)clamp(c.getBlue()*factor,0,255));}
        private static boolean isDark(Color c){return c.getRed()*.299+c.getGreen()*.587+c.getBlue()*.114<128;}
        private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
        private static double normaliseAngle(double angle){angle%=Math.PI*2;if(angle>Math.PI)angle-=Math.PI*2;if(angle<-Math.PI)angle+=Math.PI*2;return angle;}

        private GeneratedObject object;private final ColourScheme colours;private final Map<String,BufferedImage> textures=loadTextures();private final Map<BufferedImage,BufferedImage[]> rotatedTextures=new IdentityHashMap<>();private final ArrayList<VoxelFace> faces=new ArrayList<>(),visibleFaces=new ArrayList<>();private final ArrayList<VoxelPoint> points=new ArrayList<>();
        private final javax.swing.Timer frameTimer,settleTimer;private BufferedImage cachedFrame;private float[] fastDepth;private Point last;private double gridX,gridY;private boolean interactive,forceLod,renderRequested=true;private double yaw=-.65,pitch=.52,zoom=1,panX,panY;private DragMode dragMode=DragMode.ORBIT;
        private enum DragMode{ORBIT,PAN,DOLLY}
        private record VoxelPoint(double x,double y,double z,int rgb,double size){}
        private static final class LodBucket{final int cell;double x,y,z;int count,rgb;boolean wood;LodBucket(int cell){this.cell=cell;}void add(double px,double py,double pz,int colour,boolean isWood){x+=px;y+=py;z+=pz;count++;if(isWood||!wood){rgb=colour;wood=isWood;}}VoxelPoint point(){return new VoxelPoint(x/count,y/count,z/count,rgb,Math.max(1,cell*.86));}}
        private static final class VoxelFace{final int x,y,z,direction,axis;final Material material;final Color fastColour;final int[]screenX=new int[4],screenY=new int[4];double depth;VoxelFace(int x,int y,int z,int d,int a,Material m,Color c){this.x=x;this.y=y;this.z=z;direction=d;axis=a;material=m;fastColour=c;}}
        private record View(double cx,double cy,double scale){}
        private static final double MAX_PITCH=Math.PI*.5-0.01,MIN_ZOOM=.08,MAX_ZOOM=16.0;
        private static final String[] TEXTURE_KEYS={"oak_log","oak_log_top","oak_leaves","birch_log","birch_log_top","birch_leaves","spruce_log","spruce_log_top","spruce_leaves","jungle_log","jungle_log_top","jungle_leaves","acacia_log","acacia_log_top","acacia_leaves","dark_oak_log","dark_oak_log_top","dark_oak_leaves","mangrove_log","mangrove_log_top","mangrove_leaves","mangrove_roots","mangrove_roots_top","cherry_log","cherry_log_top","cherry_leaves","pale_oak_log","pale_oak_log_top","pale_oak_leaves","azalea_leaves","flowering_azalea_leaves","poplar_log","poplar_log_top","red_poplar_leaves","orange_poplar_leaves","yellow_poplar_leaves","mushroom_stem","mushroom_block_inside","red_mushroom_block","brown_mushroom_block"};
        private static final int[][]DIRECTIONS={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};private static final double[]SHADES={.84,.70,.91,.76,1.08,.57};
        private static final double[][][]FACE_VERTICES={{{1,0,0},{1,1,0},{1,1,1},{1,0,1}},{{0,1,0},{0,0,0},{0,0,1},{0,1,1}},{{1,1,0},{0,1,0},{0,1,1},{1,1,1}},{{0,0,0},{1,0,0},{1,0,1},{0,0,1}},{{0,0,1},{1,0,1},{1,1,1},{0,1,1}},{{0,1,0},{1,1,0},{1,0,0},{0,0,0}}};
        private static final long serialVersionUID=1L;
    }

    static final class SchemWriter {
        static void write(GeneratedObject o,File f)throws IOException{Point3i d=o.dimensions;LinkedHashMap<Material,Integer> pal=new LinkedHashMap<>();pal.put(Material.AIR,0);for(Material m:o.blocks.values())pal.computeIfAbsent(m,k->pal.size());ByteArrayOutputStream data=new ByteArrayOutputStream();for(int z=0;z<d.z;z++)for(int y=0;y<d.y;y++)for(int x=0;x<d.x;x++)varint(pal.getOrDefault(o.getMaterial(x,y,z),0),data);Map<String,Tag> p=new LinkedHashMap<>();for(var e:pal.entrySet())p.put(state(e.getKey()),new IntTag(state(e.getKey()),e.getValue()));Map<String,Tag> tags=new LinkedHashMap<>();tags.put("Version",new IntTag("Version",2));tags.put("DataVersion",new IntTag("DataVersion",4550));tags.put("Width",new ShortTag("Width",(short)d.x));tags.put("Height",new ShortTag("Height",(short)d.z));tags.put("Length",new ShortTag("Length",(short)d.y));tags.put("Offset",new IntArrayTag("Offset",new int[]{o.offset.x,o.offset.z,o.offset.y}));tags.put("PaletteMax",new IntTag("PaletteMax",pal.size()));tags.put("Palette",new CompoundTag("Palette",p));tags.put("BlockData",new ByteArrayTag("BlockData",data.toByteArray()));tags.put("BlockEntities",new ListTag<>("BlockEntities",CompoundTag.class,emptyList()));tags.put("Metadata",new CompoundTag("Metadata",Map.of("Name",new StringTag("Name",o.name))));try(NBTOutputStream n=new NBTOutputStream(new GZIPOutputStream(new FileOutputStream(f)))){n.writeTag(new CompoundTag("Schematic",tags));}}
        static String state(Material m){StringBuilder q=new StringBuilder(m.name);Map<String,String> p=m.getProperties();if(p!=null&&!p.isEmpty()){q.append('[');boolean first=true;for(var e:new TreeMap<>(p).entrySet()){if(!first)q.append(',');first=false;q.append(e.getKey()).append('=').append(e.getValue());}q.append(']');}return q.toString();}static void varint(int v,OutputStream o)throws IOException{do{int b=v&127;v>>>=7;if(v!=0)b|=128;o.write(b);}while(v!=0);}
    }

    private Settings settings; private GeneratedObject object; private boolean loading; private javax.swing.Timer timer;
    private JList<Preset> speciesList; private TreePreviewPanel preview; private JLabel status; private JTabbedPane tabs;
    private NumericControl seedField,height,radius,branches,levels,lean,leanDir,forkCount,leafSize,rootCount,mushHeight,mushRadius,mushLean,mushLeanDir,capRadius,capThickness,capWaves;
    private NumericControl variation,bend,forkHeight,taper,branchStart,branchLength,density,leafRough,rootLength,rootThickness,rootAngle,rootDepth,boughLength,boughThickness,boughTaper,boughAngle,branchThickness,branchTaper,branchAngle,canopyWidth,canopyHeight,mushCurve,mushTaper,mushFlare,capHeight,capEdge,capBump,capDepression,capAsym,capOffset,capWaveStrength,capRoughness;
    private JComboBox<BranchPattern> branchPattern; private JComboBox<CrownForm> crownForm; private JComboBox<MushroomForm> mushroomForm; private JComboBox<TrunkBlock> trunkBlock; private JComboBox<Wood> wood; private JComboBox<Leaves> leaves;
    private static final long serialVersionUID=1L;
}
