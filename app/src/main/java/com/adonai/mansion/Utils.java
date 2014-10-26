package com.adonai.mansion;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by adonai on 26.10.14.
 */
public class Utils {

    public static Map<String, String> parseStringArray(Context context, int stringArrayResourceId) {
        String[] stringArray = context.getResources().getStringArray(stringArrayResourceId);
        Map<String, String> outputMap = new HashMap<>(stringArray.length);
        for (String entry : stringArray) {
            String[] splitResult = entry.split("\\|", 2);
            outputMap.put(splitResult[0], splitResult[1]);
        }
        return outputMap;
    }
}
