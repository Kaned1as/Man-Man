package com.adonai.manman

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
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
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.adonai.manman.database.DbProvider
import com.adonai.manman.databinding.FragmentManPageShowBinding
import com.adonai.manman.entities.ManPage
import com.adonai.manman.misc.resolveAttr
import com.adonai.manman.misc.showFullscreenFragment
import com.adonai.manman.parser.Man2Html
import com.adonai.manman.service.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * @see ManPage
 *
 * @author Kanedias
 */
class ManPageDialogFragment : Fragment() {
    private val mPrefChangeListener = FontChangeListener()

    private lateinit var mPrefs: SharedPreferences
    private lateinit var mLocalArchive: File

    private lateinit var binding: FragmentManPageShowBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        mLocalArchive = File(requireActivity().cacheDir, "manpages.zip")

        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
        mPrefs.registerOnSharedPreferenceChangeListener(mPrefChangeListener)

        binding = FragmentManPageShowBinding.inflate(inflater, container, false)

        binding.manContentWeb.webViewClient = ManPageChromeClient()
        binding.manContentWeb.settings.javaScriptEnabled = true
        binding.manContentWeb.settings.minimumFontSize = Config.fontSize

        binding.slidingPane.sliderFadeColor = ColorUtils.setAlphaComponent(Utils.getThemedValue(requireContext(), R.attr.background_color), 200)
        binding.slidingPane.setTrackedView(binding.manContentWeb)

        // search-specific
        binding.closeSearchBar.setOnClickListener(SearchBarCloser())
        binding.searchEdit.addTextChangedListener(SearchExecutor())
        binding.findNextOccurrence.setOnClickListener(SearchFurtherExecutor(true))
        binding.findPreviousOccurrence.setOnClickListener(SearchFurtherExecutor(false))

        // Lollipop blocks mixed content but we should load CSS from filesystem
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            binding.manContentWeb.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        triggerLoadPageContent()

        return binding.root
    }

    @UiThread
    private fun triggerLoadPageContent() {
        val addressUrl = requireArguments().getString(PARAM_ADDRESS)!!
        val commandName = requireArguments().getString(PARAM_NAME)!!

        binding.manpageToolbar.title = commandName
        binding.manpageToolbar.subtitle = addressUrl
        binding.manpageToolbar.navigationIcon = DrawerArrowDrawable(binding.manpageToolbar.context).apply { progress = 1.0f  }
        binding.manpageToolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        binding.manpageToolbar.inflateMenu(R.menu.man_page_menu)
        binding.manpageToolbar.setOnMenuItemClickListener { mi ->
            return@setOnMenuItemClickListener when (mi.itemId) {
                R.id.show_search_bar -> {
                    toggleSearchBar(View.VISIBLE)
                    true
                }
                else -> false
            }
        }

        lifecycleScope.launch {
            val manpage = withContext(Dispatchers.IO) { doLoadContent(addressUrl, commandName) }
            if (manpage != null) {
                binding.manContentWeb.loadDataWithBaseURL(addressUrl, Utils.getWebWithCss(requireContext(), manpage.url, manpage.webContent), "text/html", "UTF-8", null)
                binding.manContentWeb.setBackgroundColor(Utils.getThemedValue(requireContext(), R.attr.background_color)) // prevent flickering
                fillLinkPane(manpage.links)

                // show the actual content on web page
                binding.manpageWaitFlipper.showNext()
                shakeSlider()
            } else {
                parentFragmentManager.popBackStack()
            }
        }
    }

    @WorkerThread
    private fun doLoadContent(addressUrl: String, commandName: String): ManPage? {
        // handle special case when it's a filesystem
        if (addressUrl.startsWith("/")) { // TODO: rewrite with URI
            try {
                val input = File(addressUrl)
                val charset = Utils.detectEncodingOfArchive(input)
                val fis = FileInputStream(input)
                val gis = GZIPInputStream(fis)

                val reader = when (charset) {
                    null -> BufferedReader(InputStreamReader(gis))
                    else -> BufferedReader(InputStreamReader(gis, charset))
                }

                reader.use {
                    val parser = Man2Html(it)
                    val parsed = parser.doc
                    val result = ManPage(input.name, "file://$addressUrl")
                    result.links = getLinks(parsed.select("div.man-page").first())
                    result.webContent = parsed.html()
                    return result
                }
            } catch (e: FileNotFoundException) {
                Log.e(Utils.MM_TAG, "File with man page was not found in local folder: $addressUrl", e)
                Toast.makeText(activity, R.string.file_not_found, Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e(Utils.MM_TAG, "Exception while loading man page file from local folder: $addressUrl", e)
                Toast.makeText(activity, R.string.wrong_file_format, Toast.LENGTH_SHORT).show()
            }

            // no further querying, request was for local file
            return null
        }

        // special case: local file
        if (addressUrl.startsWith("local:")) {
            // local man archive
            try {
                val zip = ZipFile(mLocalArchive)
                var zEntry = zip.getEntry(addressUrl.substringAfter("local:/"))
                if (zEntry == null) {
                    // local links from the page itself can be without gz prefix
                    zEntry = zip.getEntry(addressUrl.substringAfter("local:/") + ".gz")
                }
                if (zEntry == null) {
                    // still null, reference to a non-existing page?
                    throw FileNotFoundException("File $addressUrl not found in local zip archive")
                }

                val zStream = zip.getInputStream(zEntry)
                // can't use java's standard GZIPInputStream around zip IS because of inflating issue
                val gis = GzipCompressorInputStream(zStream) // manpage files are .gz
                val reader = BufferedReader(InputStreamReader(gis))

                reader.use {
                    val parser = Man2Html(it)
                    val parsed = parser.doc
                    val result = ManPage(zEntry.name, addressUrl)
                    result.links = getLinks(parsed.select("div.man-page").first())
                    result.webContent = parsed.html()
                    return result
                }
            } catch (e: FileNotFoundException) {
                Log.e(Utils.MM_TAG, "Error while loading man page from local archive", e)
                Utils.showToastFromAnyThread(activity, getString(R.string.file_not_found_extra, addressUrl))
                return null
            } catch (e: IOException) {
                Log.e(Utils.MM_TAG, "Error while loading man page from local archive: $addressUrl", e)
                Utils.showToastFromAnyThread(activity, R.string.wrong_file_format)
                return null
            }
        }

        // query cache database for corresponding command
        val cached = DbProvider.helper.manPagesDao.queryForId(addressUrl)
        if (cached != null) {
            return cached
        }

        // exhausted all local variants, fetch remote content
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(addressUrl).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val result = response.body!!.string()
                val root = Jsoup.parse(result, addressUrl)
                val man = root.select("div.man-page, main").first() ?: return null
                val webContent = man.html()
                val linkContainer = getLinks(man)

                // save to DB for caching
                val toCache = ManPage(commandName, addressUrl)
                toCache.links = linkContainer
                toCache.webContent = webContent
                DbProvider.helper.manPagesDao.createIfNotExists(toCache)
                LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(MainPagerActivity.DB_CHANGE_NOTIFY))
                return toCache
            }
        } catch (e: IOException) {
            Log.e(Utils.MM_TAG, "Exception while saving cached page to DB: $addressUrl", e)
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

    override fun onResume() {
        super.onResume()
        // hide keyboard on fragment show, window token is hopefully present at this moment
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.manContentWeb.windowToken, 0)
    }

    private fun toggleSearchBar(visibility: Int) {
        binding.searchBarLayout.visibility = visibility
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (visibility == View.VISIBLE) {
            binding.searchEdit.requestFocus()
            imm.showSoftInput(binding.searchEdit, 0)
        } else {
            binding.searchEdit.clearFocus()
            imm.hideSoftInputFromWindow(binding.searchEdit.windowToken, 0)
        }
    }

    private fun shakeSlider() {
        if (binding.linkList.childCount == 0) {
            // nothing to show in the links pane, skip
            return
        }

        if (Config.userLearnedSlider) {
            // user already seen this animation, skip
            return
        }

        binding.slidingPane.postDelayed({ binding.slidingPane.openPane() }, 1000)
        binding.slidingPane.postDelayed({ binding.slidingPane.closePane() }, 2000)

        Config.userLearnedSlider = true
    }

    private fun fillLinkPane(links: Set<String>) {
        binding.linkList.removeAllViews()
        if (links.isNullOrEmpty()) {
            return
        }

        val sortedLinks = links.sortedWith(
            compareBy<String>{ it.firstOrNull()?.isLetter()?.not() }
            .thenBy { it.length != 2 }
        )
        for (link in sortedLinks) {
            // hack  for https://code.google.com/p/android/issues/detail?id=36660 - place inside of FrameLayout
            val root = LayoutInflater.from(activity).inflate(R.layout.link_text_item, binding.linkList, false)
            val linkLabel = root.findViewById<View>(R.id.link_text) as TextView
            linkLabel.text = link
            root.setOnClickListener {
                binding.manContentWeb.loadUrl("""
                javascript:(function() {
                    var l = document.querySelector('a[href$="#$link"]');
                    var event = new MouseEvent('click', {
                        'view': window,
                        'bubbles': true,
                        'cancelable': true
                    });
                    l.dispatchEvent(event);
                })()""".trimIndent())
            }
            binding.linkList.addView(root)
        }
    }

    /**
     * Class to load URLs inside of already active webview
     * Calls original browser intent for the URLs it can't handle
     */
    private inner class ManPageChromeClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (url.matches(Regex("https://www\\.mankier\\.com/.+/.+"))) { // it's an address of the command
                val name = url.substring(url.lastIndexOf('/') + 1)

                val mpdf = newInstance(name, url)
                requireActivity().showFullscreenFragment(mpdf)

                return true
            }

            if (url.startsWith("local:")) {
                val name = url.substringAfterLast('/')

                val mpdf = newInstance(name, url)
                requireActivity().showFullscreenFragment(mpdf)

                return true
            }

            return shouldOverrideUrlLoadingOld(view, url)
        }

        /**
         * Copied from WebViewContentsClientAdapter (internal android class)
         * to handle URLs in old way if it's not a man page
         */
        fun shouldOverrideUrlLoadingOld(view: WebView, url: String): Boolean {
            // Perform generic parsing of the URI to turn it into an Intent.
            val intent = try {
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
            binding.manContentWeb.clearMatches()
        }
    }

    /**
     * Finds next occurrence depending on direction
     */
    private inner class SearchFurtherExecutor(private val goDown: Boolean) : View.OnClickListener {
        override fun onClick(v: View) {
            binding.manContentWeb.findNext(goDown)
        }
    }

    /**
     * Executes search on string change
     */
    private inner class SearchExecutor : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable) {
            binding.manContentWeb.findAllAsync(s.toString())
        }
    }

    private inner class FontChangeListener : OnSharedPreferenceChangeListener {
        override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
            when (key) {
                Config.FONT_PREF_KEY -> binding.manContentWeb.settings.minimumFontSize = Config.fontSize
            }
        }
    }

    companion object {
        private const val PARAM_ADDRESS = "param.address"
        private const val PARAM_NAME = "param.name"

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