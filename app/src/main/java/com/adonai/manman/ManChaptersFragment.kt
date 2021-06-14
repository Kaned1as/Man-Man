package com.adonai.manman

import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adonai.manman.adapters.ChapterContentsArrayAdapter
import com.adonai.manman.database.DbProvider
import com.adonai.manman.databinding.ChapterCommandListItemBinding
import com.adonai.manman.databinding.ChaptersListItemBinding
import com.adonai.manman.databinding.FragmentManContentsBinding
import com.adonai.manman.entities.ManSectionItem
import com.adonai.manman.misc.ManChapterItemOnClickListener
import com.google.android.material.internal.ViewUtils
import com.j256.ormlite.misc.TransactionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException
import java.sql.SQLException
import java.util.*
import java.util.zip.GZIPInputStream
import kotlin.math.min

/**
 * Fragment to show table of contents and navigate into it
 * Note: works slower than just search!
 *
 * @author Kanedias
 */
class ManChaptersFragment : Fragment() {

    companion object {
        const val CHAPTER_COMMANDS_PREFIX = "https://www.mankier.com"
    }

    private val mBroadcastHandler: BroadcastReceiver = BackButtonBroadcastReceiver()

    private lateinit var binding: FragmentManContentsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentManContentsBinding.inflate(inflater, container, false)

        val cachedChapters = Utils.parseStringArray(requireContext(), R.array.man_page_chapters)
        val chaptersAdapter = ChaptersAdapter(cachedChapters.map { ManChapter(it.key, it.value) })

        binding.chapterList.layoutManager = LinearLayoutManager(requireContext())
        binding.chapterList.adapter = chaptersAdapter

        binding.chapterCommandsList.layoutManager = LinearLayoutManager(requireContext())

        return binding.root
    }

    @UiThread
    private fun triggerLoadChapter(index: String) {
        lifecycleScope.launch {
            val chapter = withContext(Dispatchers.IO) { doLoadChapter(index) } ?: return@launch

            binding.chapterCommandsList.adapter = PackageAdapter(chapter.packages)
            binding.chapterCommandsList.scrollToPosition(0)
            FastScrollerBuilder(binding.chapterCommandsList)
                .setPopupTextProvider { idx ->
                    val cmdName = chapter.packages[idx].name
                    cmdName.substring(0, min(cmdName.length, 2))
                }
                .setPopupStyle {  }
                .useMd2Style()
                .build()
            binding.chapterContentsFlipper.showNext()

            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mBroadcastHandler, IntentFilter(MainPagerActivity.BACK_BUTTON_NOTIFY))
        }
    }

    @WorkerThread
    private fun doLoadChapter(index: String): ManPageContentsResult? {
        // check the DB for cached pages first
        val savedChapter = DbProvider.helper.manChaptersDao.queryBuilder()
                .orderBy("name", true)
                .where().eq("parentChapter", index)
                .query()

        if (savedChapter.isNotEmpty()) {
            // we have it in the cache
            return ManPageContentsResult(savedChapter, index)
        }

        // If we're here, nothing is in DB for now
        val results = loadChapterFromNetwork(index, "$CHAPTER_COMMANDS_PREFIX/$index")
        if (results.isNotEmpty()) {
            results.sort()
            saveChapterToDb(results)
            return ManPageContentsResult(results, index)
        }

        return null
    }

    @WorkerThread
    private fun loadChapterFromNetwork(index: String, link: String): MutableList<ManSectionItem> {
        try {
            // load chapter page with command links
            val client = OkHttpClient()
            val request = Request.Builder()
                    .header("Accept-Encoding", "gzip, deflate")
                    .url(link)
                    .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                GZIPInputStream(response.body!!.byteStream()).use { dlStream ->
                    val msItems: MutableList<ManSectionItem> = ArrayList(500)
                    val doc = Jsoup.parse(dlStream, "UTF-8", link)
                    val rows = doc.select("div.section-index-content > table tr")
                    for (row in rows) {
                        msItems.add(sectionItemFromRow(index, row))
                    }
                    return msItems
                }
            }
        } catch (e: Exception) {
            Log.e(Utils.MM_TAG, "Exception while loading man pages from network", e)
            // can't show a toast from a thread without looper
            Utils.showToastFromAnyThread(activity, R.string.connection_error)
        }

        return mutableListOf()
    }

    @WorkerThread
    private fun saveChapterToDb(items: List<ManSectionItem>) {
        // save to DB for caching
        try {
            TransactionManager.callInTransaction<Void>(DbProvider.helper.connectionSource) {
                for (msi in items) {
                    DbProvider.helper.manChaptersDao.createOrUpdate(msi)
                }
                null
            }
        } catch (e: SQLException) {
            Log.e(Utils.MM_TAG, "Exception while saving cached page to DB", e)
            // can't show a toast from a thread without looper
            Utils.showToastFromAnyThread(activity, R.string.database_save_error)
        }
    }

    @UiThread
    private fun triggerLoadPackage(parentChapter: String, url: String) {
        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) { doLoadPackage(parentChapter, url) }

            // finished loading - show selector dialog to the user
            val adapter = ChapterContentsArrayAdapter(requireContext(), R.layout.package_command_list_item, R.id.command_name_label, items)
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.select_command)
                    .setAdapter(adapter) { _, which ->
                        val item = adapter.getItem(which)
                        val mpdf = ManPageDialogFragment.newInstance(item!!.name, item.url)
                        parentFragmentManager
                                .beginTransaction()
                                .addToBackStack("PageFromChapterPackage")
                                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                                .replace(R.id.replacer, mpdf)
                                .commit()
                    }
                    .create()
                    .show()
        }
    }

    @WorkerThread
    private fun doLoadPackage(index: String, url: String): List<ManSectionItem> {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val result = response.body!!.string()
                val root = Jsoup.parse(result, CHAPTER_COMMANDS_PREFIX)
                val rows = root.select(String.format("table.package-mans tr", index))
                val manPages: MutableList<ManSectionItem> = ArrayList(rows.size)
                for (row in rows) {
                    if (!row.select("td.td-heading").isEmpty()) {
                        // that's a heading, skip
                        continue
                    }

                    manPages.add(sectionItemFromRow(index, row))
                }
                return manPages
            }
        } catch (e: IOException) {
            Log.e(Utils.MM_TAG, "Exception while parsing package page $url", e)
        }
        return emptyList()
    }

    private fun sectionItemFromRow(chapterIndex: String, row: Element): ManSectionItem {
        val name = row.select("td").first()
        val anchor = row.select("td a[href]").first()
        val desc = row.select("td").last()

        val msi = ManSectionItem()
        msi.parentChapter = chapterIndex
        msi.name = name.text()
        msi.url = CHAPTER_COMMANDS_PREFIX + anchor.attr("href")
        msi.description = desc.text()
        return msi
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mBroadcastHandler)
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mBroadcastHandler, IntentFilter(MainPagerActivity.BACK_BUTTON_NOTIFY))
    }

    override fun onDestroy() { // if application is forcibly closed
        super.onDestroy()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mBroadcastHandler)
    }

    /**
     * Convenience class for selecting exclusively one of the result types
     * <br></br>
     * The first is for network load and the second is the DB retrieval
     *
     */
    data class ManPageContentsResult(val packages: List<ManSectionItem>, val chapter: String)

    /**
     * Handler to receive notifications for back button press (to return list view to chapter show)
     */
    private inner class BackButtonBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            binding.chapterContentsFlipper.showPrevious()
            LocalBroadcastManager.getInstance(requireActivity()).unregisterReceiver(this)
        }
    }

    data class ManChapter(val index: String,val name: String)

    /**
     * This class represents an array adapter for showing man chapters
     * There are only about ten constant chapters, so it was convenient to place it to the string-array
     *
     * The array is retrieved via [Utils.parseStringArray]
     * and stored in [ManChaptersFragment.mCachedChapters]
     *
     * @author Kanedias
     */
    inner class ChaptersAdapter(val chapters: List<ManChapter>) : RecyclerView.Adapter<ChaptersAdapter.ChapterHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ChaptersListItemBinding.inflate(inflater)
            return ChapterHolder(binding)
        }

        override fun onBindViewHolder(holder: ChapterHolder, position: Int) {
            val chapter = chapters[position]
            holder.setup(chapter)
        }

        override fun getItemCount() = chapters.size

        inner class ChapterHolder(private val binding: ChaptersListItemBinding): RecyclerView.ViewHolder(binding.root) {

            fun setup(chapter: ManChapter) {
                binding.chapterIndexLabel.text = chapter.index
                binding.chapterNameLabel.text = chapter.name

                binding.root.setOnClickListener {
                    triggerLoadChapter(chapter.index)
                }
            }

        }
    }

    inner class PackageAdapter(val commands: List<ManSectionItem>) : RecyclerView.Adapter<PackageAdapter.PackageHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackageHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ChapterCommandListItemBinding.inflate(inflater)
            return PackageHolder(binding)
        }

        override fun onBindViewHolder(holder: PackageHolder, position: Int) {
            val chapter = commands[position]
            holder.setup(chapter)
        }

        override fun getItemCount() = commands.size

        inner class PackageHolder(private val binding: ChapterCommandListItemBinding): RecyclerView.ViewHolder(binding.root) {

            fun setup(pkg: ManSectionItem) {
                binding.commandNameLabel.text = pkg.name
                binding.commandDescriptionLabel.text = pkg.description
                binding.popupMenu.setOnClickListener(ManChapterItemOnClickListener(requireContext(), pkg))

                binding.root.setOnClickListener {
                    triggerLoadPackage(pkg.parentChapter, pkg.url)
                }
            }

        }
    }
}