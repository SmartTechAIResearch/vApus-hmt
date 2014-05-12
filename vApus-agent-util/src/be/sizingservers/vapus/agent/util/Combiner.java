/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent.util;

import java.util.ArrayList;

/**
 *
 * @author didjeeh
 */
public class Combiner {

    /**
     * 
     * @param arr
     * @param separator
     * @return 
     */
    public static String combine(ArrayList<String> arr, String separator) {
        StringBuilder sb = new StringBuilder();

        if (!arr.isEmpty()) {
            for (int i = 0; i != arr.size() - 1; i++) {
                sb.append(arr.get(i));
                sb.append(separator);
            }
            sb.append(arr.get(arr.size() - 1));
        }
        return sb.toString();
    }
}
