package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainterView;
import org.pepsoft.worldpainter.painting.DimensionPainter;
import org.pepsoft.worldpainter.painting.Paint;

import javax.swing.*;

/**
 * Created by pepijn on 14-5-15.
 */
public class SprayPaint extends AbstractPaintOperation {
    public SprayPaint(WorldPainterView view) {
        super("Spray Paint", "Spray paint any terrain, layer or biome onto the world", view, 100, "operation.sprayPaint");
    }

    @Override
    public JPanel getOptionsPanel() {
        return OPTIONS_PANEL;
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        final Dimension dimension = getDimension();
        if (dimension == null) {
            // Probably some kind of race condition
            return;
        }
        painter.setUndo(inverse);
        dimension.setEventsInhibited(true);
        try {
            painter.drawPoint(dimension, centreX, centreY, dynamicLevel);
        } finally {
            dimension.setEventsInhibited(false);
        }
    }

    @Override
    protected void paintChanged(Paint newPaint) {
        newPaint.setDither(true);
        painter.setPaint(newPaint);
    }

    private static final JPanel OPTIONS_PANEL = new StandardOptionsPanel(org.pepsoft.worldpainter.WPI18n.s("ui.help.tool.sprayPaint.title"), org.pepsoft.worldpainter.WPI18n.s("ui.help.tool.sprayPaint.body"));
    private final DimensionPainter painter = new DimensionPainter();
}