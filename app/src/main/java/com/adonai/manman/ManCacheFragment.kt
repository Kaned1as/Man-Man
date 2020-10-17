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
import android.widget.ListView
import android.widget.SearchView
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.adonai.manman.adapters.CachedCommandsArrayAdapter
import com.adonai.manman.database.DbProvider
import com.adonai.manman.entities.ManPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.SQLException

/**
 * Fragment to show cached man pages list
 * These pages can be viewed without touching the network
 *
 * @author Oleg Chernovskiy
 */
class ManCacheFragment : Fragment() {
    private val mBroadcastHandler: BroadcastReceiver = DbBroadcastReceiver()
    private lateinit var mSearchCache: SearchView
    private lateinit var mCacheList: ListView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_cache_browse, container, false)

        mSearchCache = root.findViewById<View>(R.id.cache_search_edit) as SearchView
        mSearchCache.setOnQueryTextListener(SearchInCacheListener())

        mCacheList = root.findViewById<View>(R.id.cached_pages_list) as ListView
        mCacheList.setOnItemClickListener { parent, view, position, id ->
            mSearchCache.clearFocus() // otherwise we have to click "back" twice
            val manPage = parent.getItemAtPosition(position) as ManPage
            val mpdf = ManPageDialogFragment.newInstance(manPage.name, manPage.url)
            parentFragmentManager
                    .beginTransaction()
                    .addToBackStack("PageFromCache")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.replacer, mpdf)
                    .commit()
        }

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mBroadcastHandler, IntentFilter(MainPagerActivity.DB_CHANGE_NOTIFY))
        triggerReloadCache()

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mBroadcastHandler)
    }

    @UiThread
    private fun triggerReloadCache() {
        val query = mSearchCache.query.toString()

        lifecycleScope.launch {
            val pages = withContext(Dispatchers.IO) { doReloadCache(query) }
            mCacheList.adapter = CachedCommandsArrayAdapter(requireContext(), R.layout.chapter_command_list_item, R.id.command_name_label, pages)
        }
    }

    @WorkerThread
    private fun doReloadCache(query: String): List<ManPage> {
        // check the DB for cached pages
        try {
            val query = DbProvider.helper.manPagesDao
                    .queryBuilder()
                    .where()
                    .like("name", "%${query}%")
                    .prepare()

            return DbProvider.helper.manPagesDao.query(query)
        } catch (e: SQLException) {
            Log.e(Utils.MM_TAG, "Exception while querying DB for cached page", e)
            Utils.showToastFromAnyThread(activity, R.string.database_retrieve_error)
        }

        return emptyList()
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
            triggerReloadCache()
        }
    }

    /**
     * Handler to receive notifications for changes in database (to update cache list view)
     */
    private inner class DbBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            triggerReloadCache()
        }
    }
}