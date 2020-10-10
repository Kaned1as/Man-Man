package com.adonai.manman

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ListView
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.adonai.manman.adapters.CachedCommandsArrayAdapter
import com.adonai.manman.database.DbProvider
import com.adonai.manman.entities.ManPage
import com.adonai.manman.misc.AbstractNetworkAsyncLoader
import java.sql.SQLException

/**
 * Fragment to show cached man pages list
 * These pages can be viewed without touching the network
 *
 * @author Oleg Chernovskiy
 */
class ManCacheFragment : Fragment(), OnItemClickListener {
    private val mCacheBrowseCallback = CacheBrowseCallback()
    private val mBroadcastHandler: BroadcastReceiver = DbBroadcastReceiver()
    private lateinit var mSearchCache: SearchView
    private lateinit var mCacheList: ListView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_cache_browse, container, false)

        mSearchCache = root.findViewById<View>(R.id.cache_search_edit) as SearchView
        mSearchCache.setOnQueryTextListener(SearchInCacheListener())

        mCacheList = root.findViewById<View>(R.id.cached_pages_list) as ListView
        mCacheList.onItemClickListener = this

        loaderManager.initLoader(MainPagerActivity.CACHE_RETRIEVER_LOADER, null, mCacheBrowseCallback)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mBroadcastHandler, IntentFilter(MainPagerActivity.DB_CHANGE_NOTIFY))
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mBroadcastHandler)
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        mSearchCache!!.clearFocus() // otherwise we have to click "back" twice
        val manPage = parent.getItemAtPosition(position) as ManPage
        val mpdf = ManPageDialogFragment.newInstance(manPage.name, manPage.url)
        parentFragmentManager
                .beginTransaction()
                .addToBackStack("PageFromCache")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.replacer, mpdf)
                .commit()
    }

    /**
     * Callback for loading matching cache pages from database
     * Since count of cached pages is far less than total chapter contents, we can retrieve it all at once
     */
    private inner class CacheBrowseCallback : LoaderManager.LoaderCallbacks<List<ManPage>> {

        override fun onCreateLoader(i: Int, args: Bundle?): AbstractNetworkAsyncLoader<List<ManPage>> {
            return object : AbstractNetworkAsyncLoader<List<ManPage>>(requireContext()) {

                override fun loadInBackground(): List<ManPage> {
                    // check the DB for cached pages
                    try {
                        val query = DbProvider.helper.manPagesDao.queryBuilder().where().like("name", "%" + mSearchCache.query.toString() + "%").prepare()
                        return DbProvider.helper.manPagesDao.query(query)
                    } catch (e: SQLException) {
                        Log.e(Utils.MM_TAG, "Exception while querying DB for cached page", e)
                        Utils.showToastFromAnyThread(activity, R.string.database_retrieve_error)
                    }
                    return emptyList()
                }
            }
        }

        override fun onLoadFinished(objectLoader: Loader<List<ManPage>>, results: List<ManPage>) {
            mCacheList.adapter = CachedCommandsArrayAdapter(requireContext(), R.layout.chapter_command_list_item, R.id.command_name_label, results)
        }

        override fun onLoaderReset(objectLoader: Loader<List<ManPage>?>) {}
    }

    private inner class SearchInCacheListener : SearchView.OnQueryTextListener {
        private var currentText: String? = null
        override fun onQueryTextSubmit(query: String): Boolean {
            currentText = query
            fireLoader()
            return true
        }

        override fun onQueryTextChange(newText: String): Boolean {
            if (TextUtils.equals(currentText, newText)) return false
            currentText = newText
            fireLoader()
            return true
        }

        private fun fireLoader() {
            loaderManager.getLoader<Any>(MainPagerActivity.CACHE_RETRIEVER_LOADER)!!.onContentChanged()
        }
    }

    /**
     * Handler to receive notifications for changes in database (to update cache list view)
     */
    private inner class DbBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            loaderManager.getLoader<Any>(MainPagerActivity.CACHE_RETRIEVER_LOADER)!!.onContentChanged()
        }
    }

    companion object {
        fun newInstance(): ManCacheFragment {
            val fragment = ManCacheFragment()
            val args = Bundle()
            fragment.arguments = args
            return fragment
        }
    }
}