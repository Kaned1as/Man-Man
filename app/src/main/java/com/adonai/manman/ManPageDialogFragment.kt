package com.adonai.manman

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.provider.Browser
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.adonai.manman.database.DbProvider.helper
import com.adonai.manman.entities.ManPage
import com.adonai.manman.misc.AbstractNetworkAsyncLoader
import com.adonai.manman.parser.Man2Html
import com.adonai.manman.views.PassiveSlidingPane
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.*
import java.net.URISyntaxException
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * Dialog fragment for showing web page with man content
 * Retrieves info from DB (if cached) or network (if not)
 *
 * @see com.adonai.manman.entities.ManPage
 *
 * @author Kanedias
 */
class ManPageDialogFragment : Fragment() {
    private val mPrefChangeListener = FontChangeListener()
    private val manPageCallback = RetrieveManPageCallback()

    private lateinit var mPrefs: SharedPreferences
    private lateinit var mLocalArchive: File
    private lateinit var mAddressUrl: String
    private lateinit var mCommandName: String

    private lateinit var mLinkContainer: LinearLayout
    private lateinit var mSlider: PassiveSlidingPane
    private lateinit var mFlipper: ViewFlipper
    private lateinit var mContent: WebView
    private lateinit var mSearchContainer: LinearLayout
    private lateinit var mSearchEdit: EditText
    private lateinit var mCloseSearchBar: ImageView
    private lateinit var mFindNext: ImageView
    private lateinit var mFindPrevious: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            mAddressUrl = requireArguments().getString(PARAM_ADDRESS)!!
            mCommandName = requireArguments().getString(PARAM_NAME)!!
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        mLocalArchive = File(requireActivity().cacheDir, "manpages.zip")
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
        // making strong reference as requested by registerOnSharedPreferenceChangeListener call
        mPrefChangeListener
        mPrefs.registerOnSharedPreferenceChangeListener(mPrefChangeListener)

        val root = inflater.inflate(R.layout.fragment_man_page_show, container, false)
        mLinkContainer = root.findViewById<View>(R.id.link_list) as LinearLayout
        mFlipper = root.findViewById<View>(R.id.flipper) as ViewFlipper
        mContent = root.findViewById<View>(R.id.man_content_web) as WebView
        mContent.webViewClient = ManPageChromeClient()
        mContent.settings.javaScriptEnabled = true
        mContent.settings.minimumFontSize = fontFromProperties
        mSlider = root.findViewById<View>(R.id.sliding_pane) as PassiveSlidingPane
        mSlider.setTrackedView(mContent)

        // search-specific
        mSearchContainer = root.findViewById<View>(R.id.search_bar_layout) as LinearLayout
        mSearchEdit = mSearchContainer.findViewById<View>(R.id.search_edit) as EditText
        mCloseSearchBar = mSearchContainer.findViewById<View>(R.id.close_search_bar) as ImageView
        mFindNext = mSearchContainer.findViewById<View>(R.id.find_next_occurrence) as ImageView
        mFindPrevious = mSearchContainer.findViewById<View>(R.id.find_previous_occurrence) as ImageView
        mCloseSearchBar.setOnClickListener(SearchBarCloser())
        mSearchEdit.addTextChangedListener(SearchExecutor())
        mFindNext.setOnClickListener(SearchFurtherExecutor(true))
        mFindPrevious.setOnClickListener(SearchFurtherExecutor(false))

        // Lollipop blocks mixed content but we should load CSS from filesystem
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mContent.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        loaderManager.initLoader(MainPagerActivity.MAN_PAGE_RETRIEVER_LOADER, null, manPageCallback)
        return root
    }

    override fun onResume() {
        super.onResume()
        // hide keyboard on fragment show, window token is hopefully present at this moment
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(mContent.windowToken, 0)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.man_page_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.show_search_bar -> {
                toggleSearchBar(View.VISIBLE)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleSearchBar(visibility: Int) {
        mSearchContainer.visibility = visibility
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (visibility == View.VISIBLE) {
            mSearchEdit.requestFocus()
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
        } else {
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
        }
    }

    /**
     * Class for creating a loader that performs async loading of man page from www.mankier.com
     * On finish passes data to web content and makes it active
     * On fail dismisses parent dialog
     *
     */
    private inner class RetrieveManPageCallback : LoaderManager.LoaderCallbacks<ManPage?> {
        override fun onCreateLoader(id: Int, args: Bundle?): Loader<ManPage?> {
            return object : AbstractNetworkAsyncLoader<ManPage?>(activity!!) {
                override fun loadInBackground(): ManPage? {
                    // handle special case when it's a local file
                    if (mAddressUrl.startsWith("/")) { // TODO: rewrite with URI
                        try {
                            val input = File(mAddressUrl)
                            val charset = Utils.detectEncodingOfArchive(input)
                            val fis = FileInputStream(input)
                            val gis = GZIPInputStream(fis)
                            val br = if (charset == null) BufferedReader(InputStreamReader(gis)) else BufferedReader(InputStreamReader(gis, charset))
                            val parser = Man2Html(br)
                            val parsed = parser.doc
                            val result = ManPage(input.name, "file://$mAddressUrl")
                            result.links = getLinks(parsed.select("div.man-page").first())
                            result.webContent = parsed.html()
                            br.close() // closes all the IS hierarchy
                            return result
                        } catch (e: FileNotFoundException) {
                            Log.e(Utils.MM_TAG, "File with man page was not found in local folder", e)
                            Toast.makeText(activity, R.string.file_not_found, Toast.LENGTH_SHORT).show()
                        } catch (e: IOException) {
                            Log.e(Utils.MM_TAG, "Exception while loading man page file from local folder", e)
                            Toast.makeText(activity, R.string.wrong_file_format, Toast.LENGTH_SHORT).show()
                        }
                        return null // no further querying
                    }
                    if (mAddressUrl.startsWith("local:")) { // local man archive
                        try {
                            val zip = ZipFile(mLocalArchive)
                            val zEntry = zip.getEntry(mAddressUrl.substring(7))
                            val `is` = zip.getInputStream(zEntry)
                            // can't use java's standard GZIPInputStream around zip IS because of inflating issue
                            val gis = GzipCompressorInputStream(`is`) // manpage files are .gz
                            val br = BufferedReader(InputStreamReader(gis))
                            val parser = Man2Html(br)
                            val parsed = parser.doc
                            val result = ManPage(zEntry.name, mAddressUrl)
                            result.links = getLinks(parsed.select("div.man-page").first())
                            result.webContent = parsed.html()
                            br.close() // closes all the IS hierarchy
                            return result
                        } catch (e: IOException) {
                            Log.e(Utils.MM_TAG, "Error while loading man page from local archive", e)
                            Toast.makeText(activity, R.string.wrong_file_format, Toast.LENGTH_SHORT).show()
                        }
                    }
                    try { // query cache database for corresponding command
                        val cached = helper.manPagesDao.queryForId(mAddressUrl)
                        if (cached != null) {
                            return cached
                        }
                    } catch (e: RuntimeException) { // it's RuntimeExceptionDao, so catch runtime exceptions
                        Log.e(Utils.MM_TAG, "Exception while querying the DB", e)
                        Utils.showToastFromAnyThread(activity, R.string.database_retrieve_error)
                    }
                    try {
                        val client = OkHttpClient()
                        val request = Request.Builder().url(mAddressUrl).build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val result = response.body!!.string()
                            val root = Jsoup.parse(result, mAddressUrl)
                            val man = root.select("div.man-page, main").first()
                                    ?: // not actually a man page
                                    return null
                            val webContent = man.html()
                            val linkContainer = getLinks(man)

                            // save to DB for caching
                            val toCache = ManPage(mCommandName!!, mAddressUrl!!)
                            toCache.links = linkContainer
                            toCache.webContent = webContent
                            helper.manPagesDao.createIfNotExists(toCache)
                            LocalBroadcastManager.getInstance(activity!!).sendBroadcast(Intent(MainPagerActivity.DB_CHANGE_NOTIFY))
                            return toCache
                        }
                    } catch (e: IOException) {
                        Log.e(Utils.MM_TAG, "Exception while saving cached page to DB", e)
                        // can't show a toast from a thread without looper
                        Utils.showToastFromAnyThread(activity, R.string.connection_error)
                    }
                    return null
                }

                // retrieve link set from manpage
                private fun getLinks(man: Element): TreeSet<String> {
                    val links = man.select("a[href*=#]")
                    val linkContainer = TreeSet<String>()
                    for (link in links) {
                        if (!TextUtils.isEmpty(link.text()) && link.attr("href").contains("#" + link.text())) { // it's like <a href="http://ex.com/#a">-x</a>
                            linkContainer.add(link.text())
                        }
                    }
                    return linkContainer
                }
            }
        }

        override fun onLoadFinished(loader: Loader<ManPage?>, data: ManPage?) {
            if (data != null) {
                mContent.loadDataWithBaseURL(mAddressUrl, Utils.getWebWithCss(activity!!, data.url, data.webContent), "text/html", "UTF-8", null)
                mContent.setBackgroundColor(Utils.getThemedValue(activity, R.attr.fill_color)) // prevent flickering
                fillLinkPane(data.links)
                mFlipper.showNext()
                shakeSlider()
            } else {
                mContent.postDelayed({
                    fragmentManager!!.popBackStack() // can't perform transactions from onLoadFinished
                }, 0)
            }
        }

        override fun onLoaderReset(loader: Loader<ManPage?>) {
            // never used
        }
    }

    private fun shakeSlider() {
        if (mLinkContainer.childCount == 0) // nothing to show in the links pane
            return
        if (mPrefs.contains(USER_LEARNED_SLIDER)) return
        mSlider.postDelayed({ mSlider.openPane() }, 1000)
        mSlider.postDelayed({ mSlider.closePane() }, 2000)
        mPrefs.edit().putBoolean(USER_LEARNED_SLIDER, true).apply()
    }

    private fun fillLinkPane(links: Set<String>?) {
        mLinkContainer.removeAllViews()
        if (links == null || links.isEmpty()) return
        for (link in links) {
            // hack  for https://code.google.com/p/android/issues/detail?id=36660 - place inside of FrameLayout
            val root = LayoutInflater.from(activity).inflate(R.layout.link_text_item, mLinkContainer, false)
            val linkLabel = root.findViewById<View>(R.id.link_text) as TextView
            linkLabel.text = link
            root.setOnClickListener {
                mContent.loadUrl("""javascript:(function() {
    var l = document.querySelector('a[href$="#$link"]');
    var event = new MouseEvent('click', {
        'view': window,
        'bubbles': true,
        'cancelable': true
    });    console.log(l);
    l.dispatchEvent(event);
})()""")
            }
            mLinkContainer!!.addView(root)
        }
    }// default webview font size

    /**
     * Retrieve font size for web views from shared properties
     * @return integer representing font size or default in case incorrect number is set
     */
    private val fontFromProperties: Int
        private get() = try {
            mPrefs.getString(Utils.FONT_PREF_KEY, "12")!!.toInt()
        } catch (ex: NumberFormatException) {
            Toast.makeText(activity, R.string.invalid_font_size_set, Toast.LENGTH_SHORT).show()
            12 // default webview font size
        }

    /**
     * Class to load URLs inside of already active webview
     * Calls original browser intent for the URLs it can't handle
     */
    private inner class ManPageChromeClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (url.matches(Regex("https://www\\.mankier\\.com/.+/.+"))) { // it's an address of the command
                mFlipper.showPrevious()
                mAddressUrl = url
                mCommandName = url.substring(url.lastIndexOf('/') + 1)
                loaderManager.getLoader<Any>(MainPagerActivity.MAN_PAGE_RETRIEVER_LOADER)!!.onContentChanged()
                return true
            }
            return shouldOverrideUrlLoadingOld(view, url)
        }

        /**
         * Copied from WebViewContentsClientAdapter (internal android class)
         * to handle URLs in old way if it's not a man page
         */
        fun shouldOverrideUrlLoadingOld(view: WebView, url: String): Boolean {
            val intent: Intent
            // Perform generic parsing of the URI to turn it into an Intent.
            intent = try {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } catch (ex: URISyntaxException) {
                Log.w("WebViewCallback", "Bad URI " + url + ": " + ex.message)
                return false
            }
            // Sanitize the Intent, ensuring web pages can not bypass browser
            // security (only access to BROWSABLE activities).
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null

            // Pass the package name as application ID so that the intent from the
            // same application can be opened in the same tab.
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, view.context.packageName)
            try {
                view.context.startActivity(intent)
            } catch (ex: ActivityNotFoundException) {
                Log.w("WebViewCallback", "No application can handle $url")
                return false
            }
            return true
        }
    }

    /**
     * Closes the search bar
     */
    private inner class SearchBarCloser : View.OnClickListener {
        override fun onClick(v: View) {
            toggleSearchBar(View.GONE)
            mContent.clearMatches()
        }
    }

    /**
     * Finds next occurrence depending on direction
     */
    private inner class SearchFurtherExecutor(private val goDown: Boolean) : View.OnClickListener {
        override fun onClick(v: View) {
            mContent.findNext(goDown)
        }
    }

    /**
     * Executes search on string change
     */
    private inner class SearchExecutor : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            mContent.findAllAsync(s.toString())
        }
    }

    private inner class FontChangeListener : OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
            when (key) {
                Utils.FONT_PREF_KEY -> mContent.settings.minimumFontSize = fontFromProperties
            }
        }
    }

    companion object {
        private const val USER_LEARNED_SLIDER = "user.learned.slider"
        private const val PARAM_ADDRESS = "param.address"
        private const val PARAM_NAME = "param.name"
        @JvmStatic
        fun newInstance(commandName: String, address: String): ManPageDialogFragment {
            val fragment = ManPageDialogFragment()
            val args = Bundle()
            args.putString(PARAM_ADDRESS, address)
            args.putString(PARAM_NAME, commandName)
            fragment.arguments = args
            return fragment
        }
    }
}