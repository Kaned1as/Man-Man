package com.adonai.manman;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.Toast;

import com.adonai.manman.entities.ManSectionIndex;
import com.adonai.manman.entities.ManSectionItem;
import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import com.j256.simplemagic.ContentType;

import org.apache.commons.compress.utils.IOUtils;
import org.jsoup.nodes.Document;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
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

    private static final ContentInfoUtil MIME_DETECTOR = new ContentInfoUtil();
    static {
        MIME_DETECTOR.setFileReadSize(386);
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

    public static String detectEncodingOfArchive(BufferedInputStream bis) throws IOException {
        byte[] buf = new byte[4096];
        bis.mark(Integer.MAX_VALUE);
        UniversalDetector detector = new UniversalDetector(null);
        int read;
        while ((read = bis.read(buf)) > 0 && !detector.isDone()) {
            detector.handleData(buf, 0, read);
        }
        detector.dataEnd();
        bis.reset();

        return detector.getDetectedCharset();
    }

    /**
     * File content type detector with additional checks for tar archives and troff man files
     * @param bis input stream supporting mark, non null
     * @return content type detected. OTHER in case if nothing found
     * @throws IOException on error while reading
     */
    @NonNull
    public static ContentType getMimeSubtype(@NonNull BufferedInputStream bis) throws IOException {
        bis.mark(8192);
        ContentInfo ci = MIME_DETECTOR.findMatch(bis);
        bis.reset();
        
        {
            // try to match troff lines
            byte[] cached = new byte[1024];
            bis.read(cached);
            bis.reset();

            BufferedReader sr = new BufferedReader(new StringReader(new String(cached)));
            String line;
            while ((line = sr.readLine()) != null) {
                if (line.contains("nroff")
                        || line.toLowerCase().startsWith(".sh synopsis")
                        || line.toLowerCase().startsWith(".th "))
                    return ContentType.TROFF;
            }
        }
        
        if(ci == null)
            return ContentType.OTHER;
        
        if(ci.getContentType() == ContentType.OTHER && !TextUtils.isEmpty(ci.getMimeType())) {
            if(ci.getMimeType().startsWith("application/x-tar"))
                return ContentType.TAR;
        }
        
        return ci.getContentType();
    }
}
