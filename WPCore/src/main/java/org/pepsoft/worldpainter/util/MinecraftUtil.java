/*
 * This file is part of WorldPainter Languages, an unofficial localization
 * fork of WorldPainter (https://github.com/saplome/WorldPainter-LANGUAGES).
 *
 * Original work Copyright © pepsoft.org, The Netherlands.
 * Modifications Copyright © 2026 saplome. This file was modified in 2026.
 *
 * This file remains licensed under the GNU General Public License,
 * version 3. See the LICENSE file for details.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.util;

import org.pepsoft.util.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 *
 * @author pepijn
 */
public class MinecraftUtil {
    private MinecraftUtil() {
        // Prevent instantiation
    }
    
    public static File findMinecraftDir() {
        File candidate;
        String env = System.getProperty("org.pepsoft.worldpainter.minecraftDir");
        if (env != null) {
            candidate = new File(env);
            if (candidate.isDirectory()) {
                return candidate;
            } else {
                logger.error("Minecraft directory from system property does not exist: {}; continuing without Minecraft installation", env);
                return null;
            }
        }
        if (SystemUtils.isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                candidate = new File(appData, ".minecraft");
                if (candidate.isDirectory()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Using Minecraft installation in {}", candidate);
                    }
                    return candidate;
                }
            }
        } else if (SystemUtils.isMac()) {
            candidate = new File(System.getProperty("user.home"), "Library/Application Support/minecraft");
            if (candidate.isDirectory()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Using Minecraft installation in {}", candidate);
                }
                return candidate;
            }
        }
        candidate = new File(System.getProperty("user.home"), ".minecraft");
        if (candidate.isDirectory()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Using Minecraft installation in {}", candidate);
            }
            return candidate;
        }
        return null;
    }

    /**
     * Utility method for getting a Minecraft jar of the most recent supported version.
     */
    public static File findMinecraftJar(MinecraftJarProvider minecraftJarProvider) {
        for (int i = 10; i >= 1; i--) {
            File candidate = minecraftJarProvider.getMinecraftJar(i);
            if ((candidate != null) && candidate.isFile() && candidate.canRead()) {
                return candidate;
            }
        }
        File minecraftDir = findMinecraftDir();
        if (minecraftDir != null) {
            File candidate = new File(minecraftDir, "bin/minecraft.jar");
            if (candidate.isFile() && candidate.canRead()) {
                return candidate;
            }
        }
        return null;
    }

    public static String blocksToWalkingTime(int blocks) {
        int mins = Math.round(blocks / 256f);
        if (mins < 60) {
            return org.pepsoft.worldpainter.WPI18n.format("ui.time.minutes", mins);
        } else {
            int hours = mins / 60;
            mins -= 60 * hours;
            if (mins == 0) {
                return org.pepsoft.worldpainter.WPI18n.format("ui.time.hours", hours);
            } else {
                return org.pepsoft.worldpainter.WPI18n.format("ui.time.hoursMinutes", hours, mins);
            }
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(MinecraftUtil.class);
}