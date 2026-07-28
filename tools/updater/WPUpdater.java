/*
 * This file is part of WorldPainter Languages, an unofficial localization
 * fork of WorldPainter (https://github.com/saplome/WorldPainter-LANGUAGES).
 *
 * Copyright © 2026 saplome.
 *
 * This file is licensed under the GNU General Public License, version 3.
 * See the LICENSE file for details.
 */

package org.pepsoft.worldpainter.updater;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class WPUpdater {

    private static final String TMP_SUFFIX = ".wpupdate-tmp";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT = "WorldPainter-Languages-Updater/1.0";

    private WPUpdater() {
    }

    public static void main(String[] args) {
        int exitCode;
        try {
            exitCode = run(args);
        } catch (Throwable t) {
            log("FATAL: " + t);
            t.printStackTrace();
            exitCode = 2;
        }
        System.exit(exitCode);
    }

    private static int run(String[] args) throws Exception {
        String manifestUrl = null;
        String rootOverride = null;
        String launchOverride = null;
        boolean checkOnly = false;
        boolean noLaunch = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--manifest":
                    manifestUrl = requireValue(args, ++i, "--manifest");
                    break;
                case "--root":
                    rootOverride = requireValue(args, ++i, "--root");
                    break;
                case "--launch":
                    launchOverride = requireValue(args, ++i, "--launch");
                    break;
                case "--check-only":
                    checkOnly = true;
                    break;
                case "--no-launch":
                    noLaunch = true;
                    break;
                case "--help":
                case "-h":
                    printUsage();
                    return 0;
                default:
                    log("Unknown option: " + args[i]);
                    printUsage();
                    return 2;
            }
        }

        final Path jarDir = locateJarDir();
        final Properties config = loadConfig(jarDir);

        if (manifestUrl == null) {
            manifestUrl = config.getProperty("manifestUrl");
        }
        if (manifestUrl == null || manifestUrl.isBlank()) {
            log("No manifest URL. Pass --manifest <url> or set manifestUrl in updater.properties.");
            return 2;
        }

        Path root;
        if (rootOverride != null) {
            root = Path.of(rootOverride).toAbsolutePath().normalize();
        } else {
            final String configuredRoot = config.getProperty("root");
            root = ((configuredRoot != null) && (! configuredRoot.isBlank()))
                    ? jarDir.resolve(configuredRoot).normalize()
                    : jarDir;
        }
        if (! Files.isDirectory(root)) {
            log("Installation root does not exist: " + root);
            return 2;
        }

        log("Installation root: " + root);
        log("Manifest: " + manifestUrl);

        final Manifest manifest = Manifest.parse(fetchText(manifestUrl));
        log("Remote version: " + ((manifest.version != null) ? manifest.version : "(not specified)"));

        final List<FileEntry> toUpdate = new ArrayList<>();
        for (FileEntry entry: manifest.files) {
            final Path target = resolveSafe(root, entry.path);
            if ((! Files.isRegularFile(target))
                    || (Files.size(target) != entry.size)
                    || (! sha256(target).equalsIgnoreCase(entry.sha256))) {
                toUpdate.add(entry);
            }
        }
        final List<String> toDelete = new ArrayList<>();
        for (String path: manifest.deletes) {
            if (Files.isRegularFile(resolveSafe(root, path))) {
                toDelete.add(path);
            }
        }

        if (toUpdate.isEmpty() && toDelete.isEmpty()) {
            log("Everything is up to date (" + manifest.files.size() + " files checked).");
        } else if (checkOnly) {
            log("Updates available: " + toUpdate.size() + " file(s) to download, "
                    + toDelete.size() + " file(s) to delete.");
            for (FileEntry entry: toUpdate) {
                log("  ~ " + entry.path + " (" + entry.size + " bytes)");
            }
            for (String path: toDelete) {
                log("  - " + path);
            }
            return 1;
        } else {
            long totalBytes = 0L;
            for (FileEntry entry: toUpdate) {
                totalBytes += entry.size;
            }
            log("Updating " + toUpdate.size() + " file(s), " + totalBytes + " bytes total...");
            for (FileEntry entry: toUpdate) {
                updateFile(root, entry);
            }
            for (String path: toDelete) {
                final Path target = resolveSafe(root, path);
                Files.deleteIfExists(target);
                log("  - deleted " + path);
            }
            log("Update finished successfully"
                    + ((manifest.version != null) ? ("; now at version " + manifest.version) : "."));
        }

        String launch = (launchOverride != null) ? launchOverride : config.getProperty("launch");
        if ((! noLaunch) && (! checkOnly) && (launch != null) && (! launch.isBlank())) {
            final Path executable = root.resolve(launch).normalize();
            if (Files.exists(executable)) {
                log("Launching " + executable);
                new ProcessBuilder(executable.toString())
                        .directory(executable.getParent().toFile())
                        .start();
            } else {
                log("WARNING: launch target not found: " + executable);
            }
        }
        return 0;
    }

    private static void updateFile(Path root, FileEntry entry) throws IOException {
        final Path target = resolveSafe(root, entry.path);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        final Path tmp = target.resolveSibling(target.getFileName() + TMP_SUFFIX);
        try {
            log("  ~ downloading " + entry.path + " (" + entry.size + " bytes)");
            downloadTo(entry.url, tmp);
            final long actualSize = Files.size(tmp);
            if (actualSize != entry.size) {
                throw new IOException("Size mismatch for " + entry.path + ": expected "
                        + entry.size + " bytes, got " + actualSize + " bytes");
            }
            final String actualSha256 = sha256(tmp);
            if (! actualSha256.equalsIgnoreCase(entry.sha256)) {
                throw new IOException("SHA-256 mismatch for " + entry.path + ": expected "
                        + entry.sha256 + ", got " + actualSha256);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String fetchText(String url) throws IOException {
        final byte[] data;
        try (InputStream in = open(url)) {
            data = in.readAllBytes();
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static void downloadTo(String url, Path destination) throws IOException {
        try (InputStream in = open(url); OutputStream out = Files.newOutputStream(destination)) {
            in.transferTo(out);
        }
    }

    private static InputStream open(String url) throws IOException {
        String currentUrl = url;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            final URL parsedUrl;
            try {
                parsedUrl = new URI(currentUrl).toURL();
            } catch (Exception e) {
                throw new IOException("Invalid URL: " + currentUrl, e);
            }
            final URLConnection connection = parsedUrl.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (connection instanceof HttpURLConnection httpConnection) {
                httpConnection.setInstanceFollowRedirects(false);
                final int status = httpConnection.getResponseCode();
                if ((status >= 300) && (status < 400)) {
                    final String location = httpConnection.getHeaderField("Location");
                    if (location == null) {
                        throw new IOException("Redirect without Location header from " + currentUrl);
                    }
                    httpConnection.disconnect();
                    try {
                        currentUrl = parsedUrl.toURI().resolve(location).toString();
                    } catch (Exception e) {
                        throw new IOException("Invalid redirect from " + currentUrl + " to " + location, e);
                    }
                    continue;
                } else if (status != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP " + status + " from " + currentUrl);
                }
                return httpConnection.getInputStream();
            } else {
                return connection.getInputStream();
            }
        }
        throw new IOException("Too many redirects for " + url);
    }

    private static Path resolveSafe(Path root, String relativePath) throws IOException {
        if (relativePath.isBlank()) {
            throw new IOException("Empty path in manifest");
        }
        final Path candidate = Path.of(relativePath.replace('\\', '/'));
        if (candidate.isAbsolute()) {
            throw new IOException("Absolute path in manifest: " + relativePath);
        }
        final Path resolved = root.resolve(candidate).normalize();
        if (! resolved.startsWith(root)) {
            throw new IOException("Path in manifest escapes the installation root: " + relativePath);
        }
        return resolved;
    }

    private static String sha256(Path file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new IOException("SHA-256 not available", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            final byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        final StringBuilder sb = new StringBuilder(64);
        for (byte b: digest.digest()) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    private static Path locateJarDir() {
        try {
            final CodeSource codeSource = WPUpdater.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                final Path location = Path.of(codeSource.getLocation().toURI());
                return (Files.isDirectory(location) ? location : location.getParent()).toAbsolutePath().normalize();
            }
        } catch (Exception e) {
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static Properties loadConfig(Path jarDir) throws IOException {
        final Properties properties = new Properties();
        final Path configFile = jarDir.resolve("updater.properties");
        if (Files.isRegularFile(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                properties.load(in);
            }
        }
        return properties;
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static void log(String message) {
        System.out.println("[updater] " + message);
    }

    private static void printUsage() {
        log("Usage: java -jar wp-updater.jar [--manifest <url>] [--root <dir>] [--launch <path>] [--check-only] [--no-launch]");
        log("Defaults are read from updater.properties next to the jar (keys: manifestUrl, root, launch).");
    }

    private record FileEntry(String sha256, long size, String path, String url) {}

    private static final class Manifest {
        String version;
        final List<FileEntry> files = new ArrayList<>();
        final List<String> deletes = new ArrayList<>();

        static Manifest parse(String text) throws IOException {
            final Manifest manifest = new Manifest();
            boolean formatSeen = false;
            for (String rawLine: text.split("\\r?\\n")) {
                final String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                final int separatorOffset = line.indexOf('=');
                if (separatorOffset < 1) {
                    throw new IOException("Malformed manifest line: " + line);
                }
                final String key = line.substring(0, separatorOffset);
                final String value = line.substring(separatorOffset + 1);
                switch (key) {
                    case "format":
                        if (! "1".equals(value.strip())) {
                            throw new IOException("Unsupported manifest format: " + value
                                    + " (this updater supports format 1; please reinstall the application)");
                        }
                        formatSeen = true;
                        break;
                    case "version":
                        manifest.version = value.strip();
                        break;
                    case "file": {
                        final String[] fields = value.split("\\t");
                        if (fields.length != 4) {
                            throw new IOException("Malformed file entry (expected 4 TAB-separated fields): " + value);
                        }
                        final String sha256 = fields[0].strip().toLowerCase(Locale.ROOT);
                        if (! sha256.matches("[0-9a-f]{64}")) {
                            throw new IOException("Malformed SHA-256 hash: " + fields[0]);
                        }
                        final long size;
                        try {
                            size = Long.parseLong(fields[1].strip());
                        } catch (NumberFormatException e) {
                            throw new IOException("Malformed size: " + fields[1]);
                        }
                        manifest.files.add(new FileEntry(sha256, size, fields[2].strip(), fields[3].strip()));
                        break;
                    }
                    case "delete":
                        manifest.deletes.add(value.strip());
                        break;
                    default:
                        break;
                }
            }
            if (! formatSeen) {
                throw new IOException("Not a WorldPainter update manifest (missing format=1 line)");
            }
            return manifest;
        }
    }
}
