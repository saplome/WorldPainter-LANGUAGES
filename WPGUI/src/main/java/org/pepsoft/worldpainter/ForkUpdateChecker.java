/*
 * This file is part of WorldPainter Languages, an unofficial localization
 * fork of WorldPainter (https://github.com/saplome/WorldPainter-LANGUAGES).
 *
 * Copyright © 2026 saplome.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 */
package org.pepsoft.worldpainter;

import com.fasterxml.jackson.databind.JsonNode;
import org.pepsoft.util.DesktopUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.CodeSource;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.pepsoft.util.ObjectMapperHolder.OBJECT_MAPPER;

/** Checks GitHub Releases for newer WorldPainter Languages builds. */
public final class ForkUpdateChecker {
    private ForkUpdateChecker() {
    }

    public static void checkAtStartup(Window parent) {
        final Configuration config = Configuration.getInstance();
        if ((! config.isCheckForUpdates())
                || "true".equalsIgnoreCase(System.getProperty("org.pepsoft.worldpainter.disableUpdateCheck"))) {
            return;
        }
        check(parent, false);
    }

    public static void checkManually(Window parent) {
        check(parent, true);
    }

    private static void check(Window parent, boolean manual) {
        final Thread thread = new Thread(() -> {
            try {
                final Release release = loadLatestRelease();
                // A null installed version would mean this build cannot compare releases at all. Staying quiet is then
                // the only safe reaction: the alternative offers an "update" on every single check.
                if ((INSTALLED_VERSION != null) && release.version.isNewerThan(INSTALLED_VERSION)) {
                    final Configuration config = Configuration.getInstance();
                    if (manual || (! release.tag.equals(config.getDismissedForkUpdateTag()))) {
                        SwingUtilities.invokeLater(() -> showUpdateAvailable(parent, release));
                    }
                } else if (manual) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent,
                            WPI18n.s("ui.update.upToDate.message"), WPI18n.s("ui.update.upToDate.title"),
                            JOptionPane.INFORMATION_MESSAGE));
                }
            } catch (Exception e) {
                logger.warn("Could not check WorldPainter Languages updates", e);
                if (manual) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent,
                            WPI18n.s("ui.update.failed.message"), WPI18n.s("ui.update.failed.title"),
                            JOptionPane.WARNING_MESSAGE));
                }
            }
        }, "WorldPainter Languages Update Checker");
        thread.setDaemon(true);
        thread.start();
    }

    private static Release loadLatestRelease() throws Exception {
        final HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "WorldPainter-Languages/" + CURRENT_PRODUCT_VERSION);
        try {
            final int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("GitHub returned HTTP " + responseCode);
            }
            try (InputStream in = connection.getInputStream()) {
                final JsonNode json = OBJECT_MAPPER.readTree(in);
                final String tag = json.path("tag_name").asText();
                final String pageUrl = json.path("html_url").asText();
                final ProductVersion version = ProductVersion.parse(tag);
                if ((version == null) || pageUrl.isEmpty()) {
                    throw new IllegalStateException("Unsupported GitHub release: " + tag);
                }
                return new Release(version, tag, pageUrl);
            }
        } finally {
            connection.disconnect();
        }
    }

    static File findUpdater() {
        if (! System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return null;
        }
        try {
            final CodeSource codeSource = ForkUpdateChecker.class.getProtectionDomain().getCodeSource();
            if ((codeSource == null) || (codeSource.getLocation() == null)) {
                return null;
            }
            File dir = new File(codeSource.getLocation().toURI()).getParentFile();
            for (int i = 0; (dir != null) && (i < 3); i++, dir = dir.getParentFile()) {
                final File candidate = new File(dir, UPDATER_EXECUTABLE);
                if (candidate.isFile()) {
                    return candidate;
                }
            }
        } catch (Exception e) {
            logger.debug("Could not locate " + UPDATER_EXECUTABLE, e);
        }
        return null;
    }

    private static void showUpdateAvailable(Window parent, Release release) {
        final File updater = findUpdater();
        final JLabel message = new JLabel(MessageFormat.format(
                WPI18n.s((updater != null) ? "ui.update.available.updaterMessage" : "ui.update.available.message"),
                CURRENT_PRODUCT_VERSION, release.displayVersion()));
        final JCheckBox dontRemind = new JCheckBox(WPI18n.s("ui.update.dontRemindThisVersion"));
        final JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.add(message, BorderLayout.CENTER);
        panel.add(dontRemind, BorderLayout.SOUTH);
        final Object[] options = (updater != null)
                ? new Object[] {WPI18n.s("ui.update.installNow"), WPI18n.s("ui.update.downloadNow"), WPI18n.s("ui.update.notNow")}
                : new Object[] {WPI18n.s("ui.update.downloadNow"), WPI18n.s("ui.update.notNow")};
        final int answer = JOptionPane.showOptionDialog(parent, panel, WPI18n.s("ui.update.available.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        if (dontRemind.isSelected()) {
            Configuration.getInstance().setDismissedForkUpdateTag(release.tag);
        }
        if (answer < 0) {
            return;
        }
        if (updater != null) {
            if (answer == 0) {
                installUpdate(parent, updater);
            } else if (answer == 1) {
                openReleasePage(parent, release);
            }
        } else if (answer == 0) {
            openReleasePage(parent, release);
        }
    }

    private static void installUpdate(Window parent, File updater) {
        // The updater replaces jars this JVM still has open, so it has to wait for this process to disappear first.
        final String[] command = {updater.getAbsolutePath(), "--wait-pid", Long.toString(ProcessHandle.current().pid())};
        final Thread hook = new Thread(() -> {
            try {
                new ProcessBuilder(command)
                        .directory(updater.getParentFile())
                        .start();
            } catch (Exception e) {
                logger.error("Could not start " + updater, e);
            }
        }, "WorldPainter Languages Updater Launcher");
        Runtime.getRuntime().addShutdownHook(hook);
        if (parent instanceof App) {
            ((App) parent).exit();
            Runtime.getRuntime().removeShutdownHook(hook);
        } else {
            System.exit(0);
        }
    }

    private static void openReleasePage(Window parent, Release release) {
        try {
            DesktopUtils.open(new URL(release.pageUrl));
        } catch (Exception e) {
            logger.error("Could not open release page " + release.pageUrl, e);
            JOptionPane.showMessageDialog(parent, WPI18n.s("ui.update.failed.message"),
                    WPI18n.s("ui.update.failed.title"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private static final class Release {
        private Release(ProductVersion version, String tag, String pageUrl) {
            this.version = version;
            this.tag = tag;
            this.pageUrl = pageUrl;
        }

        /** The tag as a plain version number, so that the dialog shows both sides in the same shape. */
        String displayVersion() {
            return tag.startsWith("v") ? tag.substring(1) : tag;
        }

        final ProductVersion version;
        final String tag, pageUrl;
    }

    /**
     * A release version in the fork's own scheme: the WorldPainter base version, then the fork revision, as in
     * {@code 2.27.1-L2.1.0}. Both halves count, base first, so a release that only moves the base forward
     * ({@code 2.28.0-L2.1.0}) is recognised as new just like one that only bumps the fork revision.
     */
    private static final class ProductVersion {
        private ProductVersion(int[] base, int[] fork) {
            this.base = base;
            this.fork = fork;
        }

        /** Parses a release tag or version number, with or without the leading {@code v}; {@code null} if it is neither. */
        static ProductVersion parse(String version) {
            if (version == null) {
                return null;
            }
            final Matcher matcher = PRODUCT_VERSION_PATTERN.matcher(version.trim());
            if (! matcher.matches()) {
                return null;
            }
            try {
                // A tag without a base version ("L2.1.1") says nothing about the base, so only the fork part decides.
                final String base = (matcher.group(1) != null) ? matcher.group(1) : baseVersion();
                return new ProductVersion(numbers(base), numbers(matcher.group(2)));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        boolean isNewerThan(ProductVersion other) {
            final int baseOrder = compare(base, other.base);
            return (baseOrder != 0) ? (baseOrder > 0) : (compare(fork, other.fork) > 0);
        }

        private static int[] numbers(String version) {
            final String[] parts = version.split("\\.");
            final int[] numbers = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                numbers[i] = Integer.parseInt(parts[i]);
            }
            return numbers;
        }

        /** Compares component by component; a missing component counts as zero, so 2.27 equals 2.27.0. */
        private static int compare(int[] left, int[] right) {
            for (int i = 0; i < Math.max(left.length, right.length); i++) {
                final int a = (i < left.length) ? left[i] : 0;
                final int b = (i < right.length) ? right[i] : 0;
                if (a != b) {
                    return Integer.compare(a, b);
                }
            }
            return 0;
        }

        private final int[] base, fork;
    }

    /** The WorldPainter version this build is based on, without a {@code -SNAPSHOT} or similar suffix. */
    private static String baseVersion() {
        final Matcher matcher = BASE_VERSION_PATTERN.matcher(Version.VERSION);
        return matcher.lookingAt() ? matcher.group(1) : "0";
    }

    private static final Pattern PRODUCT_VERSION_PATTERN
            = Pattern.compile("v?(?:(\\d+(?:\\.\\d+)*)-)?L(\\d+(?:\\.\\d+)*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BASE_VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)*)");
    private static final String UPDATER_EXECUTABLE = "WorldPainter-Update.exe";
    private static final String LATEST_RELEASE_API = "https://api.github.com/repos/saplome/WorldPainter-LANGUAGES/releases/latest";
    private static final Logger logger = LoggerFactory.getLogger(ForkUpdateChecker.class);
    /** Fork revision of this build; the base version comes from {@link Version#VERSION} and cannot fall out of sync. */
    public static final String CURRENT_FORK_VERSION = "2.1.0";
    /** Installed version in the shape of a release tag without the leading {@code v}: {@code 2.27.1-L2.1.0}. */
    public static final String CURRENT_PRODUCT_VERSION = baseVersion() + "-L" + CURRENT_FORK_VERSION;
    private static final ProductVersion INSTALLED_VERSION = ProductVersion.parse(CURRENT_PRODUCT_VERSION);
}
