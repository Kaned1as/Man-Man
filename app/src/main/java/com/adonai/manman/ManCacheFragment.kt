package com.adonai.manman

import android.content.*
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SearchView
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adonai.manman.ManCacheFragment.CacheAdapter.*
import com.adonai.manman.database.DbProvider
import com.adonai.manman.databinding.ChapterCommandListItemBinding
import com.adonai.manman.databinding.FragmentCacheBrowseBinding
import com.adonai.manman.entities.ManPage
import com.adonai.manman.misc.showFullscreenFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.SQLException

/**
 * Fragment to show cached man pages list
 * These pages can be viewed without touching the network
 *
 * @author Kanedias
 */
class ManCacheFragment : Fragment() {

    private val mBroadcastHandler: BroadcastReceiver = DbBroadcastReceiver()

    private lateinit var binding: FragmentCacheBrowseBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentCacheBrowseBinding.inflate(inflater, container, false)
        binding.searchEdit.setOnQueryTextListener(SearchInCacheListener())
        binding.cachedPagesList.layoutManager = LinearLayoutManager(requireContext())

        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mBroadcastHandler, IntentFilter(MainPagerActivity.DB_CHANGE_NOTIFY))
        triggerReloadCache()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mBroadcastHandler)
    }

    @UiThread
    private fun triggerReloadCache() {
        val query = binding.searchEdit.query.toString()

        lifecycleScope.launch {
            val pages = withContext(Dispatchers.IO) { doReloadCache(query) }
            binding.cachedPagesList.adapter = CacheAdapter(pages)
        }
    }

    @WorkerThread
    private fun doReloadCache(query: String): List<ManPage> {
        // check the DB for cached pages
        try {
            val prepared = DbProvider.helper.manPagesDao
                    .queryBuilder()
                    .where()
                    .like("name", "%${query}%")
                    .prepare()

            return DbProvider.helper.manPagesDao.query(prepared)
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

    inner class CacheAdapter(val cache: List<ManPage>) : RecyclerView.Adapter<CacheCommandHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CacheCommandHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ChapterCommandListItemBinding.inflate(inflater)
            return CacheCommandHolder(binding)
        }

        override fun onBindViewHolder(holder: CacheCommandHolder, position: Int) {
            val manpage = cache[position]
            holder.setup(manpage)
        }

        override fun getItemCount() = cache.size

        inner class CacheCommandHolder(private val item: ChapterCommandListItemBinding): RecyclerView.ViewHolder(item.root) {

            fun setup(manpage: ManPage) {
                item.commandNameLabel.text = manpage.name
                item.commandDescriptionLabel.text = manpage.url

                item.root.setOnClickListener {
                    binding.searchEdit.clearFocus() // otherwise we have to click "back" twice
                    val mpdf = ManPageDialogFragment.newInstance(manpage.name, manpage.url)
                    requireActivity().showFullscreenFragment(mpdf)
                }

                item.popupMenu.setOnClickListener { v ->
                    val pm = PopupMenu(context, v)
                    pm.inflate(R.menu.cached_item_popup)
                    pm.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.share_link_popup_menu_item -> {
                                val sendIntent = Intent(Intent.ACTION_SEND)
                                sendIntent.type = "text/plain"
                                sendIntent.putExtra(Intent.EXTRA_TITLE, manpage.name)
                                sendIntent.putExtra(Intent.EXTRA_TEXT, manpage.url)
                                startActivity(Intent.createChooser(sendIntent, getString(R.string.share_link)))
                                return@OnMenuItemClickListener true
                            }
                            R.id.copy_link_popup_menu_item -> {
                                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                Toast.makeText(requireContext(), getString(R.string.copied) + " " + manpage.url, Toast.LENGTH_SHORT).show()
                                clipboard.setPrimaryClip(ClipData.newPlainText(manpage.name, manpage.url))
                                return@OnMenuItemClickListener true
                            }
                            R.id.delete_popup_menu_item -> {
                                DbProvider.helper.manPagesDao.delete(manpage)
                                LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(Intent(MainPagerActivity.DB_CHANGE_NOTIFY))
                                return@OnMenuItemClickListener true
                            }
                        }
                        false
                    })
                    pm.show()
                }
            }
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