package com.adonai.manman;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.TypedValue;
import android.widget.Toast;

import com.adonai.manman.entities.ManSectionIndex;
import com.adonai.manman.entities.ManSectionItem;

import org.jsoup.nodes.Document;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

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
        if(target == null) // probably called from detached fragment (app hidden)
            return;

        target.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(target, stringRes, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void showToastFromAnyThread(final Activity target, final String toShow) {
        // can't show a toast from a thread without looper
        if(target == null) // probably called from detached fragment (app hidden)
            return;

        target.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(target, toShow, Toast.LENGTH_SHORT).show();
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

    public static int getThemedValue(Context context, int resource) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(resource, typedValue, true);
        return typedValue.data;
    }

    public static int getThemedResource(Context context, int resource) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(resource, typedValue, true);
        return typedValue.resourceId;
    }

    /**
     * Loads CSS from assets folder according to selected theme.
     * Fragment should be in attached state for this
     *
     * @param context context to retrieve theme properties from
     * @param url base url of page
     * @param htmlContent page with content to splatter color on...
     * @return html string
     */
    public static String getWebWithCss(@NonNull Context context, @NonNull String url, @Nullable String htmlContent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String theme = prefs.getString("app.theme", "light");

        Document doc = Document.createShell(url);
        doc.head().append("<link rel=\"stylesheet\" href=\"file:///android_asset/css/" + theme + ".css\" type=\"text/css\" media=\"all\" title=\"Standard\"/>");
        return doc.html().replace("<body>", "<body>" + htmlContent); // ugly hack, huh? Well, why don't you come up with something?
    }

    public static String detectEncodingOfArchive(File gzipped) throws IOException {
        FileInputStream fis = new FileInputStream(gzipped);
        GZIPInputStream gis = new GZIPInputStream(fis);
        byte[] buf = new byte[4096];

        UniversalDetector detector = new UniversalDetector(null);
        int read;
        while ((read = gis.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, read);
        }
        detector.dataEnd();
        gis.close();

        return detector.getDetectedCharset();
    }
}
