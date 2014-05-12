/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A helper class that handles bash reading a .properties file.
 *
 * @author didjeeh
 */
public class PropertyHelper {

    /**
     *
     * @param propertiesFile
     * @param key
     * @return
     * @throws IOException
     */
    public static boolean containsProperty(String propertiesFile, String key) throws IOException {
        return getProperties(propertiesFile).containsKey(key);
    }

    /**
     *
     * @param propertiesFile If the file is in the default package the path
     * would be /foo.properties.
     * @param key of the property.
     * @return
     * @throws IOException
     */
    public static String getProperty(String propertiesFile, String key) throws IOException {
        return getProperties(propertiesFile).getProperty(key);
    }

    /**
     *
     * @param propertiesFile If the file is in the default package the path
     * would be /foo.properties.
     * @return
     * @throws IOException
     */
    public static Properties getProperties(String propertiesFile) throws IOException {
        Properties properties = new Properties();
        InputStream stream = null;
        try {
            stream = PropertyHelper.class.getResourceAsStream(propertiesFile);
            properties.load(stream);
        } finally {
            if (stream != null) {
                stream.close();
            }
        }
        return properties;
    }
}
