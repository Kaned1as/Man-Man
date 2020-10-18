package com.adonai.manman

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.adonai.manman.entities.ManSectionIndex
import com.adonai.manman.entities.ManSectionItem
import org.jsoup.nodes.Document
import org.mozilla.universalchardet.UniversalDetector
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.zip.GZIPInputStream

/**
 * Utility class for wrapping useful static functions
 *
 * @author Kanedias
 */
object Utils {
    const val MM_TAG = "Man Man"
    const val FONT_PREF_KEY = "webview.font.size"

    /**
     * Converts |-delimited string array from resources to hash map
     * @param context context to retrieve resources from
     * @param stringArrayResourceId resourceId to look for
     * @return map consisting of key|value pairs as in string-array
     */
    fun parseStringArray(context: Context, stringArrayResourceId: Int): Map<String, String> {
        val stringArray = context.resources.getStringArray(stringArrayResourceId)
        val outputMap: MutableMap<String, String> = LinkedHashMap(stringArray.size)
        for (entry in stringArray) {
            val splitResult = entry.split("\\|".toRegex(), 2).toTypedArray()
            outputMap[splitResult[0]] = splitResult[1]
        }
        return outputMap
    }

    fun showToastFromAnyThread(target: Activity?, stringRes: Int) {
        // can't show a toast from a thread without looper
        if (target == null) // probably called from detached fragment (app hidden)
            return
        target.runOnUiThread(Runnable { Toast.makeText(target, stringRes, Toast.LENGTH_SHORT).show() })
    }

    fun showToastFromAnyThread(target: Activity?, toShow: String?) {
        // can't show a toast from a thread without looper
        if (target == null) // probably called from detached fragment (app hidden)
            return
        target.runOnUiThread(Runnable { Toast.makeText(target, toShow, Toast.LENGTH_SHORT).show() })
    }

    fun createIndexer(items: List<ManSectionItem>): List<ManSectionIndex> {
        val indexes: MutableList<ManSectionIndex> = ArrayList(26) // a guess
        var lastLetter = 0.toChar() // EOF is never encountered
        for (i in items.indices) {
            val msi = items[i]
            val newLetter = msi.name[0] // no commands without name, don't check
            if (newLetter != lastLetter) { // it's a start of new index
                val newIndex = ManSectionIndex(newLetter, i, msi.parentChapter)
                indexes.add(newIndex)
                lastLetter = newLetter
            }
        }
        return indexes
    }

    fun getThemedValue(context: Context, resource: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(resource, typedValue, true)
        return typedValue.data
    }

    fun getThemedResource(context: Context, resource: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(resource, typedValue, true)
        return typedValue.resourceId
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
    fun getWebWithCss(context: Context, url: String, htmlContent: String?): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val theme = prefs.getString("app.theme", "light")
        val doc = Document.createShell(url)
        doc.head().append("<link rel=\"stylesheet\" href=\"file:///android_asset/css/$theme.css\" type=\"text/css\" media=\"all\" title=\"Standard\"/>")
        return doc.html().replace("<body>", "<body>$htmlContent") // ugly hack, huh? Well, why don't you come up with something?
    }

    @Throws(IOException::class)
    fun detectEncodingOfArchive(gzipped: File?): String? {
        val fis = FileInputStream(gzipped)
        val gis = GZIPInputStream(fis)
        val buf = ByteArray(4096)
        val detector = UniversalDetector(null)
        var read: Int
        while (gis.read(buf).also { read = it } > 0 && !detector.isDone) {
            detector.handleData(buf, 0, read)
        }
        detector.dataEnd()
        gis.close()
        return detector.detectedCharset
    }

    fun setupTheme(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        val theme = prefs.getString("app.theme", "light")
        when (theme) {
            "light" -> activity.setTheme(R.style.Light)
            "dark" -> activity.setTheme(R.style.Dark)
            "green" -> activity.setTheme(R.style.Green)
        }
    }
}