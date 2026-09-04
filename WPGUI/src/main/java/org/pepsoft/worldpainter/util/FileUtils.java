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

package org.pepsoft.worldpainter.util;

import org.pepsoft.util.SystemUtils;
import org.pepsoft.worldpainter.ExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileView;
import java.awt.*;
import java.io.File;

import static java.awt.FileDialog.LOAD;
import static java.lang.Boolean.TRUE;
import static javax.swing.JFileChooser.APPROVE_OPTION;
import static javax.swing.JFileChooser.FILES_AND_DIRECTORIES;
import static org.pepsoft.worldpainter.ExceptionHandler.doWithoutExceptionReporting;

public class FileUtils {
    /**
     * Select a single existing file for loading.
     *
     * @param parent The window relative to which the modal file dialog should be displayed.
     * @param title The text for the title bar of the file dialog.
     * @param fileOrDir A file or directory to preselect.
     * @param fileFilter A filter limiting which files and/or directories can be selected.
     * @return The selected file, or {@code null} if the user cancelled the dialog.
     */
    public static File selectFileForOpen(Window parent, String title, File fileOrDir, final FileFilter fileFilter) {
        final Boolean old = UIManager.getBoolean("FileChooser.readOnly");
        UIManager.put("FileChooser.readOnly", TRUE);
        try {
            if (SystemUtils.isWindows()) {
                try {
                    return WindowsExplorerFileDialog.selectFile(parent, title, fileOrDir, fileFilter, false);
                } catch (RuntimeException e) {
                    logger.error("{} while using the Windows Explorer open dialog; no legacy chooser will be shown (message: \"{}\")",
                            e.getClass().getSimpleName(), e.getMessage(), e);
                    return null;
                }
            } else if (SystemUtils.isMac()) {
                try {
                    return selectFileForOpenFallback(parent, title, fileOrDir, fileFilter);
                } catch (RuntimeException e) {
                    logger.error("{} while using native FileDialog; falling back to JFileChooser (message: \"{}\")", e.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
            try {
                    final JFileChooser fileChooser;
                    if (fileOrDir != null) {
                        if (fileOrDir.isDirectory()) {
                            fileChooser = new JFileChooser(fileOrDir);
                        } else {
                            fileChooser = new JFileChooser(fileOrDir.getParentFile());
                            fileChooser.setSelectedFile(fileOrDir);
                        }
                    } else {
                        fileChooser = new JFileChooser();
                    }
                    fileChooser.setDialogTitle(title);
                    fileChooser.setFileFilter(fileFilter);
                    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    if (ExceptionHandler.doWithoutExceptionReporting(() -> fileChooser.showOpenDialog(parent)) == APPROVE_OPTION) {
                        return fileChooser.getSelectedFile();
                    } else {
                        return null;
                    }
                } catch (RuntimeException e) {
                    logger.error("{} while using JFileChooser; falling back to FileDialog (message: \"{}\")", e.getClass().getSimpleName(), e.getMessage(), e);
                    return selectFileForOpenFallback(parent, title, fileOrDir, fileFilter);
                }
        } finally {
            UIManager.put("FileChooser.readOnly", old);
        }
    }

    /**
     * Select a single existing directory for loading.
     *
     * @param parent The window relative to which the modal file dialog should be displayed.
     * @param title The text for the title bar of the file dialog.
     * @param dir A directory to preselect.
     * @param description A description of the type of directory to select.
     * @return The selected directory, or {@code null} if the user cancelled the dialog.
     */
    public static File selectDirectoryForOpen(Window parent, String title, File dir, String description, FileView fileView) {
        return selectDirectoryForOpen(parent, title, dir, description, fileView, null);
    }

    public static File selectDirectoryForOpen(Window parent, String title, File dir, String description, FileView fileView,
                                              FileFilter visibleFileFilter) {
        final Boolean old = UIManager.getBoolean("FileChooser.readOnly");
        UIManager.put("FileChooser.readOnly", TRUE);
        try {
            if (SystemUtils.isWindows()) {
                try {
                    return WindowsExplorerFileDialog.selectDirectory(parent, title, dir,
                            org.pepsoft.worldpainter.WPI18n.s("ui.filePicker.selectFolder"));
                } catch (RuntimeException e) {
                    logger.error("{} while using the Windows Explorer folder dialog; no legacy chooser will be shown (message: \"{}\")",
                            e.getClass().getSimpleName(), e.getMessage(), e);
                    return null;
                }
            }
            final JFileChooser fileChooser = (dir != null) ? new JFileChooser(dir) : new JFileChooser();
            fileChooser.setDialogTitle(title);
            fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override public boolean accept(File f) { return f.isDirectory(); }
                @Override public String getDescription() { return description; }
            });
            fileChooser.setFileSelectionMode(FILES_AND_DIRECTORIES);
            if (fileView != null) fileChooser.setFileView(fileView);
            return (ExceptionHandler.doWithoutExceptionReporting(() -> fileChooser.showOpenDialog(parent)) == APPROVE_OPTION)
                    ? fileChooser.getSelectedFile() : null;
        } finally {
            UIManager.put("FileChooser.readOnly", old);
        }
    }

    /**
     * Select one or more existing files for loading.
     *
     * @param parent The window relative to which the modal file dialog should be displayed.
     * @param title The text for the title bar of the file dialog.
     * @param fileOrDir A file or directory to preselect.
     * @param fileFilter A filter limiting which files and/or directories can be selected.
     * @return The selected file(s), or {@code null} if the user cancelled the dialog.
     */
    public static File[] selectFilesForOpen(Window parent, String title, File fileOrDir, final FileFilter fileFilter) {
        final Boolean old = UIManager.getBoolean("FileChooser.readOnly");
        UIManager.put("FileChooser.readOnly", TRUE);
        try {
            if (SystemUtils.isWindows()) {
                try {
                    return WindowsExplorerFileDialog.selectFiles(parent, title, fileOrDir, fileFilter);
                } catch (RuntimeException e) {
                    logger.error("{} while using the Windows Explorer multi-file dialog; no legacy chooser will be shown (message: \"{}\")",
                            e.getClass().getSimpleName(), e.getMessage(), e);
                    return null;
                }
            } else if (SystemUtils.isMac()) {
                try {
                    return selectFilesForOpenFallback(parent, title, fileOrDir, fileFilter);
                } catch (RuntimeException e) {
                    logger.error("{} while using native FileDialog; falling back to JFileChooser (message: \"{}\")", e.getClass().getSimpleName(), e.getMessage(), e);
                }
            }
            try {
                    final JFileChooser fileChooser;
                    if (fileOrDir != null) {
                        if (fileOrDir.isDirectory()) {
                            fileChooser = new JFileChooser(fileOrDir);
                        } else {
                            fileChooser = new JFileChooser(fileOrDir.getParentFile());
                            fileChooser.setSelectedFile(fileOrDir);
                        }
                    } else {
                        fileChooser = new JFileChooser();
                    }
                    fileChooser.setMultiSelectionEnabled(true);
                    fileChooser.setDialogTitle(title);
                    fileChooser.setFileFilter(fileFilter);
                    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    if (ExceptionHandler.doWithoutExceptionReporting(() -> fileChooser.showOpenDialog(parent)) == APPROVE_OPTION) {
                        return fileChooser.getSelectedFiles();
                    } else {
                        return null;
                    }
                } catch (RuntimeException e) {
                    logger.error("{} while using JFileChooser; falling back to FileDialog (message: \"{}\")", e.getClass().getSimpleName(), e.getMessage(), e);
                    return selectFilesForOpenFallback(parent, title, fileOrDir, fileFilter);
                }
        } finally {
            UIManager.put("FileChooser.readOnly", old);
        }
    }

    /** Select one or more files, or one directory, using Explorer-native dialogs on Windows. */
    public static File[] selectFilesOrDirectoriesForOpen(Window parent, String title, File fileOrDir, final FileFilter fileFilter) {
        if (SystemUtils.isWindows()) {
            final Object[] options = {
                    org.pepsoft.worldpainter.WPI18n.s("ui.filePicker.selectFiles"),
                    org.pepsoft.worldpainter.WPI18n.s("ui.filePicker.selectFolder"),
                    org.pepsoft.worldpainter.WPI18n.s("ui.button.cancel")
            };
            final int choice = JOptionPane.showOptionDialog(parent,
                    org.pepsoft.worldpainter.WPI18n.s("ui.filePicker.chooseSource"), title,
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == 0) return selectFilesForOpen(parent, title, fileOrDir, fileFilter);
            if (choice == 1) {
                final File directory = selectDirectoryForOpen(parent, title,
                        ((fileOrDir != null) && fileOrDir.isFile()) ? fileOrDir.getParentFile() : fileOrDir,
                        fileFilter.getDescription(), null, fileFilter);
                return (directory != null) ? new File[] {directory} : null;
            }
            return null;
        }
        final Boolean old = UIManager.getBoolean("FileChooser.readOnly");
        UIManager.put("FileChooser.readOnly", TRUE);
        try {
            final JFileChooser fileChooser = (fileOrDir != null) ? new JFileChooser(fileOrDir) : new JFileChooser();
            fileChooser.setMultiSelectionEnabled(true);
            fileChooser.setDialogTitle(title);
            fileChooser.setFileFilter(fileFilter);
            fileChooser.setFileSelectionMode(FILES_AND_DIRECTORIES);
            if (doWithoutExceptionReporting(() -> fileChooser.showOpenDialog(parent)) == APPROVE_OPTION) {
                final File[] files = fileChooser.getSelectedFiles();
                if ((files == null) || (files.length == 0)) {
                    final File file = fileChooser.getSelectedFile();
                    return (file != null) ? new File[] {file} : null;
                }
                return files;
            }
            return null;
        } finally { UIManager.put("FileChooser.readOnly", old); }
    }

    /**
     * Select a single filename for saving. May be the name of an existing file, or a non-existent file.
     *
     * @param parent The window relative to which the modal file dialog should be displayed.
     * @param title The text for the title bar of the file dialog.
     * @param fileOrDir An existing file or directory to preselect.
     * @param fileFilter A filter limiting which files and/or directories can be selected.
     * @return The selected file, or {@code null} if the user cancelled the dialog.
     */
    public static File selectFileForSave(Window parent, String title, File fileOrDir, final FileFilter fileFilter) {
        if (SystemUtils.isWindows()) {
            try {
                return WindowsExplorerFileDialog.selectFile(parent, title, fileOrDir, fileFilter, true);
            } catch (RuntimeException e) {
                logger.error("{} while using the Windows Explorer save dialog; no legacy chooser will be shown (message: \"{}\")",
                        e.getClass().getSimpleName(), e.getMessage(), e);
                return null;
            }
        } else if (SystemUtils.isMac()) {
            try {
                return selectFileForSaveFallback(parent, title, fileOrDir, fileFilter);
            } catch (RuntimeException e) {
                logger.error("{} while using native FileDialog; falling back to JFileChooser (message: \"{}\")", e.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        try {
                final JFileChooser fileChooser;
                if (fileOrDir != null) {
                    if (fileOrDir.isDirectory()) {
                        fileChooser = new JFileChooser(fileOrDir);
                    } else {
                        fileChooser = new JFileChooser(fileOrDir.getParentFile());
                        fileChooser.setSelectedFile(fileOrDir);
                    }
                } else {
                    fileChooser = new JFileChooser();
                }
                fileChooser.setDialogTitle(title);
                fileChooser.setFileFilter(fileFilter);
                fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                if (doWithoutExceptionReporting(() -> fileChooser.showSaveDialog(parent)) == APPROVE_OPTION) {
                    return fileChooser.getSelectedFile();
                } else {
                    return null;
                }
            } catch (RuntimeException e) {
                logger.error("{} while using JFileChooser; falling back to FileDialog (message: \"{}\")", e.getClass().getSimpleName(), e.getMessage(), e);
                return selectFileForSaveFallback(parent, title, fileOrDir, fileFilter);
            }
    }

    private static FileDialog createNativeFileDialog(Window parent, String title, int mode) {
        if (parent instanceof Frame) {
            return new FileDialog((Frame) parent, title, mode);
        } else if (parent instanceof Dialog) {
            return new FileDialog((Dialog) parent, title, mode);
        } else {
            return new FileDialog((Frame) null, title, mode);
        }
    }

    private static String getNativeFilePattern(FileFilter fileFilter) {
        final String[] patterns = fileFilter.getExtensions().split(";");
        for (int i = 0; i < patterns.length; i++) {
            final String pattern = patterns[i].trim();
            if ((! pattern.isEmpty()) && (pattern.indexOf('*') == -1) && (pattern.indexOf('?') == -1)
                    && (pattern.indexOf('.') == -1)) {
                patterns[i] = "*." + pattern;
            } else {
                patterns[i] = pattern;
            }
        }
        return String.join(";", patterns);
    }

    private static File selectFileForOpenFallback(Window parent, String title, File fileOrDir, final FileFilter fileFilter) {
        final FileDialog fileDialog = createNativeFileDialog(parent, title, LOAD);
        boolean fileSet = false;
        if (fileOrDir != null) {
            if (fileOrDir.isDirectory()) {
                fileDialog.setDirectory(fileOrDir.getPath());
            } else {
                fileDialog.setDirectory(fileOrDir.getParent());
                fileDialog.setFile(fileOrDir.getName());
                fileSet = true;
            }
        }
        if (fileFilter != null) {
            if (SystemUtils.isWindows() && (! fileSet)) {
                // Oracle Java does not implement setFilenameFilter() on Windows
                fileDialog.setFile(getNativeFilePattern(fileFilter));
            } else {
                fileDialog.setFilenameFilter((file, s) -> fileFilter.accept(new File(file, s)));
            }
        }
        fileDialog.setVisible(true);
        final File[] files = fileDialog.getFiles();
        if (files.length == 1) {
            return files[0];
        } else {
            return null;
        }
    }

    private static File[] selectFilesForOpenFallback(Window parent, String title, File fileOrDir, final FileFilter fileFilter) {
        final FileDialog fileDialog = createNativeFileDialog(parent, title, LOAD);
        fileDialog.setMultipleMode(true);
        boolean fileSet = false;
        if (fileOrDir != null) {
            if (fileOrDir.isDirectory()) {
                fileDialog.setDirectory(fileOrDir.getPath());
            } else {
                fileDialog.setDirectory(fileOrDir.getParent());
                fileDialog.setFile(fileOrDir.getName());
                fileSet = true;
            }
        }
        if (SystemUtils.isWindows() && (! fileSet)) {
            // Oracle Java does not implement setFilenameFilter() on Windows
            fileDialog.setFile(getNativeFilePattern(fileFilter));
        } else {
            fileDialog.setFilenameFilter((file, s) -> fileFilter.accept(new File(file, s)));
        }
        fileDialog.setVisible(true);
        final File[] files = fileDialog.getFiles();
        if (files.length > 0) {
            return files;
        } else {
            return null;
        }
    }

    private static File selectFileForSaveFallback(Window parent, String title, File fileOrDir, final FileFilter fileFilter) {
        final FileDialog fileDialog = createNativeFileDialog(parent, title, FileDialog.SAVE);
        boolean fileSet = false;
        if (fileOrDir != null) {
            if (fileOrDir.isDirectory()) {
                fileDialog.setDirectory(fileOrDir.getPath());
            } else {
                fileDialog.setDirectory(fileOrDir.getParent());
                fileDialog.setFile(fileOrDir.getName());
                fileSet = true;
            }
        }
        if (SystemUtils.isWindows() && (! fileSet)) {
            // Oracle Java does not implement setFilenameFilter() on Windows
            fileDialog.setFile(getNativeFilePattern(fileFilter));
        } else {
            fileDialog.setFilenameFilter((file, s) -> fileFilter.accept(new File(file, s)));
        }
        fileDialog.setVisible(true);
        final File[] files = fileDialog.getFiles();
        if (files.length == 1) {
            return files[0];
        } else {
            return null;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);
}
