package com.adonai.manman

import android.annotation.SuppressLint
import android.app.ProgressDialog
import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import android.widget.SearchView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.adonai.manman.adapters.LocalArchiveArrayAdapter
import com.adonai.manman.misc.AbstractNetworkAsyncLoader
import com.adonai.manman.views.ProgressBarWrapper
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
    private val mLocalArchiveParseCallback = LocalArchiveParserCallback()

    private lateinit var mPreferences: SharedPreferences // needed for folder list retrieval
    private lateinit var mLocalPageList: ListView
    private lateinit var mSearchLocalPage: SearchView
    private lateinit var mProgress: ProgressBarWrapper

    private lateinit var mLocalArchive: File

    /**
     * Click listener for loading man page from selected archive file (or show config if no folders are present)
     * <br></br>
     * Archives are pretty small, so gzip decompression and parsing won't take loads of time...
     * <br></br>
     * Long story short, let's try to do this in UI and look at the performance
     *
     */
    private val mManArchiveClickListener = OnItemClickListener { parent, view, position, id ->
        mSearchLocalPage.clearFocus() // otherwise we have to click "back" twice

        val data = parent.getItemAtPosition(position) as? File
        if (data == null) { // header is present, start config tool
            when (position) {
                0 -> showFolderSettingsDialog()
                1 -> downloadArchive()
            }
        } else {
            val mpdf = ManPageDialogFragment.newInstance(data.name, data.path)
            parentFragmentManager
                    .beginTransaction()
                    .addToBackStack("PageFromLocalArchive")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.replacer, mpdf)
                    .commit()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setHasOptionsMenu(true)

        mLocalArchive = File(requireContext().cacheDir, "manpages.zip")
        mPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
        mPreferences.registerOnSharedPreferenceChangeListener(this)

        val root = inflater.inflate(R.layout.fragment_local_storage, container, false)
        mLocalPageList = root.findViewById<View>(R.id.local_storage_page_list) as ListView
        mSearchLocalPage = root.findViewById<View>(R.id.local_search_edit) as SearchView
        mProgress = ProgressBarWrapper(requireActivity())

        mLocalPageList.onItemClickListener = mManArchiveClickListener
        mSearchLocalPage.setOnQueryTextListener(FilterLocalStorage())

        loaderManager.initLoader(MainPagerActivity.LOCAL_PACKAGE_LOADER, null, mLocalArchiveParseCallback)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mBroadcastHandler, IntentFilter(MainPagerActivity.LOCAL_CHANGE_NOTIFY))
        return root
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mProgress.onOrientationChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mProgress.hide()
        mPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.local_archive_menu, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        // don't show it if we already have archive
        menu.findItem(R.id.download_archive).isVisible = !mLocalArchive!!.exists()
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
            loaderManager.getLoader<Any>(MainPagerActivity.LOCAL_PACKAGE_LOADER)!!.onContentChanged()
        }
    }

    private fun showFolderSettingsDialog() {
        FolderChooseFragment().show(parentFragmentManager, "FolderListFragment")
    }

    private inner class LocalArchiveParserCallback : LoaderManager.LoaderCallbacks<List<File>> {
        override fun onCreateLoader(i: Int, bundle: Bundle?): AbstractNetworkAsyncLoader<List<File>> {
            return object : AbstractNetworkAsyncLoader<List<File>>(requireContext()) {
                lateinit var mFolderList: Set<String>

                override fun loadInBackground(): List<File> {
                    // results from locally-defined folders
                    mFolderList = mPreferences.getStringSet(MainPagerActivity.FOLDER_LIST_KEY, HashSet())!!
                    val result: MutableList<File> = ArrayList()
                    for (path in mFolderList) {
                        val targetedFolder = File(path)
                        if (targetedFolder.exists() && targetedFolder.isDirectory) { // paranoid check, we already checked in dialog!
                            walkFileTree(targetedFolder, result)
                        }
                    }

                    // results from local archive, if exists
                    if (mLocalArchive.exists()) {
                        // show progress bar
                        activity!!.runOnUiThread { mProgress.show() }
                        // it's a tar-gzipped archive with standard structure
                        populateWithLocal(result)
                    }

                    // sort results alphabetically...
                    Collections.sort(result)
                    return result
                }

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

                private fun walkFileTree(directoryRoot: File, resultList: MutableList<File>) {
                    val list = directoryRoot.listFiles() ?: // unknown, happens on some devices
                    return
                    for (f in list) {
                        if (f.isDirectory) {
                            walkFileTree(f, resultList)
                        } else if (f.name.toLowerCase().endsWith(".gz")) { // take only gzipped files
                            resultList.add(f)
                        }
                    }
                }

                override fun deliverResult(data: List<File>?) {
                    mProgress.hide()
                    super.deliverResult(data)
                }
            }
        }

        override fun onLoadFinished(loader: Loader<List<File>>, manPageFiles: List<File>) {
            if (mLocalPageList.headerViewsCount > 0) {
                mLocalPageList.removeHeaderView(mLocalPageList.getChildAt(0))
                mLocalPageList.removeHeaderView(mLocalPageList.getChildAt(1))
            }

            mLocalPageList.adapter = null // for android < kitkat for header to work properly
            if (manPageFiles.isEmpty()) {
                mSearchLocalPage.visibility = View.GONE
                val header1 = View.inflate(activity, R.layout.add_folder_header, null)
                val header2 = View.inflate(activity, R.layout.load_archive_header, null)
                mLocalPageList.addHeaderView(header1)
                mLocalPageList.addHeaderView(header2)
            } else {
                mSearchLocalPage.visibility = View.VISIBLE
            }
            mLocalPageList.adapter = LocalArchiveArrayAdapter(requireContext(), R.layout.chapter_command_list_item, R.id.command_name_label, manPageFiles)
        }

        override fun onLoaderReset(loader: Loader<List<File>>) {}
    }

    /**
     * Load archive to app data folder from my GitHub releases page
     */
    private fun downloadArchive() {
        if (mLocalArchive!!.exists()) {
            return
        }
        if (!mUserAgreedToDownload) {
            AlertDialog.Builder(requireContext())
                    .setTitle(R.string.confirm_action)
                    .setMessage(R.string.confirm_action_load_archive)
                    .setPositiveButton(android.R.string.ok) { dialog, which ->
                        mUserAgreedToDownload = true
                        downloadArchive()
                    }.setNegativeButton(android.R.string.no, null)
                    .create().show()
            return
        }

        // kind of stupid to make a loader just for oneshot DL task...
        // OK, let's do it old way...
        val dlTask: AsyncTask<String, Long, Void> = @SuppressLint("StaticFieldLeak") object : AsyncTask<String, Long, Void>() {
            private var possibleEncountered: Exception? = null
            private var pd: ProgressDialog? = null
            override fun onPreExecute() {
                pd = ProgressDialog.show(activity,
                        getString(R.string.downloading),
                        getString(R.string.please_wait), true)
            }

            protected override fun doInBackground(vararg params: String): Void? {
                try {
                    val client = OkHttpClient()
                    val request = Request.Builder().url(params[0]).build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) {
                        publishProgress(-2L)
                        return null
                    }
                    val contentLength = response.body!!.contentLength()
                    val cis = CountingInputStream(response.body!!.byteStream())
                    val fos = FileOutputStream(mLocalArchive)
                    val buffer = ByteArray(8096)
                    var read: Int
                    while (cis.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        publishProgress(cis.bytesRead * 100 / contentLength)
                    }
                    fos.close()
                    cis.close()
                } catch (e: IOException) {
                    Log.e(Utils.MM_TAG, "Exception while downloading man pages archive", e)
                    possibleEncountered = e
                    publishProgress(-1L)
                }
                return null
            }

            override fun onProgressUpdate(vararg values: Long?) {
                if (values[0] == -1L) { // exception
                    Utils.showToastFromAnyThread(activity, possibleEncountered!!.localizedMessage)
                }
                if (values[0] == -2L) { // response is not OK
                    Utils.showToastFromAnyThread(activity, R.string.no_archive_on_server)
                }
                pd!!.setMessage(getString(R.string.please_wait) + " " + values[0] + "%")
            }

            override fun onPostExecute(aVoid: Void?) {
                pd!!.dismiss()
                loaderManager.getLoader<Any>(MainPagerActivity.LOCAL_PACKAGE_LOADER)!!.onContentChanged()
            }
        }
        dlTask.execute(LOCAL_ARCHIVE_URL)
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

        private fun applyFilter(text: CharSequence) {
            // safe to cast, we have only this type of adapter here
            val adapter = mLocalPageList.adapter as LocalArchiveArrayAdapter
            adapter.filter.filter(text)
        }
    }

    /**
     * Handler to receive notifications for changes in local archive (to update local list view)
     */
    private inner class LocalArchiveBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loaderManager.getLoader<Any>(MainPagerActivity.LOCAL_PACKAGE_LOADER)!!.onContentChanged()
        }
    }

    companion object {
        private const val LOCAL_ARCHIVE_URL = "https://github.com/Adonai/Man-Man/releases/download/1.6.0/manpages.zip"
        fun newInstance(): ManLocalArchiveFragment {
            val fragment = ManLocalArchiveFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }
}