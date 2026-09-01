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
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class WPUpdater {

    private static final String TMP_SUFFIX = ".wpupdate-tmp";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final int MAX_REDIRECTS = 5;
    private static final int UAC_DECLINED_EXIT_CODE = 1223; // ERROR_CANCELLED
    private static final String USER_AGENT = "WorldPainter-Languages-Updater/1.0";
    /** How long to wait for the application to close before replacing files it may still have open. */
    private static final long PROCESS_WAIT_TIMEOUT_MS = 30_000;
    private static final int MOVE_ATTEMPTS = 5;
    private static final long MOVE_RETRY_DELAY_MS = 400;
    private static final long MAX_LOG_BYTES = 256 * 1024;

    /** Everything {@link #log} printed, so that a failed run can be diagnosed after the console window is gone. */
    private static final List<String> transcript = Collections.synchronizedList(new ArrayList<>());
    /** Where to restart the application, once known; {@code null} when this run must not launch anything. */
    private static Path launchTarget;

    private WPUpdater() {
    }

    public static void main(String[] args) {
        // Starts at 2 so that even a failure inside the catch or finally block below is reported as an error.
        int exitCode = 2;
        try {
            exitCode = run(args);
        } catch (Throwable t) {
            log("FATAL: " + t);
            t.printStackTrace();
            exitCode = 2;
            // The user asked for an update from inside WorldPainter, which then closed itself. Whatever went wrong
            // here, leaving them with no application at all is the one outcome that is never acceptable: the
            // installation is untouched or fully replaced per file, so the old or new copy is always startable.
            launch("Restarting the application; the update did not go through");
        } finally {
            writeTranscript(exitCode);
        }
        System.exit(exitCode);
    }

    private static int run(String[] args) throws Exception {
        String manifestUrl = null;
        String rootOverride = null;
        String launchOverride = null;
        boolean checkOnly = false;
        boolean noLaunch = false;
        boolean alreadyElevated = false;
        long waitPid = -1;

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
                case "--wait-pid":
                    waitPid = Long.parseLong(requireValue(args, ++i, "--wait-pid"));
                    break;
                case "--check-only":
                    checkOnly = true;
                    break;
                case "--no-launch":
                    noLaunch = true;
                    break;
                case "--elevated":
                    alreadyElevated = true;
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

        // Resolved before the first download, so that the failure path in main() can still bring the application back.
        final String launch = (launchOverride != null) ? launchOverride : config.getProperty("launch");
        if ((! noLaunch) && (! checkOnly) && (launch != null) && (! launch.isBlank())) {
            launchTarget = root.resolve(launch).normalize();
        }

        final Manifest manifest = Manifest.parse(fetchText(manifestUrl));
        log("Remote version: " + ((manifest.version != null) ? manifest.version : "(not specified)"));

        final List<FileEntry> toUpdate = new ArrayList<>();
        final Path runningJar = locateJarFile();
        for (FileEntry entry: manifest.files) {
            final Path target = resolveSafe(root, entry.path);
            if ((runningJar != null) && Files.exists(target) && Files.isSameFile(target, runningJar)) {
                // Windows keeps the jar of the running updater open, so it can never be replaced from here. Release
                // builds give the jar a versioned name for exactly this reason; skip it if a manifest ever lists it.
                continue;
            }
            if ((! Files.isRegularFile(target))
                    || (Files.size(target) != entry.size)
                    || (! sha256(target).equalsIgnoreCase(entry.sha256))) {
                toUpdate.add(entry);
            }
        }
        // The .cfg files hold the class path of the launchers, so they must only start pointing at new jars once those
        // jars are actually on disk.
        toUpdate.sort(Comparator.comparing(entry -> entry.path.toLowerCase(Locale.ROOT).endsWith(".cfg")));
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
            // Everything below replaces files in place. WorldPainter starts this updater from a shutdown hook, so its
            // JVM may still be running - and Windows refuses to replace a jar that is still open.
            if (waitPid > 0) {
                awaitProcessExit(waitPid);
            }
            if (! isWritable(root)) {
                // A default installation lives under Program Files, where standard users only have read access. Do the
                // work in an elevated copy of this updater and keep the application itself at the normal integrity
                // level.
                if (alreadyElevated) {
                    log("ERROR: no write access to " + root + " even with administrator rights.");
                    return 2;
                }
                log("No write access to " + root + "; restarting the updater with administrator rights...");
                final int childExitCode = relaunchElevated(args);
                if (childExitCode == UAC_DECLINED_EXIT_CODE) {
                    log("ERROR: the administrator prompt was declined; nothing was updated.");
                    launch("Restarting the application; nothing was updated");
                    return 2;
                } else if (childExitCode != 0) {
                    log("ERROR: the elevated updater failed with exit code " + childExitCode + ".");
                    launch("Restarting the application; the update did not go through");
                    return 2;
                }
                log("Elevated update finished successfully.");
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
                    try {
                        Files.deleteIfExists(target);
                        log("  - deleted " + path);
                    } catch (IOException e) {
                        // Typically the jar of a previous updater, which this process still has open. Leaving a stale
                        // file behind is harmless; failing the whole update after the new files are already in place
                        // is not.
                        log("  - could not delete " + path + " (" + e.getMessage() + "); it will be removed later");
                    }
                }
                log("Update finished successfully"
                        + ((manifest.version != null) ? ("; now at version " + manifest.version) : "."));
            }
        }

        if (! checkOnly) {
            removeStaleUpdaterJars(root, manifest, runningJar);
        }

        launch(null);
        return 0;
    }

    /**
     * Waits for the process that asked for this update to disappear. Windows keeps an open jar locked, so replacing
     * WorldPainter's own jars while its JVM is still shutting down fails with an access error.
     */
    private static void awaitProcessExit(long pid) {
        final Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        if (handle.isEmpty() || (! handle.get().isAlive())) {
            return;
        }
        log("Waiting for the application to close (pid " + pid + ")...");
        try {
            handle.get().onExit().get(PROCESS_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            log("The application has closed.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Timeout, or the handle cannot be watched at all. Not fatal: the per-file retry below still gets a chance,
            // and a genuinely stuck process must not block the update forever.
            log("WARNING: the application is still running after " + (PROCESS_WAIT_TIMEOUT_MS / 1000)
                    + " s; continuing anyway.");
        }
    }

    /**
     * Starts the application again, if this run is supposed to. Used both after a successful update and on every
     * failure path, because WorldPainter has already closed itself by the time this updater runs.
     *
     * @param reason what to log before starting, or {@code null} for the normal post-update launch.
     */
    private static void launch(String reason) {
        final Path executable = launchTarget;
        if (executable == null) {
            return;
        }
        // Only ever launch once, however many failure paths are taken on the way out.
        launchTarget = null;
        if (! Files.exists(executable)) {
            log("WARNING: launch target not found: " + executable);
            return;
        }
        if (reason != null) {
            log(reason + ": " + executable);
        } else {
            log("Launching " + executable);
        }
        try {
            new ProcessBuilder(executable.toString())
                    .directory(executable.getParent().toFile())
                    .start();
        } catch (IOException e) {
            log("ERROR: could not start " + executable + " (" + e.getMessage() + ")");
        }
    }

    /**
     * Removes {@code wp-updater*.jar} files left over from earlier releases. The manifest cannot do this: the jar that
     * performs an update is always the one from the previous release, and Windows keeps it open, so the delete would
     * always fail. The next run starts from the new jar and can clean up the old one.
     */
    private static void removeStaleUpdaterJars(Path root, Manifest manifest, Path runningJar) {
        final Set<String> current = new HashSet<>();
        for (FileEntry entry: manifest.files) {
            current.add(entry.path);
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(root, "wp-updater*.jar")) {
            for (Path jar: jars) {
                final String name = jar.getFileName().toString();
                if (current.contains(name) || ((runningJar != null) && Files.isSameFile(jar, runningJar))) {
                    continue;
                }
                try {
                    Files.delete(jar);
                    log("  - removed the updater jar of a previous release: " + name);
                } catch (IOException e) {
                    // Either no write access or the file is still open; ten kilobytes can wait for the next run.
                    log("  - could not remove the old updater jar " + name + " (" + e.getMessage() + ")");
                }
            }
        } catch (IOException e) {
            log("WARNING: could not scan " + root + " for old updater jars: " + e.getMessage());
        }
    }

    private static boolean isWritable(Path directory) {
        Path probe = null;
        try {
            probe = Files.createTempFile(directory, ".wpupdate-probe", null);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException e) {
                    log("WARNING: could not remove the write probe " + probe + ": " + e);
                }
            }
        }
    }

    /**
     * Runs a second copy of this updater with administrator rights and waits for it. The elevated copy performs the
     * file replacement only; launching the application stays with this process so that WorldPainter does not end up
     * running elevated.
     *
     * @return the exit code of the elevated copy, or {@link #UAC_DECLINED_EXIT_CODE} if the prompt was declined.
     */
    private static int relaunchElevated(String[] args) throws IOException, InterruptedException {
        final List<String> childArguments = new ArrayList<>();
        final String executable = currentExecutable();
        if (executable == null) {
            throw new IOException("Could not determine the path of the running updater");
        }
        final String fileName = Path.of(executable).getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.equals("java.exe") || fileName.equals("javaw.exe")) {
            // Development or local test run: java -jar wp-updater-<version>.jar
            final Path jar = locateJarFile();
            if (jar == null) {
                throw new IOException("Could not determine the jar of the running updater");
            }
            childArguments.add("-jar");
            childArguments.add(jar.toString());
        }
        for (String argument: args) {
            if ((! argument.equals("--elevated")) && (! argument.equals("--no-launch"))) {
                childArguments.add(argument);
            }
        }
        childArguments.add("--elevated");
        childArguments.add("--no-launch");

        final StringBuilder command = new StringBuilder("$ErrorActionPreference='Stop'; try { $p = Start-Process -FilePath ");
        command.append(quoteForPowerShell(executable));
        command.append(" -ArgumentList ");
        for (int i = 0; i < childArguments.size(); i++) {
            if (i > 0) {
                command.append(',');
            }
            command.append(quoteForPowerShell(childArguments.get(i)));
        }
        command.append(" -Verb RunAs -Wait -PassThru; exit $p.ExitCode } catch { exit ")
                .append(UAC_DECLINED_EXIT_CODE)
                .append(" }");

        final Process process = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-Command", command.toString())
                .inheritIO()
                .start();
        return process.waitFor();
    }

    private static String currentExecutable() {
        return ProcessHandle.current().info().command().orElse(null);
    }

    private static String quoteForPowerShell(String value) {
        return "'" + value.replace("'", "''") + "'";
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
                moveWithRetry(tmp, target);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while replacing " + entry.path, e);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Replaces {@code target} with {@code tmp}, retrying while the file is still locked. Windows refuses to replace a
     * file another process has open, and the JVM that asked for the update may still be releasing its jars.
     */
    private static void moveWithRetry(Path tmp, Path target) throws IOException, InterruptedException {
        FileSystemException lastFailure = null;
        for (int attempt = 1; attempt <= MOVE_ATTEMPTS; attempt++) {
            try {
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (FileSystemException e) {
                lastFailure = e;
                if (attempt < MOVE_ATTEMPTS) {
                    final long delay = MOVE_RETRY_DELAY_MS * attempt;
                    log("    " + target.getFileName() + " is still in use; retrying in " + delay + " ms ("
                            + attempt + "/" + (MOVE_ATTEMPTS - 1) + ")");
                    Thread.sleep(delay);
                }
            }
        }
        throw lastFailure;
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

    /** The jar this updater is running from, or {@code null} when it runs from a directory of class files. */
    private static Path locateJarFile() {
        try {
            final CodeSource codeSource = WPUpdater.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                final Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
                if (Files.isRegularFile(location)) {
                    return location;
                }
            }
        } catch (Exception e) {
        }
        return null;
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
        transcript.add(message);
    }

    /**
     * Appends this run to {@code updater.log} in {@code %LOCALAPPDATA%/WorldPainter Languages}. The updater runs in a
     * console window that closes with the process, so without this a failed update leaves no trace to diagnose.
     */
    private static void writeTranscript(int exitCode) {
        final String localAppData = System.getenv("LOCALAPPDATA");
        final Path directory = ((localAppData != null) && (! localAppData.isBlank()))
                ? Path.of(localAppData).resolve("WorldPainter Languages")
                : Path.of(System.getProperty("java.io.tmpdir", "."));
        final Path file = directory.resolve("updater.log");
        try {
            Files.createDirectories(directory);
            if (Files.isRegularFile(file) && (Files.size(file) > MAX_LOG_BYTES)) {
                Files.delete(file);
            }
            final List<String> lines = new ArrayList<>();
            lines.add("=== " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now())
                    + "  pid " + ProcessHandle.current().pid() + "  exit code " + exitCode + " ===");
            synchronized (transcript) {
                lines.addAll(transcript);
            }
            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (exitCode == 2) {
                System.out.println("[updater] Details of this failed run were written to " + file);
            }
        } catch (Exception e) {
            System.out.println("[updater] WARNING: could not write " + file + ": " + e);
        }
    }

    private static void printUsage() {
        log("Usage: java -jar wp-updater-<version>.jar [--manifest <url>] [--root <dir>] [--launch <path>] [--wait-pid <pid>] [--check-only] [--no-launch] [--elevated]");
        log("Defaults are read from updater.properties next to the jar (keys: manifestUrl, root, launch).");
        log("--wait-pid waits for that process to exit before the first file is replaced.");
        log("--elevated marks an already elevated run and disables the automatic administrator restart.");
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
