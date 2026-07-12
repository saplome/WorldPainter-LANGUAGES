/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.threedeeview;

import org.pepsoft.minecraft.Direction;
import org.pepsoft.util.IconUtils;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.ProgressReceiver.OperationCancelled;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.WPI18n;
import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.threedeeview.Tile3DRenderer.LayerVisibilityMode;
import org.pepsoft.worldpainter.util.BetterAction;
import org.pepsoft.worldpainter.util.ImageUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Set;

import static org.pepsoft.util.GUIUtils.scaleToUI;
import static org.pepsoft.util.swing.MessageUtils.beepAndShowError;
import static org.pepsoft.worldpainter.App.INT_NUMBER_FORMAT;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
import static org.pepsoft.worldpainter.threedeeview.Tile3DRenderer.LayerVisibilityMode.*;
import static org.pepsoft.worldpainter.util.LayoutUtils.setDefaultSizeAndLocation;

/**
 *
 * @author pepijn
 */
public class ThreeDeeFrame extends JFrame implements WindowListener {
    public ThreeDeeFrame(Dimension dimension, ColourScheme colourScheme, CustomBiomeManager customBiomeManager, Point initialCoords) throws HeadlessException {
        super(WPI18n.s("ui.3d.title"));
        setIconImage(App.ICON);
        this.colourScheme = colourScheme;
        this.customBiomeManager = customBiomeManager;
        this.coords = initialCoords;
        
        scrollPane = new JScrollPane();
        
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                previousX = e.getX();
                previousY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - previousX;
                int dy = e.getY() - previousY;
                previousX = e.getX();
                previousY = e.getY();
                JScrollBar scrollBar = scrollPane.getHorizontalScrollBar();
                scrollBar.setValue(scrollBar.getValue() - dx);
                scrollBar = scrollPane.getVerticalScrollBar();
                scrollBar.setValue(scrollBar.getValue() - dy);
            }

            @Override public void mouseClicked(MouseEvent e) {}
            @Override public void mouseReleased(MouseEvent e) {}
            @Override public void mouseEntered(MouseEvent e) {}
            @Override public void mouseExited(MouseEvent e) {}
            @Override public void mouseMoved(MouseEvent e) {}
            
            private int previousX, previousY;
        };
        scrollPane.addMouseListener(mouseAdapter);
        scrollPane.addMouseMotionListener(mouseAdapter);
        scrollPane.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.getWheelRotation() < 0) {
                    if (zoom < MAX_ZOOM) {
                        ZOOM_IN_ACTION.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, null, e.getWhen(), e.getModifiers()));
                    }
                } else {
                    if (zoom > MIN_ZOOM) {
                        ZOOM_OUT_ACTION.actionPerformed(new ActionEvent(e.getSource(), ActionEvent.ACTION_PERFORMED, null, e.getWhen(), e.getModifiers()));
                    }
                }
            }
        });
        
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        
        alwaysOnTopButton.setToolTipText(org.pepsoft.worldpainter.WPI18n.s("ui.set.setTheDView"));
        alwaysOnTopButton.addActionListener(e -> {
            if (alwaysOnTopButton.isSelected()) {
                ThreeDeeFrame.this.setAlwaysOnTop(true);
            } else {
                ThreeDeeFrame.this.setAlwaysOnTop(false);
            }
        });
        
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.add(alwaysOnTopButton);
        toolBar.addSeparator();
        toolBar.add(ROTATE_LEFT_ACTION);
        toolBar.add(ROTATE_RIGHT_ACTION);
        toolBar.addSeparator();
        toolBar.add(ZOOM_OUT_ACTION);
        toolBar.add(RESET_ZOOM_ACTION);
        toolBar.add(ZOOM_IN_ACTION);
        toolBar.addSeparator();
        toolBar.add(EXPORT_IMAGE_ACTION);
        toolBar.addSeparator();
        toolBar.add(MOVE_TO_SPAWN_ACTION);
        toolBar.add(MOVE_TO_ORIGIN_ACTION);
        toolBar.addSeparator();
        toolBar.add(new JLabel(org.pepsoft.worldpainter.WPI18n.s("ui.42d6e016e6")));
        final JRadioButton radioButtonLayersNone = new JRadioButton(NO_LAYERS_ACTION);
        layerVisibilityButtonGroup.add(radioButtonLayersNone);
        toolBar.add(radioButtonLayersNone);
        final JRadioButton radioButtonLayersSync = new JRadioButton(SYNC_LAYERS_ACTION);
        layerVisibilityButtonGroup.add(radioButtonLayersSync);
        toolBar.add(radioButtonLayersSync);
        final JRadioButton radioButtonLayersAll = new JRadioButton(SURFACE_LAYERS_ACTION);
        layerVisibilityButtonGroup.add(radioButtonLayersAll);
        toolBar.add(radioButtonLayersAll);
        getContentPane().add(toolBar, BorderLayout.NORTH);

        glassPane = new GlassPane();
        setGlassPane(glassPane);
        getGlassPane().setVisible(true);
        
        ActionMap actionMap = rootPane.getActionMap();
        actionMap.put("rotateLeft", ROTATE_LEFT_ACTION);
        actionMap.put("rotateRight", ROTATE_RIGHT_ACTION);
        actionMap.put("zoomIn", ZOOM_IN_ACTION);
        actionMap.put("resetZoom", RESET_ZOOM_ACTION);
        actionMap.put("zoomOut", ZOOM_OUT_ACTION);

        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke('l'), "rotateLeft");
        inputMap.put(KeyStroke.getKeyStroke('r'), "rotateRight");
        inputMap.put(KeyStroke.getKeyStroke('-'), "zoomOut");
        inputMap.put(KeyStroke.getKeyStroke('0'), "resetZoom");
        inputMap.put(KeyStroke.getKeyStroke('+'), "zoomIn");
        
        setSize(800, 600);
        scaleToUI(this);
        setDefaultSizeAndLocation(this, 60);
        
        setDimension(dimension);
        
        addWindowListener(this);
    }

    public final Dimension getDimension() {
        return dimension;
    }

    public final void setDimension(Dimension dimension) {
        this.dimension = dimension;
        if (dimension != null) {
            threeDeeView = new ThreeDeeView(dimension, colourScheme, customBiomeManager, rotation, zoom);
            threeDeeView.setLayerVisibility(layerVisibility);
            threeDeeView.setHiddenLayers(hiddenLayers);
            scrollPane.setViewportView(threeDeeView);
            MOVE_TO_SPAWN_ACTION.setEnabled(dimension.getAnchor().equals((dimension.getWorld().getSpawnPointDimension() == null) ? NORMAL_DETAIL : dimension.getWorld().getSpawnPointDimension()));
            glassPane.setRotation(DIRECTIONS[rotation], dimension.getAnchor().invert);
        }
    }

    public void setHiddenLayers(Set<Layer> hiddenLayers) {
        this.hiddenLayers = hiddenLayers;
        if (threeDeeView != null) {
            threeDeeView.setHiddenLayers(hiddenLayers);
        }
    }

    public void resetAlwaysOnTop() {
        if (isAlwaysOnTop()) {
            setAlwaysOnTop(false);
            alwaysOnTopButton.setSelected(false);
        }
    }

    public void moveTo(Point coords) {
        this.coords = coords;
        threeDeeView.moveTo(coords.x, coords.y);
    }

    public void refresh(boolean clear) {
        if (threeDeeView != null) {
            threeDeeView.refresh(clear);
        }
    }

    private boolean imageFitsInJavaArray(Rectangle imageBounds) {
        final long area = (long) imageBounds.width * imageBounds.height;
        return (area >= 0L) && (area <= Integer.MAX_VALUE);
    }

    private void setLayerVisibility(LayerVisibilityMode layerVisibility) {
        this.layerVisibility = layerVisibility;
        if (threeDeeView != null) {
            threeDeeView.setLayerVisibility(layerVisibility);
        }
    }

    // WindowListener

    @Override
    public void windowOpened(WindowEvent e) {
        moveTo(coords);
    }

    @Override public void windowClosing(WindowEvent e) {}
    @Override public void windowClosed(WindowEvent e) {}
    @Override public void windowIconified(WindowEvent e) {}
    @Override public void windowDeiconified(WindowEvent e) {}
    @Override public void windowActivated(WindowEvent e) {}
    @Override public void windowDeactivated(WindowEvent e) {}
    
    private final Action ROTATE_LEFT_ACTION = new BetterAction("rotate3DViewLeft", WPI18n.s("ui.3d.rotateLeft"), ICON_ROTATE_LEFT) {
        {
            setShortDescription(WPI18n.s("ui.3d.rotateLeft.tooltip"));
        }
        
        @Override
        public void performAction(ActionEvent e) {
            rotation--;
            if (rotation < 0) {
                rotation = 3;
            }
            final Tile centreMostTile = threeDeeView.getCentreMostTile();
            if (centreMostTile != null) {
                threeDeeView = new ThreeDeeView(dimension, colourScheme, customBiomeManager, rotation, zoom);
                threeDeeView.setLayerVisibility(layerVisibility);
                threeDeeView.setHiddenLayers(hiddenLayers);
                scrollPane.setViewportView(threeDeeView);
//                scrollPane.getViewport().setViewPosition(new Point((threeDeeView.getWidth() - scrollPane.getWidth()) / 2, (threeDeeView.getHeight() - scrollPane.getHeight()) / 2));
                threeDeeView.moveToTile(centreMostTile);
                glassPane.setRotation(DIRECTIONS[rotation], dimension.getAnchor().invert);
            }
        }
        
        private static final long serialVersionUID = 1L;
    };
    
    private final Action ROTATE_RIGHT_ACTION = new BetterAction("rotate3DViewRight", WPI18n.s("ui.3d.rotateRight"), ICON_ROTATE_RIGHT) {
        {
            setShortDescription(WPI18n.s("ui.3d.rotateRight.tooltip"));
        }
        
        @Override
        public void performAction(ActionEvent e) {
            rotation++;
            if (rotation > 3) {
                rotation = 0;
            }
            final Tile centreMostTile = threeDeeView.getCentreMostTile();
            if (centreMostTile != null) {
                threeDeeView = new ThreeDeeView(dimension, colourScheme, customBiomeManager, rotation, zoom);
                threeDeeView.setLayerVisibility(layerVisibility);
                threeDeeView.setHiddenLayers(hiddenLayers);
                scrollPane.setViewportView(threeDeeView);
//                scrollPane.getViewport().setViewPosition(new Point((threeDeeView.getWidth() - scrollPane.getWidth()) / 2, (threeDeeView.getHeight() - scrollPane.getHeight()) / 2));
                threeDeeView.moveToTile(centreMostTile);
                glassPane.setRotation(DIRECTIONS[rotation], dimension.getAnchor().invert);
            }
        }
        
        private static final long serialVersionUID = 1L;
    };

    private final Action EXPORT_IMAGE_ACTION = new BetterAction("export3DViewImage", WPI18n.s("ui.3d.exportImage"), ICON_EXPORT_IMAGE) {
        {
            setShortDescription(WPI18n.s("ui.3d.exportImage.tooltip"));
        }
        
        @Override
        public void performAction(ActionEvent e) {
            final Rectangle imageBounds = threeDeeView.getImageBounds();
            if (! imageFitsInJavaArray(imageBounds)) {
                beepAndShowError(ThreeDeeFrame.this,
                        MessageFormat.format(WPI18n.s("ui.3d.imageTooLarge.message"), INT_NUMBER_FORMAT.format(Integer.MAX_VALUE)),
                        WPI18n.s("ui.3d.imageTooLarge.title"));
                return;
            }
            final String defaultname = dimension.getWorld().getName().replaceAll("\\s", "").toLowerCase() + ((dimension.getAnchor().dim == DIM_NORMAL) ? "" : ("_" + dimension.getName().toLowerCase())) + "_3d.png";
            File selectedFile = ImageUtils.selectImageForSave(ThreeDeeFrame.this, WPI18n.s("ui.imageFile"), new File(defaultname));
            if (selectedFile != null) {
                final String type;
                int p = selectedFile.getName().lastIndexOf('.');
                if (p != -1) {
                    type = selectedFile.getName().substring(p + 1).toUpperCase();
                } else {
                    type = "PNG";
                    selectedFile = new File(selectedFile.getParentFile(), selectedFile.getName() + ".png");
                }
                if (selectedFile.exists()) {
                    if (JOptionPane.showConfirmDialog(ThreeDeeFrame.this, org.pepsoft.worldpainter.WPI18n.s("ui.confirm.fileExistsOverwrite"), org.pepsoft.worldpainter.WPI18n.s("ui.dialog.overwriteFileConfirm.title"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                final File file = selectedFile;
                Boolean result = ProgressDialog.executeTask(ThreeDeeFrame.this, new ProgressTask<Boolean>() {
                        @Override
                        public String getName() {
                            return WPI18n.s("ui.3d.exportingImage");
                        }

                        @Override
                        public Boolean execute(ProgressReceiver progressReceiver) throws OperationCancelled {
                            try {
                                return ImageIO.write(threeDeeView.getImage(imageBounds, progressReceiver), type, file);
                            } catch (IOException e) {
                                throw new RuntimeException(WPI18n.s("ui.3d.exportImage.ioError"), e);
                            }
                        }
                    });
                if ((result != null) && result.equals(Boolean.FALSE)) {
                    JOptionPane.showMessageDialog(ThreeDeeFrame.this, org.pepsoft.worldpainter.WPI18n.s("ui.frag.formatPrefix") + type + org.pepsoft.worldpainter.WPI18n.s("ui.frag.notSupportedSuffix"), org.pepsoft.worldpainter.WPI18n.s("information"), JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }
        
        private static final long serialVersionUID = 1L;
    };
    
    private final Action MOVE_TO_SPAWN_ACTION = new BetterAction("move3DViewToSpawn", WPI18n.s("ui.3d.moveToSpawn"), ICON_MOVE_TO_SPAWN) {
        {
            setShortDescription(WPI18n.s("ui.3d.moveToSpawn.tooltip"));
        }
        
        @Override
        public void performAction(ActionEvent e) {
            if (dimension.getAnchor().dim == DIM_NORMAL) {
                Point spawn = dimension.getWorld().getSpawnPoint();
                threeDeeView.moveTo(spawn.x, spawn.y);
            }
        }
        
        private static final long serialVersionUID = 1L;
    };
    
    private final Action MOVE_TO_ORIGIN_ACTION = new BetterAction("move3DViewToOrigin", WPI18n.s("ui.3d.moveToOrigin"), ICON_MOVE_TO_ORIGIN) {
        {
            setShortDescription(WPI18n.s("ui.3d.moveToOrigin.tooltip"));
        }
        
        @Override
        public void performAction(ActionEvent e) {
            threeDeeView.moveTo(0, 0);
        }
        
        private static final long serialVersionUID = 1L;
    };
    
    private final Action ZOOM_IN_ACTION = new BetterAction("zoom3DViewIn", WPI18n.s("ui.3d.zoomIn"), ICON_ZOOM_IN) {
        {
            setShortDescription(WPI18n.s("ui.3d.zoomIn.tooltip"));
        }
        
        @Override
        public void performAction(ActionEvent e) {
            final Rectangle visibleRect = threeDeeView.getVisibleRect();
            zoom++;
            threeDeeView.setZoom(zoom);
            visibleRect.x *= 2;
            visibleRect.y *= 2;
            visibleRect.x += visibleRect.width / 2;
            visibleRect.y += visibleRect.height / 2;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    threeDeeView.scrollRectToVisible(visibleRect);
                }
            });
            if (zoom >= MAX_ZOOM) {
                setEnabled(false);
            }
            ZOOM_OUT_ACTION.setEnabled(true);
            RESET_ZOOM_ACTION.setEnabled(zoom != 1);
        }
        
        private static final long serialVersionUID = 1L;
    };
    
    private final Action RESET_ZOOM_ACTION = new BetterAction("reset3DViewZoom", WPI18n.s("ui.3d.resetZoom"), ICON_RESET_ZOOM) {
        {
            setShortDescription(WPI18n.s("ui.3d.resetZoom.tooltip"));
            setEnabled(false);
        }
        
        @Override
        public void performAction(ActionEvent e) {
            final Rectangle visibleRect = threeDeeView.getVisibleRect();
            if (zoom < 1) {
                while (zoom < 1) {
                    zoom++;
                    visibleRect.x *= 2;
                    visibleRect.y *= 2;
                    visibleRect.x += visibleRect.width / 2;
                    visibleRect.y += visibleRect.height / 2;
                }
                threeDeeView.setZoom(zoom);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        threeDeeView.scrollRectToVisible(visibleRect);
                    }
                });
            } else if (zoom > 1) {
                while (zoom > 1) {
                    zoom--;
                    visibleRect.x /= 2;
                    visibleRect.y /= 2;
                    visibleRect.x -= visibleRect.width / 4;
                    visibleRect.y -= visibleRect.height / 4;
                }
                threeDeeView.setZoom(zoom);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        threeDeeView.scrollRectToVisible(visibleRect);
                    }
                });
            }
            ZOOM_IN_ACTION.setEnabled(true);
            ZOOM_OUT_ACTION.setEnabled(true);
            setEnabled(false);
        }
        
        private static final long serialVersionUID = 1L;
    };
    
    private final Action ZOOM_OUT_ACTION = new BetterAction("zoom3DViewOut", WPI18n.s("ui.3d.zoomOut"), ICON_ZOOM_OUT) {
        {
            setShortDescription(WPI18n.s("ui.3d.zoomOut.tooltip"));
        }
        
        @Override
        public void performAction(ActionEvent e) {
            final Rectangle visibleRect = threeDeeView.getVisibleRect();
            zoom--;
            threeDeeView.setZoom(zoom);
            visibleRect.x /= 2;
            visibleRect.y /= 2;
            visibleRect.x -= visibleRect.width / 4;
            visibleRect.y -= visibleRect.height / 4;
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    threeDeeView.scrollRectToVisible(visibleRect);
                }
            });
            if (zoom <= MIN_ZOOM) {
                setEnabled(false);
            }
            ZOOM_IN_ACTION.setEnabled(true);
            RESET_ZOOM_ACTION.setEnabled(zoom != 1);
        }
        
        private static final long serialVersionUID = 1L;
    };

    private final Action NO_LAYERS_ACTION = new BetterAction("layerVisibilityNone", WPI18n.s("ui.3d.layerVisibility.none")) {
        {
            setShortDescription(WPI18n.s("ui.3d.layerVisibility.none.tooltip"));
        }

        @Override
        protected void performAction(ActionEvent e) {
            setLayerVisibility(NONE);
        }
    };

    private final Action SYNC_LAYERS_ACTION = new BetterAction("layerVisibilitySync", WPI18n.s("ui.3d.layerVisibility.sync")) {
        {
            setShortDescription(WPI18n.s("ui.3d.layerVisibility.sync.tooltip"));
        }

        @Override
        protected void performAction(ActionEvent e) {
            setLayerVisibility(SYNC);
        }
    };

    private final Action SURFACE_LAYERS_ACTION = new BetterAction("layerVisibilitySurface", WPI18n.s("ui.3d.layerVisibility.surface")) {
        {
            setShortDescription(WPI18n.s("ui.3d.layerVisibility.surface.tooltip"));
            setSelected(true);
        }

        @Override
        protected void performAction(ActionEvent e) {
            setLayerVisibility(SURFACE);
        }
    };

    private final JScrollPane scrollPane;
    private final GlassPane glassPane;
    private final CustomBiomeManager customBiomeManager;
    private final ButtonGroup layerVisibilityButtonGroup = new ButtonGroup();
    final JToggleButton alwaysOnTopButton = new JToggleButton(ICON_ALWAYS_ON_TOP);
    private Dimension dimension;
    private ThreeDeeView threeDeeView;
    private ColourScheme colourScheme;
    private int rotation = 3, zoom = 1;
    private Point coords;
    private LayerVisibilityMode layerVisibility = SURFACE;
    private Set<Layer> hiddenLayers;
    
    private static final Direction[] DIRECTIONS = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
    
    private static final Icon ICON_ROTATE_LEFT    = IconUtils.loadScaledIcon("org/pepsoft/worldpainter/icons/arrow_rotate_anticlockwise.png");
    private static final Icon ICON_ROTATE_RIGHT   = IconUtils.loadScaledIcon("org/pepsoft/worldpainter/icons/arrow_rotate_clockwise.png");
    private static final Icon ICON_EXPORT_IMAGE   = IconUtils.loadScaledIcon("org/pepsoft/worldpainter/icons/picture_save.png");
    private static final Icon ICON_MOVE_TO_SPAWN  = IconUtils.loadScaledIcon("org/pepsoft/worldpainter/icons/spawn_red.png");
    private static final Icon ICON_MOVE_TO_ORIGIN = IconUtils.loadScaledIcon("org/pepsoft/worldpainter/icons/arrow_in.png");
    private static final Icon ICON_ALWAYS_ON_TOP  = IconUtils.loadScaledIcon("org/pepsoft/worldpainter/icons/lock.png");
    private static final Icon ICON_ZOOM_IN        = IconUtils.loadScaledIcon("org/pepsoft/worldpainter/icons/magnifier_zoom_in.png");
    private static final Icon ICON_RESET_ZOOM     = IconUtils.loadScaledIcon("org/pepsoft/worldpainter/icons/magnifier.png");
    private static final Icon ICON_ZOOM_OUT       = IconUtils.loadScaledIcon("org/pepsoft/worldpainter/icons/magnifier_zoom_out.png");
    
    private static final int MIN_ZOOM = -2;
    private static final int MAX_ZOOM = 4;
    
    private static final long serialVersionUID = 1L;
}
