package org.pepsoft.worldpainter.util;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.awt.Window;
import java.io.File;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Direct Windows Vista+ Explorer file dialog implementation.
 *
 * <p>All Windows open, save, multi-select and folder operations go through the COM
 * {@code IFileOpenDialog}/{@code IFileSaveDialog} API. It deliberately does not use Swing's
 * {@code JFileChooser}, AWT's legacy dialog or SHBrowseForFolder, so every call site gets the same
 * current Windows Explorer UI, navigation pane, address bar, search and network locations.</p>
 */
final class WindowsExplorerFileDialog {
    private WindowsExplorerFileDialog() {}

    static File selectFile(Window parent, String title, File fileOrDirectory, FileFilter filter, boolean save) {
        final File[] selected = select(parent, title, fileOrDirectory, filter, false, save, false, null);
        return ((selected != null) && (selected.length == 1)) ? selected[0] : null;
    }

    static File[] selectFiles(Window parent, String title, File fileOrDirectory, FileFilter filter) {
        return select(parent, title, fileOrDirectory, filter, true, false, false, null);
    }

    static File selectDirectory(Window parent, String title, File directory, String selectionLabel) {
        final File[] selected = select(parent, title, directory, null, false, false, true, selectionLabel);
        return ((selected != null) && (selected.length == 1)) ? selected[0] : null;
    }

    private static File[] select(Window parent, String title, File fileOrDirectory, FileFilter filter,
                                 boolean multiple, boolean save, boolean directoriesOnly, String selectionLabel) {
        final Pointer owner = ownerPointer(parent);
        final FutureTask<File[]> task = new FutureTask<>(
                () -> show(owner, title, fileOrDirectory, filter, multiple, save, directoriesOnly, selectionLabel));
        final Thread thread = new Thread(task, "WorldPainter Windows Explorer file dialog");
        thread.setDaemon(false);
        thread.start();
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the Windows Explorer dialog", e);
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("Could not open the Windows Explorer dialog", cause);
        }
    }

    private static File[] show(Pointer owner, String title, File fileOrDirectory, FileFilter filter,
                               boolean multiple, boolean save, boolean directoriesOnly, String selectionLabel) {
        final int initResult = OLE32.CoInitializeEx(Pointer.NULL, COINIT_APARTMENTTHREADED);
        if (failed(initResult)) throw hresult("CoInitializeEx", initResult);
        Pointer dialog = null;
        try {
            final PointerByReference dialogRef = new PointerByReference();
            final Guid classId = save ? CLSID_FILE_SAVE_DIALOG : CLSID_FILE_OPEN_DIALOG;
            final Guid interfaceId = save ? IID_I_FILE_SAVE_DIALOG : IID_I_FILE_OPEN_DIALOG;
            int hr = OLE32.CoCreateInstance(classId, Pointer.NULL, CLSCTX_INPROC_SERVER, interfaceId, dialogRef);
            if (failed(hr)) throw hresult("CoCreateInstance(IFileDialog)", hr);
            dialog = dialogRef.getValue();

            final IntByReference options = new IntByReference();
            hr = invoke(dialog, I_FILE_DIALOG_GET_OPTIONS, options);
            if (failed(hr)) throw hresult("IFileDialog.GetOptions", hr);
            int requested = options.getValue() | FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST | FOS_NOCHANGEDIR;
            if (directoriesOnly) requested |= FOS_PICKFOLDERS;
            else if (save) requested |= FOS_OVERWRITEPROMPT | FOS_NOREADONLYRETURN;
            else requested |= FOS_FILEMUSTEXIST;
            if (multiple) requested |= FOS_ALLOWMULTISELECT;
            hr = invoke(dialog, I_FILE_DIALOG_SET_OPTIONS, requested);
            if (failed(hr)) throw hresult("IFileDialog.SetOptions", hr);

            if ((title != null) && (! title.isBlank())) {
                hr = invoke(dialog, I_FILE_DIALOG_SET_TITLE, new WString(title));
                if (failed(hr)) throw hresult("IFileDialog.SetTitle", hr);
            }
            if ((selectionLabel != null) && (! selectionLabel.isBlank())) {
                hr = invoke(dialog, I_FILE_DIALOG_SET_OK_BUTTON_LABEL, new WString(selectionLabel));
                if (failed(hr)) throw hresult("IFileDialog.SetOkButtonLabel", hr);
            }

            setInitialSelection(dialog, fileOrDirectory);
            final FilterMemory filters = directoriesOnly ? null : setFileTypes(dialog, filter);
            if (save) setDefaultExtension(dialog, filter);

            // Keep filter strings and native structures strongly reachable through Show().
            if (filters != null) filters.keepAlive();
            hr = invoke(dialog, I_MODAL_WINDOW_SHOW, (owner != null) ? owner : Pointer.NULL);
            Reference.reachabilityFence(filters);
            if (hr == HRESULT_CANCELLED) return null;
            if (failed(hr)) throw hresult("IFileDialog.Show", hr);

            if (multiple && (! save) && (! directoriesOnly)) {
                return getMultipleResults(dialog);
            }
            final File result = getSingleResult(dialog);
            return (result != null) ? new File[] {result} : null;
        } finally {
            release(dialog);
            OLE32.CoUninitialize();
        }
    }

    private static FilterMemory setFileTypes(Pointer dialog, FileFilter filter) {
        if (filter == null) return null;
        final String pattern = normalisePatterns(filter.getExtensions());
        if (pattern.isBlank()) return null;
        final ComDlgFilterSpec[] specs = (ComDlgFilterSpec[]) new ComDlgFilterSpec().toArray(2);
        specs[0].name = new WString(filter.getDescription());
        specs[0].spec = new WString(pattern);
        specs[0].write();
        specs[1].name = new WString(org.pepsoft.worldpainter.WPI18n.s("ui.filePicker.allFiles"));
        specs[1].spec = new WString("*.*");
        specs[1].write();
        int hr = invoke(dialog, I_FILE_DIALOG_SET_FILE_TYPES, specs.length, specs[0].getPointer());
        if (failed(hr)) throw hresult("IFileDialog.SetFileTypes", hr);
        hr = invoke(dialog, I_FILE_DIALOG_SET_FILE_TYPE_INDEX, 1);
        if (failed(hr)) throw hresult("IFileDialog.SetFileTypeIndex", hr);
        return new FilterMemory(specs);
    }

    private static void setDefaultExtension(Pointer dialog, FileFilter filter) {
        if (filter == null) return;
        for (String raw: filter.getExtensions().split(";")) {
            String extension = raw.trim().toLowerCase(Locale.ROOT);
            while (extension.startsWith("*")) extension = extension.substring(1);
            while (extension.startsWith(".")) extension = extension.substring(1);
            if ((! extension.isBlank()) && (! extension.contains("*")) && (! extension.contains("?"))) {
                final int hr = invoke(dialog, I_FILE_DIALOG_SET_DEFAULT_EXTENSION, new WString(extension));
                if (failed(hr)) throw hresult("IFileDialog.SetDefaultExtension", hr);
                return;
            }
        }
    }

    private static String normalisePatterns(String extensions) {
        if (extensions == null) return "";
        final List<String> patterns = new ArrayList<>();
        for (String raw: extensions.split(";")) {
            String value = raw.trim();
            if (value.isEmpty()) continue;
            if ((! value.contains("*")) && (! value.contains("?"))) {
                if (value.startsWith(".")) value = "*" + value;
                else if (! value.contains(".")) value = "*." + value;
            }
            patterns.add(value);
        }
        return String.join(";", patterns);
    }

    private static void setInitialSelection(Pointer dialog, File fileOrDirectory) {
        if (fileOrDirectory == null) return;
        final File absolute = fileOrDirectory.getAbsoluteFile();
        final File directory = absolute.isDirectory() ? absolute : absolute.getParentFile();
        if ((directory != null) && directory.isDirectory()) {
            final PointerByReference itemRef = new PointerByReference();
            final int hr = SHELL32.SHCreateItemFromParsingName(new WString(directory.getAbsolutePath()), Pointer.NULL,
                    IID_I_SHELL_ITEM, itemRef);
            if (! failed(hr)) {
                final Pointer item = itemRef.getValue();
                try {
                    invoke(dialog, I_FILE_DIALOG_SET_FOLDER, item);
                    invoke(dialog, I_FILE_DIALOG_SET_DEFAULT_FOLDER, item);
                } finally {
                    release(item);
                }
            }
        }
        if (! absolute.isDirectory()) {
            final int hr = invoke(dialog, I_FILE_DIALOG_SET_FILE_NAME, new WString(absolute.getName()));
            if (failed(hr)) throw hresult("IFileDialog.SetFileName", hr);
        }
    }

    private static File getSingleResult(Pointer dialog) {
        final PointerByReference itemRef = new PointerByReference();
        final int hr = invoke(dialog, I_FILE_DIALOG_GET_RESULT, itemRef);
        if (failed(hr)) throw hresult("IFileDialog.GetResult", hr);
        final Pointer item = itemRef.getValue();
        try {
            return fileFromShellItem(item);
        } finally {
            release(item);
        }
    }

    private static File[] getMultipleResults(Pointer dialog) {
        final PointerByReference arrayRef = new PointerByReference();
        int hr = invoke(dialog, I_FILE_OPEN_DIALOG_GET_RESULTS, arrayRef);
        if (failed(hr)) throw hresult("IFileOpenDialog.GetResults", hr);
        final Pointer array = arrayRef.getValue();
        try {
            final IntByReference countRef = new IntByReference();
            hr = invoke(array, I_SHELL_ITEM_ARRAY_GET_COUNT, countRef);
            if (failed(hr)) throw hresult("IShellItemArray.GetCount", hr);
            final List<File> files = new ArrayList<>(countRef.getValue());
            for (int i = 0; i < countRef.getValue(); i++) {
                final PointerByReference itemRef = new PointerByReference();
                hr = invoke(array, I_SHELL_ITEM_ARRAY_GET_ITEM_AT, i, itemRef);
                if (failed(hr)) throw hresult("IShellItemArray.GetItemAt", hr);
                final Pointer item = itemRef.getValue();
                try {
                    final File file = fileFromShellItem(item);
                    if (file != null) files.add(file);
                } finally {
                    release(item);
                }
            }
            return files.isEmpty() ? null : files.toArray(new File[0]);
        } finally {
            release(array);
        }
    }

    private static File fileFromShellItem(Pointer item) {
        if (item == null) return null;
        final PointerByReference pathRef = new PointerByReference();
        final int hr = invoke(item, I_SHELL_ITEM_GET_DISPLAY_NAME, SIGDN_FILESYSPATH, pathRef);
        if (failed(hr)) throw hresult("IShellItem.GetDisplayName", hr);
        final Pointer path = pathRef.getValue();
        try {
            final String value = (path != null) ? path.getWideString(0) : null;
            return ((value != null) && (! value.isBlank())) ? new File(value) : null;
        } finally {
            if (path != null) OLE32.CoTaskMemFree(path);
        }
    }

    private static Pointer ownerPointer(Window parent) {
        if ((parent == null) || (! parent.isDisplayable())) return Pointer.NULL;
        try {
            return Native.getComponentPointer(parent);
        } catch (RuntimeException e) {
            return Pointer.NULL;
        }
    }

    private static int invoke(Pointer interfacePointer, int methodIndex, Object... arguments) {
        if (interfacePointer == null) throw new IllegalStateException("Null COM interface pointer");
        final Pointer vtable = interfacePointer.getPointer(0);
        final Pointer method = vtable.getPointer((long) methodIndex * Native.POINTER_SIZE);
        final Function function = Function.getFunction(method, Function.ALT_CONVENTION);
        final Object[] parameters = new Object[arguments.length + 1];
        parameters[0] = interfacePointer;
        System.arraycopy(arguments, 0, parameters, 1, arguments.length);
        return function.invokeInt(parameters);
    }

    private static void release(Pointer interfacePointer) {
        if (interfacePointer != null) invoke(interfacePointer, I_UNKNOWN_RELEASE);
    }

    private static boolean failed(int hresult) { return hresult < 0; }

    private static IllegalStateException hresult(String operation, int hresult) {
        return new IllegalStateException(operation + " failed: 0x" + String.format("%08X", hresult));
    }

    public static final class Guid extends Structure {
        public int data1;
        public short data2;
        public short data3;
        public byte[] data4 = new byte[8];

        public Guid(String value) {
            final UUID uuid = UUID.fromString(value);
            final long most = uuid.getMostSignificantBits(), least = uuid.getLeastSignificantBits();
            data1 = (int) (most >>> 32);
            data2 = (short) (most >>> 16);
            data3 = (short) most;
            for (int i = 0; i < 8; i++) data4[i] = (byte) (least >>> (56 - i * 8));
        }

        @Override protected List<String> getFieldOrder() { return List.of("data1", "data2", "data3", "data4"); }
    }

    public static final class ComDlgFilterSpec extends Structure {
        public WString name;
        public WString spec;

        @Override protected List<String> getFieldOrder() { return List.of("name", "spec"); }
    }

    public interface Ole32 extends StdCallLibrary {
        int CoInitializeEx(Pointer reserved, int coInit);
        int CoCreateInstance(Guid classId, Pointer outer, int context, Guid interfaceId, PointerByReference result);
        void CoTaskMemFree(Pointer memory);
        void CoUninitialize();
    }

    public interface Shell32 extends StdCallLibrary {
        int SHCreateItemFromParsingName(WString path, Pointer bindContext, Guid interfaceId, PointerByReference result);
    }

    private static final class FilterMemory {
        private final ComDlgFilterSpec[] specs;
        private FilterMemory(ComDlgFilterSpec[] specs) { this.specs = specs; }
        private void keepAlive() { if (specs.length == 0) throw new AssertionError(); }
    }

    private static final Ole32 OLE32 = Native.load("Ole32", Ole32.class);
    private static final Shell32 SHELL32 = Native.load("Shell32", Shell32.class);
    private static final Guid CLSID_FILE_OPEN_DIALOG = new Guid("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7");
    private static final Guid IID_I_FILE_OPEN_DIALOG = new Guid("D57C7288-D4AD-4768-BE02-9D969532D960");
    private static final Guid CLSID_FILE_SAVE_DIALOG = new Guid("C0B4E2F3-BA21-4773-8DBA-335EC946EB8B");
    private static final Guid IID_I_FILE_SAVE_DIALOG = new Guid("84BCCD23-5FDE-4CDB-AEA4-AF64B83D78AB");
    private static final Guid IID_I_SHELL_ITEM = new Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE");

    private static final int COINIT_APARTMENTTHREADED = 0x2, CLSCTX_INPROC_SERVER = 0x1;
    private static final int FOS_OVERWRITEPROMPT = 0x2, FOS_NOCHANGEDIR = 0x8, FOS_PICKFOLDERS = 0x20,
            FOS_FORCEFILESYSTEM = 0x40, FOS_ALLOWMULTISELECT = 0x200, FOS_PATHMUSTEXIST = 0x800,
            FOS_FILEMUSTEXIST = 0x1000, FOS_NOREADONLYRETURN = 0x8000;
    private static final int SIGDN_FILESYSPATH = 0x80058000, HRESULT_CANCELLED = 0x800704C7;
    private static final int I_UNKNOWN_RELEASE = 2, I_MODAL_WINDOW_SHOW = 3,
            I_FILE_DIALOG_SET_FILE_TYPES = 4, I_FILE_DIALOG_SET_FILE_TYPE_INDEX = 5,
            I_FILE_DIALOG_SET_OPTIONS = 9, I_FILE_DIALOG_GET_OPTIONS = 10,
            I_FILE_DIALOG_SET_DEFAULT_FOLDER = 11, I_FILE_DIALOG_SET_FOLDER = 12,
            I_FILE_DIALOG_SET_FILE_NAME = 15, I_FILE_DIALOG_SET_TITLE = 17,
            I_FILE_DIALOG_SET_OK_BUTTON_LABEL = 18, I_FILE_DIALOG_GET_RESULT = 20,
            I_FILE_DIALOG_SET_DEFAULT_EXTENSION = 22, I_FILE_OPEN_DIALOG_GET_RESULTS = 27,
            I_SHELL_ITEM_GET_DISPLAY_NAME = 5, I_SHELL_ITEM_ARRAY_GET_COUNT = 7,
            I_SHELL_ITEM_ARRAY_GET_ITEM_AT = 8;
}
