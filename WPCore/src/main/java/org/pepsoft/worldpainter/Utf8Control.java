/*
 * ResourceBundle.Control that reads .properties strictly as UTF-8.
 */
package org.pepsoft.worldpainter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public final class Utf8Control extends ResourceBundle.Control {
    public static final Utf8Control INSTANCE = new Utf8Control();

    private Utf8Control() {
    }

    @Override
    public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                    ClassLoader loader, boolean reload)
            throws IllegalAccessException, InstantiationException, IOException {
        if (!"java.properties".equals(format)) {
            return super.newBundle(baseName, locale, format, loader, reload);
        }
        final String bundleName = toBundleName(baseName, locale);
        final String resourceName = toResourceName(bundleName, "properties");
        try (InputStream is = open(resourceName, loader, reload)) {
            if (is == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    }

    private InputStream open(String resourceName, ClassLoader loader, boolean reload) throws IOException {
        if (!reload) {
            return loader.getResourceAsStream(resourceName);
        }
        final URL url = loader.getResource(resourceName);
        if (url == null) {
            return null;
        }
        final URLConnection connection = url.openConnection();
        connection.setUseCaches(false);
        return connection.getInputStream();
    }
}