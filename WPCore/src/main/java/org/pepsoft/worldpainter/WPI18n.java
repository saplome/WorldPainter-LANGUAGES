/*
 * Static accessor for externalized UI strings in the
 * org.pepsoft.worldpainter.resources.* bundles (shipped by WPGUI):
 * strings (UI), blocks, gamedata (biomes/plants/terrain/colours),
 * layers (layers/operations/chunk stages) and swing. Keys are routed to
 * their bundle by prefix, with a fallback to the main strings bundle.
 *
 * Lives in WPCore so both WPCore and WPGUI can use it. Missing keys return
 * the key itself so the UI keeps working with an incomplete bundle.
 */
package org.pepsoft.worldpainter;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.pepsoft.worldpainter.Constants.DIM_END;
import static org.pepsoft.worldpainter.Constants.DIM_NETHER;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;

public final class WPI18n {
    private static final String BUNDLE_NAME = "org.pepsoft.worldpainter.resources.strings";
    private static final String BUNDLE_PACKAGE = "org.pepsoft.worldpainter.resources.";

    // Domain bundles split out of the main strings bundle. Keys are routed by
    // prefix; a key that is not found in its domain bundle is looked up in the
    // main strings bundle as well, so legacy keys that happen to share a
    // prefix (e.g. "layer.already.present") keep working.
    private static String bundleFor(String key) {
        if (key.startsWith("block.")) {
            return BUNDLE_PACKAGE + "blocks";
        }
        if (key.startsWith("biome.") || key.startsWith("plant.") || key.startsWith("terrain.") || key.startsWith("colour.")) {
            return BUNDLE_PACKAGE + "gamedata";
        }
        if (key.startsWith("layer.") || key.startsWith("operation.") || key.startsWith("chunkStage.") || key.startsWith("layerPreview.")) {
            return BUNDLE_PACKAGE + "layers";
        }
        if (key.startsWith("swing.")) {
            return BUNDLE_PACKAGE + "swing";
        }
        return BUNDLE_NAME;
    }

    private static String lookup(String key, Locale locale) {
        final String bundle = bundleFor(key);
        try {
            return ResourceBundle.getBundle(bundle, locale, Utf8Control.INSTANCE).getString(key);
        } catch (MissingResourceException e) {
            if (! bundle.equals(BUNDLE_NAME)) {
                return ResourceBundle.getBundle(BUNDLE_NAME, locale, Utf8Control.INSTANCE).getString(key);
            }
            throw e;
        }
    }

    private WPI18n() {
    }

    /**
     * Look up a UI string by key for the current default Locale.
     * ResourceBundle caches per (base name, locale), so this is cheap.
     *
     * @param key the externalized string key
     * @return the localized string, or the key itself if not found
     */
    // Cache of resolved (validated) strings, keyed by locale tag + key, so the
    // MessageFormat validation below runs at most once per string per language.
    private static final java.util.concurrent.ConcurrentHashMap<String, String> RESOLVE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.regex.Pattern ARG_INDEX = java.util.regex.Pattern.compile("\\{\\s*(\\d+)");

    public static String s(String key) {
        if (key == null) {
            return "";
        }
        final Locale locale = Locale.getDefault();
        final String cacheKey = locale.toLanguageTag() + '\u0000' + key;
        final String cached = RESOLVE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        String result;
        try {
            final String localized = lookup(key, locale);
            result = resolveSafe(key, localized);
        } catch (MissingResourceException e) {
            result = key;
        }
        RESOLVE_CACHE.put(cacheKey, result);
        return result;
    }

    /**
     * If the English base is a MessageFormat pattern but the localized value
     * is invalid or lost its "{N}" placeholders, fall back to English.
     */
    private static String resolveSafe(String key, String localized) {
        if (localized == null) {
            return key;
        }
        final String english;
        try {
            english = lookup(key, Locale.ROOT);
        } catch (MissingResourceException e) {
            return localized; // no English reference to compare against
        }
        // If the English base is not a MessageFormat pattern, there is nothing
        // structural to protect; trust the translation as-is.
        if (english.indexOf('{') < 0) {
            return localized;
        }
        if (isPatternSafe(localized) && argIndices(localized).equals(argIndices(english))) {
            return localized;
        }
        return english;
    }

    private static boolean isPatternSafe(String pattern) {
        if (pattern.indexOf('{') < 0) {
            return true;
        }
        try {
            new MessageFormat(pattern);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static java.util.Set<String> argIndices(String pattern) {
        final java.util.Set<String> indices = new java.util.HashSet<>();
        final java.util.regex.Matcher m = ARG_INDEX.matcher(pattern);
        while (m.find()) {
            indices.add(m.group(1));
        }
        return indices;
    }

    /**
     * Persist the desired language ("en" or "ru") and update the running JVM's
     * default Locale. A restart is recommended for the change to fully apply to
     * every already-built window.
     *
     * @param lang "ru" for Russian, anything else for English
     */
    public static String biome(String englishName) {
        if (englishName == null) {
            return null;
        }
        final String key = "biome." + englishName.replaceAll("[^A-Za-z0-9]+", "_");
        final String v = s(key);
        return v.equals(key) ? englishName : v;
    }

    public static String plant(String englishName) {
        if (englishName == null) {
            return null;
        }
        final String key = "plant." + englishName.replaceAll("[^A-Za-z0-9]+", "_");
        final String v = s(key);
        return v.equals(key) ? englishName : v;
    }

    /**
     * Localised display name for a Minecraft block. Accepts either a modern
     * simple name ({@code grass_block}) or a legacy English label ({@code Grass}).
     */
    public static String block(String name) {
        if (name == null) {
            return null;
        }
        final String key = "block." + name.replaceAll("[^A-Za-z0-9]+", "_");
        final String v = s(key);
        return v.equals(key) ? name : v;
    }

    /**
     * Localised display name for a {@link Terrain} type.
     * Custom terrain slots keep their user-defined names.
     */
    public static String terrainName(Terrain terrain) {
        if (terrain == null) {
            return "";
        }
        if (terrain.isCustom()) {
            return terrain.getName();
        }
        final String key = "terrain." + terrain.name();
        final String v = s(key);
        return v.equals(key) ? terrain.getName() : v;
    }

    /**
     * Localised description for a {@link Terrain} type.
     */
    public static String terrainDescription(Terrain terrain) {
        if (terrain == null) {
            return "";
        }
        if (terrain.isCustom()) {
            return terrain.getDescription();
        }
        final String key = "terrain." + terrain.name() + ".desc";
        final String v = s(key);
        return v.equals(key) ? terrain.getDescription() : v;
    }

    public static String terrainTooltip(Terrain terrain) {
        return terrainName(terrain) + ": " + terrainDescription(terrain);
    }

    public static String colour(String englishName) {
        if (englishName == null) {
            return null;
        }
        final String key = "colour." + englishName.replaceAll("[^A-Za-z0-9]+", "_");
        final String v = s(key);
        return v.equals(key) ? englishName : v;
    }

    public static String dimensionName(Dimension dimension) {
        if (dimension == null) {
            return "";
        }
        final Dimension.Anchor anchor = dimension.getAnchor();
        final String name = dimension.getName();
        return ((anchor != null) && ((name == null) || name.equals(anchor.getDefaultName())))
                ? dimensionName(anchor)
                : name;
    }

    public static String dimensionName(Dimension.Anchor anchor) {
        final StringBuilder sb = new StringBuilder();
        switch (anchor.dim) {
            case DIM_NORMAL:
                sb.append(s("ui.dimension.surface"));
                break;
            case DIM_NETHER:
                sb.append(s("ui.dimension.nether"));
                break;
            case DIM_END:
                sb.append(s("ui.dimension.end"));
                break;
            default:
                sb.append(MessageFormat.format(s("ui.dimension.generic"), anchor.dim));
                break;
        }
        switch (anchor.role) {
            case MASTER:
                sb.append(s("ui.dimension.masterSuffix"));
                break;
            case CAVE_FLOOR:
                sb.append(s("ui.dimension.caveFloorSuffix"));
                break;
            case FLOATING_FLOOR:
                sb.append(s("ui.dimension.floatingFloorSuffix"));
                break;
        }
        if (anchor.invert) {
            sb.append(s("ui.dimension.ceilingSuffix"));
        }
        if (anchor.id != 0) {
            sb.append(' ').append(anchor.id);
        }
        return sb.toString();
    }

    /**
     * Short dimension type name for dialog titles (e.g. "Surface", "Nether").
     */
    public static String dimensionShortName(int dim) {
        switch (dim) {
            case DIM_NORMAL:
                return s("ui.dimension.short.surface");
            case DIM_NETHER:
                return s("ui.dimension.short.nether");
            case DIM_END:
                return s("ui.dimension.short.end");
            default:
                return Integer.toString(dim);
        }
    }

    public static String dimensionShortName(Dimension.Anchor anchor) {
        return dimensionShortName(anchor.dim);
    }

    /**
     * Format a localized string with MessageFormat. Never throws.
     */
    public static String format(String key, Object... args) {
        try {
            return MessageFormat.format(s(key), args);
        } catch (RuntimeException e) {
            return s(key);
        }
    }

    private static final String[][] SWING_UI_KEYS = {
            {"FileChooser.lookInLabelText", "swing.fileChooser.lookInLabel"},
            {"FileChooser.saveInLabelText", "swing.fileChooser.saveInLabel"},
            {"FileChooser.fileNameLabelText", "swing.fileChooser.fileNameLabel"},
            {"FileChooser.filesOfTypeLabelText", "swing.fileChooser.filesOfTypeLabel"},
            {"FileChooser.upFolderToolTipText", "swing.fileChooser.upFolderToolTip"},
            {"FileChooser.homeFolderToolTipText", "swing.fileChooser.homeFolderToolTip"},
            {"FileChooser.newFolderToolTipText", "swing.fileChooser.newFolderToolTip"},
            {"FileChooser.listViewButtonToolTipText", "swing.fileChooser.listViewToolTip"},
            {"FileChooser.detailsViewButtonToolTipText", "swing.fileChooser.detailsViewToolTip"},
            {"FileChooser.viewMenuButtonToolTipText", "swing.fileChooser.viewMenuToolTip"},
            {"FileChooser.viewMenuButtonAccessibleName", "swing.fileChooser.viewMenuAccessibleName"},
            {"FileChooser.fileNameHeaderText", "swing.fileChooser.fileNameHeader"},
            {"FileChooser.fileSizeHeaderText", "swing.fileChooser.fileSizeHeader"},
            {"FileChooser.fileTypeHeaderText", "swing.fileChooser.fileTypeHeader"},
            {"FileChooser.fileDateHeaderText", "swing.fileChooser.fileDateHeader"},
            {"FileChooser.fileAttrHeaderText", "swing.fileChooser.fileAttrHeader"},
            {"FileChooser.openButtonText", "swing.fileChooser.openButton"},
            {"FileChooser.openButtonToolTipText", "swing.fileChooser.openButtonToolTip"},
            {"FileChooser.cancelButtonText", "swing.fileChooser.cancelButton"},
            {"FileChooser.cancelButtonToolTipText", "swing.fileChooser.cancelButtonToolTip"},
            {"FileChooser.saveButtonText", "swing.fileChooser.saveButton"},
            {"FileChooser.saveButtonToolTipText", "swing.fileChooser.saveButtonToolTip"},
            {"FileChooser.updateButtonText", "swing.fileChooser.updateButton"},
            {"FileChooser.helpButtonText", "swing.fileChooser.helpButton"},
            {"FileChooser.directoryOpenButtonText", "swing.fileChooser.directoryOpenButton"},
            {"FileChooser.directoryOpenButtonToolTipText", "swing.fileChooser.directoryOpenButtonToolTip"},
            {"FileChooser.openDialogTitleText", "swing.fileChooser.openDialogTitle"},
            {"FileChooser.saveDialogTitleText", "swing.fileChooser.saveDialogTitle"},
            {"FileChooser.acceptAllFileFilterText", "swing.fileChooser.acceptAllFileFilter"},
            {"FileChooser.newFolderButtonText", "swing.fileChooser.newFolderButton"},
            {"FileChooser.renameFileButtonText", "swing.fileChooser.renameFileButton"},
            {"FileChooser.deleteFileButtonText", "swing.fileChooser.deleteFileButton"},
            {"FileChooser.filterLabelText", "swing.fileChooser.filterLabel"},
            {"FileChooser.foldersLabelText", "swing.fileChooser.foldersLabel"},
            {"FileChooser.filesLabelText", "swing.fileChooser.filesLabel"},
            {"FileChooser.pathLabelText", "swing.fileChooser.pathLabel"},
            {"FileChooser.newFolderErrorText", "swing.fileChooser.newFolderError"},
            {"FileChooser.other.newFolder", "swing.fileChooser.newFolder"},
            {"FileChooser.win32.newFolder", "swing.fileChooser.newFolder"},
            {"ColorChooser.okText", "swing.colorChooser.ok"},
            {"ColorChooser.cancelText", "swing.colorChooser.cancel"},
            {"ColorChooser.resetText", "swing.colorChooser.reset"},
            {"ColorChooser.previewText", "swing.colorChooser.preview"},
            {"ColorChooser.sampleText", "swing.colorChooser.sampleText"},
            {"ColorChooser.swatchesNameText", "swing.colorChooser.swatchesName"},
            {"ColorChooser.swatchesRecentText", "swing.colorChooser.swatchesRecent"},
            {"ColorChooser.rgbNameText", "swing.colorChooser.rgbName"},
            {"ColorChooser.hsvNameText", "swing.colorChooser.hsvName"},
            {"ColorChooser.hslNameText", "swing.colorChooser.hslName"},
            {"ColorChooser.cmykNameText", "swing.colorChooser.cmykName"},
            {"ColorChooser.rgbRedText", "swing.colorChooser.rgbRed"},
            {"ColorChooser.rgbGreenText", "swing.colorChooser.rgbGreen"},
            {"ColorChooser.rgbBlueText", "swing.colorChooser.rgbBlue"},
            {"ColorChooser.rgbAlphaText", "swing.colorChooser.rgbAlpha"},
            {"ColorChooser.rgbHexCodeText", "swing.colorChooser.rgbHexCode"},
            {"ColorChooser.hsvHueText", "swing.colorChooser.hsvHue"},
            {"ColorChooser.hsvSaturationText", "swing.colorChooser.hsvSaturation"},
            {"ColorChooser.hsvValueText", "swing.colorChooser.hsvValue"},
            {"ColorChooser.hsvTransparencyText", "swing.colorChooser.hsvTransparency"},
            {"ColorChooser.hslHueText", "swing.colorChooser.hslHue"},
            {"ColorChooser.hslSaturationText", "swing.colorChooser.hslSaturation"},
            {"ColorChooser.hslLightnessText", "swing.colorChooser.hslLightness"},
            {"ColorChooser.hslTransparencyText", "swing.colorChooser.hslTransparency"},
            {"ColorChooser.cmykCyanText", "swing.colorChooser.cmykCyan"},
            {"ColorChooser.cmykMagentaText", "swing.colorChooser.cmykMagenta"},
            {"ColorChooser.cmykYellowText", "swing.colorChooser.cmykYellow"},
            {"ColorChooser.cmykBlackText", "swing.colorChooser.cmykBlack"},
            {"ColorChooser.cmykAlphaText", "swing.colorChooser.cmykAlpha"},
            {"OptionPane.yesButtonText", "swing.optionPane.yes"},
            {"OptionPane.noButtonText", "swing.optionPane.no"},
            {"OptionPane.okButtonText", "swing.optionPane.ok"},
            {"OptionPane.cancelButtonText", "swing.optionPane.cancel"},
    };

    /**
     * Install translations for standard Swing dialogs (file chooser, colour
     * chooser, option pane). Call once at startup after the look-and-feel has
     * been set, when a non-English locale is active.
     */
    public static void installSwingDialogDefaults() {
        for (String[] pair : SWING_UI_KEYS) {
            final String value = s(pair[1]);
            if (! value.equals(pair[1])) {
                javax.swing.UIManager.put(pair[0], value);
            }
        }
    }

    public static void setLanguage(String lang) {
        String code = normalizeLang(lang);
        try {
            java.util.prefs.Preferences.userRoot().node("org/pepsoft/worldpainter")
                    .put("language", code);
        } catch (Exception e) {
            // ignore; language simply won't persist
        }
        Locale.setDefault("en".equals(code) ? Locale.US : new Locale(code));
        RESOLVE_CACHE.clear();
    }

    // Normalize a language code against the data-driven language list
    // (languages.list next to the string bundles). Unknown codes fall back to English.
    public static String normalizeLang(String lang) {
        if (lang == null) {
            return "en";
        }
        String code = lang.trim().toLowerCase(java.util.Locale.ROOT);
        return getAvailableLanguages().containsKey(code) ? code : "en";
    }

    /**
     * Available UI languages as an ordered map of code -> display name, read from
     * the resource file languages.list (which lives next to the string bundles).
     * To add a language, drop the five localized bundle files
     * (strings/blocks/gamedata/layers/swing)_<code>.properties into that folder
     * and add a "<code> = <display name>" line to languages.list -- no code
     * changes required. English ("en") is always available as a fallback.
     */
    public static java.util.LinkedHashMap<String, String> getAvailableLanguages() {
        java.util.LinkedHashMap<String, String> langs = new java.util.LinkedHashMap<>();
        try (java.io.InputStream in = WPI18n.class.getResourceAsStream("/org/pepsoft/worldpainter/resources/languages.list")) {
            if (in != null) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String code = trimmed.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
                    String name = trimmed.substring(eq + 1).trim();
                    if (! code.isEmpty() && ! name.isEmpty()) {
                        langs.put(code, name);
                    }
                }
            }
        } catch (Exception e) {
            // ignore; fall back to the built-in defaults below
        }
        if (langs.isEmpty()) {
            langs.put("en", "English");
            langs.put("ru", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439 (Russian)");
        }
        langs.putIfAbsent("en", "English");
        return langs;
    }

    /**
     * @return the currently saved language code ("en" or "ru")
     */
    public static String getLanguage() {
        try {
            return java.util.prefs.Preferences.userRoot().node("org/pepsoft/worldpainter")
                    .get("language", "en");
        } catch (Exception e) {
            return "en";
        }
    }
}
