package com.adonai.manman;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.adonai.manman.entities.ManSectionIndex;
import com.adonai.manman.entities.ManSectionItem;

import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for wrapping useful static functions
 *
 * @author Adonai
 */
public class Utils {

    public static HttpParams defaultHttpParams;

    static {
        defaultHttpParams = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(defaultHttpParams, 3000);
        HttpConnectionParams.setSoTimeout(defaultHttpParams, 10000);
    }

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
        if(target == null) // probably called from detached fragment (app hidden)
            return;

        target.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(target, stringRes, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static List<ManSectionIndex> createIndexer(List<ManSectionItem> items) {
        List<ManSectionIndex> indexes = new ArrayList<>(26); // a guess
        char lastLetter = 0; // EOF is never encountered
        for (int i = 0; i < items.size(); ++i) {
            ManSectionItem msi = items.get(i);
            char newLetter = msi.getName().charAt(0); // no commands without name, don't check
            if(newLetter != lastLetter) { // it's a start of new index
                ManSectionIndex newIndex = new ManSectionIndex(newLetter, i, msi.getParentChapter());
                indexes.add(newIndex);
                lastLetter = newLetter;
            }
        }
        return indexes;
    }
}
