package com.adonai.manman

import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.SearchView
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adonai.manman.ManLocalArchiveFragment.LocalArchiveAdapter.*
import com.adonai.manman.databinding.ChapterCommandListItemBinding
import com.adonai.manman.databinding.FragmentLocalStorageBinding
import com.adonai.manman.misc.showFullscreenFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.utils.CountingInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.zip.ZipFile

/**
 * Fragment for uploading and parsing local man page distributions
 *
 * @author Kanedias
 */
class ManLocalArchiveFragment : Fragment(), OnSharedPreferenceChangeListener {

    private var mUserAgreedToDownload = false
    private val mBroadcastHandler: BroadcastReceiver = LocalArchiveBroadcastReceiver()

    private lateinit var mPreferences: SharedPreferences // needed for folder list retrieval

    private lateinit var mLocalArchive: File

    private lateinit var binding: FragmentLocalStorageBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setHasOptionsMenu(true)

        mLocalArchive = File(requireContext().cacheDir, "manpages.zip")
        mPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        mPreferences.registerOnSharedPreferenceChangeListener(this)

        binding = FragmentLocalStorageBinding.inflate(inflater, container, false)

        binding.localStoragePageList.layoutManager = LinearLayoutManager(requireContext())
        binding.searchEdit.setOnQueryTextListener(FilterLocalStorage())

        triggerReloadLocalContent()
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mBroadcastHandler, IntentFilter(MainPagerActivity.LOCAL_CHANGE_NOTIFY))

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.local_archive_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        // don't show it if we already have archive
        menu.findItem(R.id.download_archive).isVisible = !mLocalArchive.exists()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.folder_settings -> {
                showFolderSettingsDialog()
                return true
            }
            R.id.download_archive -> {
                downloadArchive()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == MainPagerActivity.FOLDER_LIST_KEY) { // the only needed key
            triggerReloadLocalContent()
        }
    }

    @UiThread
    private fun triggerReloadLocalContent() {
        lifecycleScope.launch {
            val localPages = withContext(Dispatchers.IO) { doLoadContent() }

            if (localPages.isEmpty()) {
                binding.searchEdit.visibility = View.GONE
                binding.localStoragePageList.adapter = HeadersOnlyAdapter()
            } else {
                binding.searchEdit.visibility = View.VISIBLE
                binding.localStoragePageList.adapter = LocalArchiveAdapter(localPages)
            }
        }
    }

    @WorkerThread
    private fun doLoadContent(): List<File> {
        val collectedPages: MutableList<File> = ArrayList()

        // results from locally-defined folders
        val folderList = mPreferences.getStringSet(MainPagerActivity.FOLDER_LIST_KEY, HashSet())!!
        for (path in folderList) {
            val targetedFolder = File(path)
            if (targetedFolder.exists() && targetedFolder.isDirectory) {
                // paranoid check, we already checked in dialog!
                walkFileTree(targetedFolder, collectedPages)
            }
        }

        // results from local archive, if exists
        if (mLocalArchive.exists()) {
            // it's a tar-gzipped archive with standard structure
            populateWithLocal(collectedPages)
        }

        // sort results alphabetically...
        collectedPages.sortBy { it.name }
        collectedPages.sortBy { it.parentFile?.name }


        return collectedPages
    }

    @WorkerThread
    private fun populateWithLocal(result: MutableList<File>) {
        try {
            val zip = ZipFile(mLocalArchive)
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val zEntry = entries.nextElement()
                if (zEntry.isDirectory) continue
                result.add(File("local:", zEntry.name))
            }
        } catch (e: IOException) {
            Log.e(Utils.MM_TAG, "Exception while parsing local archive", e)
            Utils.showToastFromAnyThread(activity, R.string.error_parsing_local_archive)
        }
    }

    @WorkerThread
    private fun walkFileTree(directoryRoot: File, resultList: MutableList<File>) {
        val list = directoryRoot.listFiles() ?: return // unknown, happens on some devices
        for (f in list) {
            if (f.isDirectory) {
                walkFileTree(f, resultList)
            } else if (f.name.lowercase().endsWith(".gz")) { // take only gzipped files
                resultList.add(f)
            }
        }
    }

    private fun showFolderSettingsDialog() {
        FolderChooseFragment().show(parentFragmentManager, "FolderListFragment")
    }

    /**
     * Load archive to app data folder from my GitHub releases page
     */
    @UiThread
    private fun downloadArchive() {
        if (mLocalArchive.exists()) {
            return
        }
        if (!mUserAgreedToDownload) {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.confirm_action)
                    .setMessage(R.string.confirm_action_load_archive)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        mUserAgreedToDownload = true
                        downloadArchive()
                    }.setNegativeButton(android.R.string.no, null)
                    .create().show()
            return
        }

        lifecycleScope.launch {
            val pd = AlertDialog.Builder(requireContext())
                    .setTitle(R.string.downloading)
                    .setMessage(R.string.please_wait)
                    .create()

            pd.show()
            withContext(Dispatchers.IO) { doDownloadArchive(pd, LOCAL_ARCHIVE_URL) }
            pd.dismiss()
            triggerReloadLocalContent()
        }
    }

    @WorkerThread
    private suspend fun doDownloadArchive(pd: AlertDialog, archiveUrl: String) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(archiveUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                withContext(Dispatchers.Main) { Utils.showToastFromAnyThread(activity, R.string.no_archive_on_server) }
                return
            }

            val contentLength = response.body!!.contentLength()
            val cis = CountingInputStream(response.body!!.byteStream())
            FileOutputStream(mLocalArchive).use { fos ->
                val buffer = ByteArray(8096)
                var read: Int
                while (cis.read(buffer).also { read = it } != -1) {
                    fos.write(buffer, 0, read)
                    val downloadedPct = cis.bytesRead * 100 / contentLength
                    withContext(Dispatchers.Main) { pd.setMessage(getString(R.string.please_wait) + " ${downloadedPct}%") }
                }
            }
        } catch (e: IOException) {
            Log.e(Utils.MM_TAG, "Exception while downloading man pages archive", e)
            Utils.showToastFromAnyThread(activity, e.message)
        }
    }

    private inner class FilterLocalStorage : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String): Boolean {
            applyFilter(query)
            return true
        }

        override fun onQueryTextChange(newText: String): Boolean {
            applyFilter(newText)
            return true
        }

        private fun applyFilter(text: String) {
            val adapter = binding.localStoragePageList.adapter as? LocalArchiveAdapter
            adapter?.filter(text)
        }
    }

    inner class HeadersOnlyAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        val ITEM_LOCAL_FOLDERS = 0
        val ITEM_DOWNLOAD_ARCHIVE = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = when(viewType) {
                ITEM_LOCAL_FOLDERS -> inflater.inflate(R.layout.add_folder_header, parent, false)
                else /* ITEM_DOWNLOAD_ARCHIVE */ -> inflater.inflate(R.layout.load_archive_header, parent, false)
            }
            return object: RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (getItemViewType(position)) {
                ITEM_LOCAL_FOLDERS -> holder.itemView.setOnClickListener { showFolderSettingsDialog() }
                else /* ITEM_DOWNLOAD_ARCHIVE */ -> holder.itemView.setOnClickListener { downloadArchive() }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when(position) {
                0 -> ITEM_LOCAL_FOLDERS
                else /* 1 */ -> ITEM_DOWNLOAD_ARCHIVE
            }
        }

        override fun getItemCount() = 2

    }

    inner class LocalArchiveAdapter(val commands: List<File>) : RecyclerView.Adapter<LocalItemHolder>() {

        private var filteredCommands = commands

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocalItemHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ChapterCommandListItemBinding.inflate(inflater)
            return LocalItemHolder(binding)
        }

        override fun onBindViewHolder(holder: LocalItemHolder, position: Int) {
            val chapter = filteredCommands[position]
            holder.setup(chapter)
        }

        override fun getItemCount() = filteredCommands.size

        fun filter(text: String) {
            filteredCommands = commands.filter { it.name.startsWith(text.lowercase()) }
            notifyDataSetChanged()
        }


        inner class LocalItemHolder(private val item: ChapterCommandListItemBinding): RecyclerView.ViewHolder(item.root) {

            fun setup(localFile: File) {
                val localName = localFile.name
                    .replace(".gz", "")
                    .replace("\\.\\d\\w?$".toRegex(), "")

                item.commandNameLabel.text = localName
                item.commandDescriptionLabel.text = localFile.parent
                item.popupMenu.visibility = View.GONE

                item.root.setOnClickListener {
                    binding.searchEdit.clearFocus() // otherwise we have to click "back" twice
                    val mpdf = ManPageDialogFragment.newInstance(localFile.name, localFile.path)
                    requireActivity().showFullscreenFragment(mpdf)
                }
            }

        }
    }

    /**
     * Handler to receive notifications for changes in local archive (to update local list view)
     */
    private inner class LocalArchiveBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            triggerReloadLocalContent()
        }
    }

    companion object {
        private const val LOCAL_ARCHIVE_URL = "https://github.com/Adonai/Man-Man/releases/download/1.6.0/manpages.zip"
    }
}