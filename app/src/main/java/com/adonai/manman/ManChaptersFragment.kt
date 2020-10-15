package com.adonai.manman

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.adonai.manman.adapters.ChapterContentsArrayAdapter
import com.adonai.manman.adapters.ChapterContentsCursorAdapter
import com.adonai.manman.adapters.ChaptersArrayAdapter
import com.adonai.manman.database.DbProvider
import com.adonai.manman.entities.ManSectionItem
import com.adonai.manman.misc.AbstractNetworkAsyncLoader
import com.adonai.manman.views.ProgressBarWrapper
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.RuntimeExceptionDao
import com.j256.ormlite.misc.TransactionManager
import com.j256.ormlite.stmt.PreparedQuery
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.sql.SQLException
import java.util.*
import java.util.zip.GZIPInputStream

/**
 * Fragment to show table of contents and navigate into it
 * Note: works slower that just search!
 *
 * @author Kanedias
 */
class ManChaptersFragment : Fragment() {
    private val mContentRetrieveCallback = RetrieveChapterContentsCallback()
    private val mPackageRetrieveCallback = RetrievePackageContentsCallback()
    private val mBroadcastHandler: BroadcastReceiver = BackButtonBroadcastReceiver()

    private lateinit var mChaptersAdapter: ChaptersArrayAdapter
    private lateinit var mCachedChapters: Map<String, String>

    private lateinit var mFrame: FrameLayout
    private lateinit var mListView: ListView
    private lateinit var mProgress: ProgressBarWrapper

    /**
     * Click listener for selecting a chapter from the list.
     * Usable only when list view shows list of chapters
     * The request is then sent to the loader to load chapter data asynchronously
     * <br></br>
     *
     * @see RetrieveChapterContentsCallback
     */
    private val mChapterClickListener = OnItemClickListener { parent, view, position, id ->
        val item = parent.getItemAtPosition(position) as Map.Entry<String, String>
        val args = Bundle()
        args.putString(CHAPTER_INDEX, item.key)
        // show progressbar under actionbar
        mProgress.show()
        loaderManager.restartLoader(MainPagerActivity.CHAPTER_RETRIEVER_LOADER, args, mContentRetrieveCallback)
    }

    /**
     * Click listener for selecting a package from the list.
     * Usable only when list view shows list of packages.
     *
     * After picking a package a list of commands will show up that user can choose from.
     *
     * New instance of [com.adonai.manman.ManPageDialogFragment] is then created and shown
     * for loading full command man page.
     *
     */
    private val mPackageClickListener = OnItemClickListener { parent, view, position, id ->
        val item = parent.getItemAtPosition(position) as ManSectionItem
        val args = Bundle()
        args.putString(CHAPTER_INDEX, item.parentChapter)
        args.putString(CHAPTER_PACKAGE, item.url)
        // show progressbar under actionbar
        mProgress.show()
        loaderManager.restartLoader(MainPagerActivity.PACKAGE_RETRIEVER_LOADER, args, mPackageRetrieveCallback)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_man_contents, container, false)

        mCachedChapters = Utils.parseStringArray(requireContext(), R.array.man_page_chapters)
        mChaptersAdapter = ChaptersArrayAdapter(requireContext(), R.layout.chapters_list_item, R.id.chapter_index_label, ArrayList(mCachedChapters.entries))

        mListView = root.findViewById<View>(R.id.chapter_commands_list) as ListView
        mListView.adapter = mChaptersAdapter
        mListView.onItemClickListener = mChapterClickListener

        mFrame = root.findViewById<View>(R.id.chapter_fragment_frame) as FrameLayout
        mProgress = ProgressBarWrapper(requireActivity())

        loaderManager.initLoader(MainPagerActivity.CHAPTER_RETRIEVER_LOADER, Bundle.EMPTY, mContentRetrieveCallback)
        loaderManager.initLoader(MainPagerActivity.PACKAGE_RETRIEVER_LOADER, Bundle.EMPTY, mPackageRetrieveCallback)
        return root
    }

    /**
     * Loader callback for async loading of clicked chapter's contents and showing them in ListView afterwards
     * <br></br>
     * The data is retrieved from local database (if cached there) or from network (if not)
     *
     * @see ManSectionItem
     */
    private inner class RetrieveChapterContentsCallback : LoaderManager.LoaderCallbacks<ManPageContentsResult?> {

        override fun onCreateLoader(id: Int, args: Bundle?): Loader<ManPageContentsResult?> {
            return object : AbstractNetworkAsyncLoader<ManPageContentsResult?>(requireContext()) {

                override fun onStartLoading() {
                    if (args!!.containsKey(CHAPTER_INDEX)) {
                        super.onStartLoading()
                    }
                }

                /**
                 * Loads chapter page from DB or network asynchronously
                 *
                 * @return list of packages with their descriptions and urls
                 * or null on error/no input provided
                 */
                override fun loadInBackground(): ManPageContentsResult? {
                    // retrieve chapter content
                    val index = args!!.getString(CHAPTER_INDEX)!!

                    if (!isStarted) // task was cancelled
                        return null

                    // check the DB for cached pages first
                    try {
                        val query = DbProvider.helper.manChaptersDao.queryBuilder().orderBy("name", true).where().eq("parentChapter", index).prepare()
                        if (DbProvider.helper.manChaptersDao.queryForFirst(query) != null) // we have it in cache
                            return ManPageContentsResult(DbProvider.helper.manChaptersDao, query, index)
                    } catch (e: SQLException) {
                        Log.e(Utils.MM_TAG, "Exception while querying for cached pages", e)
                        Utils.showToastFromAnyThread(activity, R.string.database_retrieve_error)
                    }
                    if (!isStarted) // task was cancelled
                        return null

                    // If we're here, nothing is in DB for now
                    val results = loadFromNetwork(index, "$CHAPTER_COMMANDS_PREFIX/$index")
                    if (results.isNotEmpty()) {
                        results.sort()
                        saveToDb(results)
                        return ManPageContentsResult(results, index)
                    }

                    return null
                }

                private fun loadFromNetwork(index: String, link: String): MutableList<ManSectionItem> {
                    try {
                        // load chapter page with command links
                        val client = OkHttpClient()
                        val request = Request.Builder()
                                .header("Accept-Encoding", "gzip, deflate")
                                .url(link)
                                .build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            // count the bytes and show progress
                            val inStream: InputStream = if (response.header("Content-Length") != null) {
                                GZIPInputStream(
                                        CountingInputStream(response.body!!.byteStream(),
                                                response.body!!.contentLength().toInt()),
                                        response.body!!.contentLength().toInt())
                            } else {
                                GZIPInputStream(CountingInputStream(response.body!!.byteStream(),
                                        response.body!!.contentLength().toInt()))
                            }
                            val msItems: MutableList<ManSectionItem> = ArrayList(500)
                            val doc = Jsoup.parse(inStream, "UTF-8", link)
                            val rows = doc.select("div.section-index-content > table tr")
                            for (row in rows) {
                                msItems.add(sectionItemFromRow(index, row))
                            }
                            return msItems
                        }
                    } catch (e: Exception) {
                        Log.e(Utils.MM_TAG, "Exception while loading man pages from network", e)
                        // can't show a toast from a thread without looper
                        Utils.showToastFromAnyThread(activity, R.string.connection_error)
                    }
                    return mutableListOf()
                }

                private fun saveToDb(items: List<ManSectionItem>) {
                    if (!isStarted) // task was cancelled
                        return

                    // save to DB for caching
                    try {
                        TransactionManager.callInTransaction<Void>(DbProvider.helper.connectionSource) {
                            for (msi in items) {
                                DbProvider.helper.manChaptersDao.create(msi)
                            }
                            val indexes = Utils.createIndexer(items)
                            for (index in indexes) {
                                DbProvider.helper.manChapterIndexesDao.create(index)
                            }
                            null
                        }
                    } catch (e: SQLException) {
                        Log.e(Utils.MM_TAG, "Exception while saving cached page to DB", e)
                        // can't show a toast from a thread without looper
                        Utils.showToastFromAnyThread(activity, R.string.database_save_error)
                    }
                }

                override fun deliverResult(data: ManPageContentsResult?) {
                    mProgress.hide()
                    super.deliverResult(data)
                }
            }
        }

        override fun onLoadFinished(loader: Loader<ManPageContentsResult?>, data: ManPageContentsResult?) {
            if (data != null) { // if no error happened
                if (mListView.adapter is ChapterContentsCursorAdapter) {
                    // close opened cursor prior to adapter change
                    (mListView.adapter as ChapterContentsCursorAdapter).closeCursor()
                }
                mListView.isFastScrollEnabled = false
                mListView.adapter = null
                swapListView()
                if (data.choiceDbCache != null) {
                    mListView.adapter = ChapterContentsCursorAdapter(requireContext(), data.choiceDbCache.first, data.choiceDbCache.second, data.chapter)
                } else {
                    mListView.adapter = ChapterContentsArrayAdapter(requireContext(), R.layout.chapter_command_list_item, R.id.command_name_label, data.choiceList!!)
                }
                mListView.isFastScrollEnabled = true
                mListView.onItemClickListener = mPackageClickListener
                LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mBroadcastHandler, IntentFilter(MainPagerActivity.BACK_BUTTON_NOTIFY))
            }

            // don't start this loader again on resume, it's one-shot
            loaderManager.restartLoader(MainPagerActivity.CHAPTER_RETRIEVER_LOADER, Bundle.EMPTY, mPackageRetrieveCallback)
        }

        override fun onLoaderReset(loader: Loader<ManPageContentsResult?>) {}
    }

    /**
     * Loader callback for async loading of clicked package's contents and showing them in a dialog afterwards
     * <br></br>
     * The data is retrieved from local database (if cached there) or from network (if not)
     *
     * @see ManSectionItem
     */
    private inner class RetrievePackageContentsCallback : LoaderManager.LoaderCallbacks<List<ManSectionItem>> {
        override fun onCreateLoader(id: Int, args: Bundle?): AbstractNetworkAsyncLoader<List<ManSectionItem>> {
            return object : AbstractNetworkAsyncLoader<List<ManSectionItem>>(requireContext()) {
                override fun onStartLoading() {
                    if (args!!.containsKey(CHAPTER_INDEX)) {
                        super.onStartLoading()
                    }
                }

                /**
                 * Loads package page from network asynchronously
                 *
                 * @return list of commands with their descriptions and urls
                 * or null on error/no input provided
                 */
                override fun loadInBackground(): List<ManSectionItem>? {
                    // retrieve package content
                    val index = args!!.getString(CHAPTER_INDEX)!!
                    val url = args!!.getString(CHAPTER_PACKAGE)!!

                    if (!isStarted) // task was cancelled
                        return emptyList()
                    val client = OkHttpClient()
                    val request = Request.Builder().url(url).build()
                    try {
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val result = response.body!!.string()
                            val root = Jsoup.parse(result, CHAPTER_COMMANDS_PREFIX)
                            val rows = root.select(String.format("caption:has(a[href=/%s/]) ~ tbody > tr", index))
                            val manPages: MutableList<ManSectionItem> = ArrayList(rows.size)
                            for (row in rows) {
                                manPages.add(sectionItemFromRow(index, row))
                            }
                            return manPages
                        }
                    } catch (e: IOException) {
                        Log.e(Utils.MM_TAG, "Exception while parsing package page $url", e)
                        return emptyList()
                    }
                    return emptyList()
                }

                override fun deliverResult(data: List<ManSectionItem>?) {
                    mProgress.hide()
                    super.deliverResult(data)
                }
            }
        }

        override fun onLoadFinished(loader: Loader<List<ManSectionItem>>, data: List<ManSectionItem>) {
            // finished loading - show selector dialog to the user
            val adapter: ArrayAdapter<ManSectionItem> = ChapterContentsArrayAdapter(requireContext(), R.layout.package_command_list_item, R.id.command_name_label, data)
            AlertDialog.Builder(context!!)
                    .setTitle(R.string.select_command)
                    .setAdapter(adapter) { dialog, which ->
                        val item = adapter.getItem(which)
                        val mpdf = ManPageDialogFragment.newInstance(item!!.name, item.url)
                        parentFragmentManager
                                .beginTransaction()
                                .addToBackStack("PageFromChapterPackage")
                                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                                .replace(R.id.replacer, mpdf)
                                .commit()
                    }.create().show()

            // don't start this loader again on resume, it's one-shot
            loaderManager.restartLoader(MainPagerActivity.PACKAGE_RETRIEVER_LOADER, Bundle.EMPTY, mPackageRetrieveCallback)
        }

        override fun onLoaderReset(loader: Loader<List<ManSectionItem>>) {}
    }

    private fun sectionItemFromRow(chapterIndex: String, row: Element): ManSectionItem {
        val cells = row.select("td")
        val anchor = cells.first().child(0)
        val msi = ManSectionItem()
        msi.parentChapter = chapterIndex
        msi.name = anchor.text()
        msi.url = CHAPTER_COMMANDS_PREFIX + anchor.attr("href")
        msi.description = cells.last().text()
        return msi
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mProgress.hide() // always hide progressbar to avoid window leakage
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mProgress.onOrientationChanged()
    }

    override fun onPause() {
        super.onPause()
        // if we're pausing this fragment and have active listener, we should no longer receive back button feedback
        if (!userVisibleHint && mListView.onItemClickListener === mPackageClickListener) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mBroadcastHandler)
        }
    }

    override fun onResume() {
        super.onResume()
        // if we're resuming this fragment while in command list, we re-register to receive back button feedback
        if (userVisibleHint && mListView!!.onItemClickListener === mPackageClickListener) {
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mBroadcastHandler, IntentFilter(MainPagerActivity.BACK_BUTTON_NOTIFY))
        }
    }

    override fun onDestroy() { // if application is forcibly closed
        super.onDestroy()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mBroadcastHandler)
    }

    /**
     * Convenience class for counting progress in cases we have
     * exact length of what we want to receive
     *
     * @see java.io.FilterInputStream
     */
    private inner class CountingInputStream(inStream: InputStream, private val length: Int) : FilterInputStream(inStream) {
        private var transferred = 0
        private var shouldCount = true
        private var shouldWarn = true

        override fun read(buffer: ByteArray, byteOffset: Int, byteCount: Int): Int {
            val res = super.read(buffer, byteOffset, byteCount)
            if (shouldWarn) {
                shouldWarn = false
                if (length <= 0 || length > 25 shl 10) { // if no length provided or it's more than 25 kbytes
                    Utils.showToastFromAnyThread(activity, R.string.long_load_warn)
                }
            }
            if (shouldCount) {
                transferred += res
                if (activity != null) {
                    activity!!.runOnUiThread(Runnable {
                        if (length <= 0) { // if no length provided
                            stopCounting()
                            return@Runnable
                        }
                        val progress = transferred * 100 / length
                        if (progress == 100) { // download is complete
                            stopCounting()
                            return@Runnable
                        }
                        mProgress.setIndeterminate(false)
                        mProgress.setProgress(progress)
                    })
                }
            }
            return res
        }

        // don't count further, show only animation
        private fun stopCounting() {
            mProgress.setIndeterminate(true)
            shouldCount = false
        }
    }

    /**
     * Convenience class for selecting exclusively one of the result types
     * <br></br>
     * The first is for network load and the second is the DB retrieval
     *
     */
    private class ManPageContentsResult {
        val choiceList: List<ManSectionItem>? // from network
        val choiceDbCache : Pair<Dao<ManSectionItem, String>, PreparedQuery<ManSectionItem>>? // from DB
        val chapter: String

        constructor(choiceList: List<ManSectionItem>, chapter: String) {
            this.choiceList = choiceList
            choiceDbCache = null
            this.chapter = chapter
        }

        constructor(dao: Dao<ManSectionItem, String>, query: PreparedQuery<ManSectionItem>, chapter: String) {
            choiceDbCache = Pair.create(dao, query)
            choiceList = null
            this.chapter = chapter
        }
    }

    /**
     * Handler to receive notifications for back button press (to return list view to chapter show)
     */
    private inner class BackButtonBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mListView.adapter = mChaptersAdapter
            mListView.onItemClickListener = mChapterClickListener
            LocalBroadcastManager.getInstance(activity!!).unregisterReceiver(this)
        }
    }

    /**
     * Workaround for [this](http://stackoverflow.com/questions/20730301/android-refresh-listview-sections-overlay-not-working-in-4-4)
     * <br></br>
     * Swaps the list view prior to setting adapter to invalidate fast scroller
     */
    private fun swapListView() {
        //save layout params
        val listViewParams: ViewGroup.LayoutParams = mListView.layoutParams

        //frame is a FrameLayout around the ListView
        mFrame.removeView(mListView)
        mListView = ListView(activity)
        mListView.layoutParams = listViewParams
        //other ListView initialization code like divider settings
        mListView.divider = null
        mFrame.addView(mListView)
    }

    companion object {
        const val CHAPTER_INDEX = "chapter.index"
        const val CHAPTER_PACKAGE = "chapter.package"
        const val CHAPTER_COMMANDS_PREFIX = "https://www.mankier.com"
        fun newInstance(): ManChaptersFragment {
            val fragment = ManChaptersFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }
}