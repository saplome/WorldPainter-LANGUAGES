package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.Constants;

import javax.swing.*;
import java.awt.*;

/**
 * Created by Pepijn Schmitz on 21-09-16.
 */
public class BiomeAlgorithmListCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Integer) {
            switch ((Integer) value) {
                case Constants.BIOME_ALGORITHM_1_1:
                    setText("Minecraft 1.1");
                    break;
                case Constants.BIOME_ALGORITHM_1_2_AND_1_3_DEFAULT:
                    setText(org.pepsoft.worldpainter.WPI18n.s("ui.preset.minecraft16Default"));
                    break;
                case Constants.BIOME_ALGORITHM_1_3_LARGE:
                    setText(org.pepsoft.worldpainter.WPI18n.s("ui.preset.minecraft16LargeBiomes"));
                    break;
                case Constants.BIOME_ALGORITHM_1_7_DEFAULT:
                    setText(org.pepsoft.worldpainter.WPI18n.s("ui.preset.minecraft110Default"));
                    break;
                case Constants.BIOME_ALGORITHM_1_7_LARGE:
                    setText(org.pepsoft.worldpainter.WPI18n.s("ui.preset.minecraft110LargeBiomes"));
                    break;
            }
        }
        return this;
    }
}
