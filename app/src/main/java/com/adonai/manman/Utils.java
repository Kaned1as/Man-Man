package com.adonai.manman;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class for wrapping useful static functions
 *
 * @author Adonai
 */
public class Utils {

    /**
     * Converts |-delimited string array from resources to hash map
     * @param context context to retrieve resources from
     * @param stringArrayResourceId resourceId to look for
     * @return map consisting of key|value pairs as in string-array
     */
    public static Map<String, String> parseStringArray(Context context, int stringArrayResourceId) {
        String[] stringArray = context.getResources().getStringArray(stringArrayResourceId);
        Map<String, String> outputMap = new LinkedHashMap<>(stringArray.length);
        for (String entry : stringArray) {
            String[] splitResult = entry.split("\\|", 2);
            outputMap.put(splitResult[0], splitResult[1]);
        }
        return outputMap;
    }

    public static void showToastFromAnyThread(final Activity target, final int stringRes) {
        // can't show a toast from a thread without looper
        target.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(target, stringRes, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
