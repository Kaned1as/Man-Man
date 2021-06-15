package com.adonai.manman

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adonai.manman.databinding.SearchListItemBinding
import com.adonai.manman.entities.SearchResult
import com.adonai.manman.entities.SearchResultList
import com.adonai.manman.misc.resolveAttr
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.*

/**
 * Fragment to show search results in a handy list view
 * All loaders for search content are implemented here
 *
 * @author Kanedias
 */
class ManPageSearchFragment : Fragment() {
    private val mJsonConverter = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    private lateinit var mSearchView: SearchView
    private lateinit var mSearchImage: ImageView
    private lateinit var mSearchDefaultDrawable: Drawable
    private lateinit var mSearchList: RecyclerView
    private lateinit var cachedChapters: Map<String, String>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        cachedChapters = Utils.parseStringArray(requireContext(), R.array.man_page_chapters)

        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_man_page_search, container, false)
        mSearchView = root.findViewById(R.id.search_edit)
        mSearchView.setOnQueryTextListener(SearchQueryTextListener())
        mSearchImage = mSearchView.findViewById(Resources.getSystem().getIdentifier("search_mag_icon", "id", "android"))
        mSearchDefaultDrawable = mSearchImage.drawable

        mSearchList = root.findViewById(R.id.search_results_list)
        mSearchList.layoutManager = LinearLayoutManager(requireContext())
        return root
    }

    private inner class SearchQueryTextListener : SearchView.OnQueryTextListener {

        private var currentText = ""
        private var queryJob: Job? = null

        override fun onQueryTextSubmit(query: String): Boolean {
            currentText = query
            fireLoader(true)
            return true
        }

        override fun onQueryTextChange(newText: String): Boolean {
            if (TextUtils.isEmpty(newText)) {
                currentText = newText
                queryJob?.cancel("No text entered")
                return true
            }

            if (TextUtils.equals(currentText, newText))
                return false

            currentText = newText
            fireLoader(false)
            return true
        }

        // make a delay for not spamming requests to server so fast
        @UiThread
        private fun fireLoader(immediate: Boolean) {
            queryJob?.cancel("Launching new search")
            queryJob = lifecycleScope.launch {
                if (!immediate)
                    delay(800)

                val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.wait)!!
                drawable.setTint(requireContext().resolveAttr(R.attr.colorAccent))
                mSearchImage.setImageResource(R.drawable.wait)

                val address = URLEncoder.encode(mSearchView.query.toString(), "UTF-8")
                val client = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(chain
                            .request()
                            .newBuilder()
                            .header("User-Agent", "Man Man ${BuildConfig.VERSION_NAME}")
                            .build())
                    }
                    .build()

                if (!currentText.contains(" ")) {
                    // this is a single command query, just search
                    val request = Request.Builder().url(SEARCH_COMMAND_PREFIX + address).build()
                    val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                    if (response.isSuccessful) {
                        val result = response.body!!.string()
                        val searchList = mJsonConverter.fromJson(result, SearchResultList::class.java)
                        mSearchList.adapter = SearchResultAdapter(searchList)
                    }
                } else {
                    // this is oneliner with arguments/other commands
                    val request = Request.Builder().url(SEARCH_ONE_LINER_PREFIX + address).build()
                    val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                    if (response.isSuccessful) {
                        val data = response.body!!.string()
                        val elements: Array<String> = data.split("\\n\\n".toRegex()).toTypedArray()
                        mSearchList.adapter = OnelinerResultAdapter(elements)
                    }
                }

                mSearchImage.setImageDrawable(mSearchDefaultDrawable) // finish animation
            }
        }
    }

    private inner class OnelinerResultAdapter(val data: Array<String>) : RecyclerView.Adapter<SearchResultHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = SearchListItemBinding.inflate(inflater)
            return SearchResultHolder(binding)
        }

        override fun onBindViewHolder(holder: SearchResultHolder, position: Int) {
            val command = data[position]
            holder.setup(command)
        }

        override fun getItemCount() = data.size
    }

    private inner class SearchResultAdapter(val data: SearchResultList) : RecyclerView.Adapter<SearchResultHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = SearchListItemBinding.inflate(inflater)
            return SearchResultHolder(binding)
        }

        override fun onBindViewHolder(holder: SearchResultHolder, position: Int) {
            val command = data.results[position]
            holder.setup(command)
        }

        override fun getItemCount() = data.results.size
    }

    companion object {
        private const val SEARCH_COMMAND_PREFIX = "https://www.mankier.com/api/v2/mans/?q="
        private const val SEARCH_ONE_LINER_PREFIX = "https://www.mankier.com/api/v2/explain/?cols=80&q="
        private const val SEARCH_DESCRIPTION_PREFIX = "https://www.mankier.com/api/v2/mans/"
    }

    private inner class SearchResultHolder(val binding: SearchListItemBinding): RecyclerView.ViewHolder(binding.root) {

        fun setup(freeFormText: String) {
            binding.commandNameLabel.visibility = View.GONE
            binding.commandChapterLabel.visibility = View.GONE
            binding.popupMenu.visibility = View.GONE
            binding.descriptionTextWeb.visibility = View.VISIBLE

            binding.descriptionTextWeb.text = freeFormText
        }

        fun setup(command: SearchResult) {
            binding.commandNameLabel.visibility = View.VISIBLE
            binding.commandChapterLabel.visibility = View.VISIBLE
            binding.popupMenu.visibility = View.VISIBLE
            binding.descriptionTextWeb.visibility = View.GONE

            val chapterName = cachedChapters[command.section]

            binding.commandNameLabel.text = command.name
            binding.commandChapterLabel.text = chapterName
            binding.descriptionTextWeb.text = command.description

            binding.root.setOnClickListener {
                mSearchView.clearFocus() // otherwise we have to click "back" twice
                val mpdf = ManPageDialogFragment.newInstance(command.name, command.url)
                parentFragmentManager
                    .beginTransaction()
                    .addToBackStack("PageFromSearch")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.replacer, mpdf)
                    .commit()
            }

            binding.popupMenu.visibility = View.VISIBLE
            binding.popupMenu.setOnClickListener {
                val pm = PopupMenu(activity, it)
                pm.inflate(R.menu.search_item_popup)

                pm.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.load_description_popup_menu_item -> {
                            binding.descriptionTextWeb.visibility = View.VISIBLE
                            return@OnMenuItemClickListener true
                        }
                        R.id.share_link_popup_menu_item -> {
                            val sendIntent = Intent(Intent.ACTION_SEND)
                            sendIntent.type = "text/plain"
                            sendIntent.putExtra(Intent.EXTRA_TITLE, command.name)
                            sendIntent.putExtra(Intent.EXTRA_TEXT, command.url)
                            startActivity(Intent.createChooser(sendIntent, getString(R.string.share_link)))
                            return@OnMenuItemClickListener true
                        }
                        R.id.copy_link_popup_menu_item -> {
                            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            Toast.makeText(requireContext(), "${getString(R.string.copied)} ${command.url}", Toast.LENGTH_SHORT).show()
                            clipboard.setPrimaryClip(ClipData.newPlainText(command.name, command.url))
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