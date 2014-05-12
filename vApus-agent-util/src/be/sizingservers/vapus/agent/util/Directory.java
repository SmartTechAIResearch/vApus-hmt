/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent.util;

import java.io.File;
import java.net.URISyntaxException;
import java.security.CodeSource;

/**
 *
 * @author didjeeh
 */
public class Directory {

    /**
     * Gets the path of the current executing jar, filename excluded. Ends with
     * a / or a \ (OS specific).
     *
     * @param cls
     * @return
     * @throws java.net.URISyntaxException If the dir could not be determined.
     */
    public static String getExecutingDirectory(Class cls) throws URISyntaxException {
        String jarPath = getJarPath(cls, true);

        String delimiter = "" + jarPath.charAt(jarPath.length() - 1);

        String dir = jarPath;

        if (dir.endsWith(".jar" + delimiter)) {
            File jarFile = new File(jarPath);
            dir = jarFile.getParentFile().getAbsolutePath();
        }

        if (!dir.endsWith(delimiter)) {
            dir += delimiter;
        }

        return dir;
    }

    private static String getJarPath(Class cls, boolean endWithDelimiter) throws URISyntaxException {

        CodeSource codeSource = cls.getProtectionDomain().getCodeSource();

        File jarFile = new File(codeSource.getLocation().toURI().getPath());
        String jarPath = jarFile.getAbsolutePath();

        if (endWithDelimiter) {
            String delimiter = extractDelimiter(jarPath);
            if (!jarPath.endsWith(delimiter)) {
                jarPath += delimiter;
            }
        }
        return jarPath;
    }

    private static String extractDelimiter(String path) {
        if (path.length() == 0) {
            return "";
        }

        String delimiter = "/";
        if (path.contains("\\")) {
            delimiter = "\\";
        }

        return delimiter;
    }
}
