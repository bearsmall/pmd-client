package com.xy.pmd.international;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * @author bearsmalll
 * @date 2018/11/27
 * 资源信息国际化
 * reference：https://docs.oracle.com/javase/8/docs/api/java/util/ResourceBundle.Control.html  example two
 */
public class I18nResourceMapper {
    private static final String XML_LITERAL = "xml";

    private static String lang = System.getProperty("pmd.language", "zh");

    private static Locale currentLocale;

    private static ResourceBundle resourceBundle = changeLanguage(lang);

    public static ResourceBundle changeLanguage(String language) {
        Locale locale = Locale.CHINESE.getLanguage().equals(language) ? Locale.CHINESE : Locale.ENGLISH;
        return changeLanguage(locale);
    }

    public static ResourceBundle changeLanguage(Locale locale) {
        if (currentLocale != null && currentLocale.equals(locale)) {
            return resourceBundle;
        }
        currentLocale = locale;
        resourceBundle = ResourceBundle.getBundle("messages", locale, new XMLResourceBundleControl());
        return resourceBundle;
    }

    public static String getMessage(String key) {
        return resourceBundle.getString(key).trim();
    }

    public static String getMessage(String key, Object... params) {
        String value = getMessage(key);
        if (params == null || params.length == 0) {
            return value;
        }
        return String.format(value, params);
    }

    public static String getMessageWithExceptionHandled(String key) {
        try {
            return resourceBundle.getString(key).trim();
        } catch (MissingResourceException e) {
            return key;
        }
    }

    public static class XMLResourceBundleControl extends ResourceBundle.Control {
        @Override
        public List<String> getFormats(String baseName) {
            if (baseName == null) {
                throw new NullPointerException();
            }
            return Collections.singletonList(XML_LITERAL);
        }

        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }

        @Override
        public ResourceBundle newBundle(String baseName,
                                        Locale locale,
                                        String format,
                                        ClassLoader loader,
                                        boolean reload)
                throws IllegalAccessException,
                InstantiationException,
                IOException {
            if (baseName == null || locale == null
                    || format == null || loader == null) {
                throw new NullPointerException();
            }
            ResourceBundle bundle = null;
            if (XML_LITERAL.equals(format)) {
                String bundleName = toBundleName(baseName, locale);
                String resourceName = toResourceName(bundleName, format);
                InputStream stream;
                if (reload) {
                    stream = getInputStream(loader, resourceName);
                } else {
                    stream = loader.getResourceAsStream(resourceName);
                }
                if (stream != null) {
                    BufferedInputStream bis = new BufferedInputStream(stream);
                    bundle = new XmlResourceBundle(bis);
                    bis.close();
                }
            }
            return bundle;
        }

        private InputStream getInputStream(ClassLoader loader, String resourceName)
                throws IOException {
            URL url = loader.getResource(resourceName);
            if (url != null) {
                URLConnection connection = url.openConnection();
                if (connection != null) {
                    // Disable caches to get fresh data for
                    // reloading.
                    connection.setUseCaches(false);
                    return connection.getInputStream();
                }
            }
            return null;
        }
    }

    public static class XmlResourceBundle extends ResourceBundle {
        private Properties props;

        XmlResourceBundle(InputStream stream) throws IOException {
            props = new Properties();
            props.loadFromXML(stream);
        }

        @Override
        protected Object handleGetObject(String key) {
            return props.getProperty(key);
        }

        @Override
        public Enumeration<String> getKeys() {
            List<String> keys = new ArrayList<>();
            Enumeration<Object> enumeration = props.keys();
            while (enumeration.hasMoreElements()) {
                keys.add((String)enumeration.nextElement());
            }
            return Collections.enumeration(keys);
        }
    }

}
