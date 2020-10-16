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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import com.adonai.manman.ManPageDialogFragment.Companion.newInstance
import com.adonai.manman.entities.SearchResult
import com.adonai.manman.entities.SearchResultList
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
    private lateinit var mSearchList: ListView
    private lateinit var cachedChapters: Map<String, String>

    /**
     * Click listener for loading man-page of the clicked command
     * Usable only when list view shows list of commands
     */
    private val mCommandClickListener = OnItemClickListener { parent, view, position, id ->
        mSearchView.clearFocus() // otherwise we have to click "back" twice
        val sr = parent.getItemAtPosition(position) as SearchResult
        val mpdf = newInstance(sr.name, sr.url)
        parentFragmentManager
                .beginTransaction()
                .addToBackStack("PageFromSearch")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.replacer, mpdf)
                .commit()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        cachedChapters = Utils.parseStringArray(activity, R.array.man_page_chapters)

        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_man_page_search, container, false)
        mSearchView = root.findViewById<View>(R.id.query_edit) as SearchView
        mSearchView.setOnQueryTextListener(SearchQueryTextListener())
        mSearchImage = mSearchView.findViewById<View>(Resources.getSystem().getIdentifier("search_mag_icon", "id", "android")) as ImageView
        mSearchDefaultDrawable = mSearchImage.drawable

        mSearchList = root.findViewById<View>(R.id.search_results_list) as ListView
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
        private fun fireLoader(immediate: Boolean) {
            queryJob?.cancel("Launching new search")
            queryJob = lifecycleScope.launch {
                if (!immediate)
                    delay(800)

                mSearchImage.setImageResource(Utils.getThemedResource(activity, R.attr.loading_icon_resource))

                val address = URLEncoder.encode(mSearchView.query.toString(), "UTF-8")
                val client = OkHttpClient()

                if (!currentText.contains(" ")) {
                    // this is a single command query, just search
                    val request = Request.Builder().url(SEARCH_COMMAND_PREFIX + address).build()
                    val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                    if (response.isSuccessful) {
                        val result = response.body!!.string()
                        val searchList = mJsonConverter.fromJson(result, SearchResultList::class.java)
                        mSearchList.adapter = SearchResultArrayAdapter(searchList)
                        mSearchList.onItemClickListener = mCommandClickListener
                    }
                } else {
                    // this is oneliner with arguments/other commands
                    val request = Request.Builder().url(SEARCH_ONE_LINER_PREFIX + address).build()
                    val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                    if (response.isSuccessful) {
                        val data = response.body!!.string()
                        val elements: Array<String> = data.split("\\n\\n".toRegex()).toTypedArray()
                        mSearchList.adapter = OnelinerArrayAdapter(elements)
                        mSearchList.onItemClickListener = null
                    }
                }

                mSearchImage.setImageDrawable(mSearchDefaultDrawable) // finish animation
            }
        }
    }

    private inner class OnelinerArrayAdapter(objects: Array<String>) : ArrayAdapter<String?>(requireContext(), R.layout.search_list_item, R.id.command_name_label, objects) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val root = super.getView(position, convertView, parent)
            val paragraph = getItem(position)
            val command = root.findViewById<View>(R.id.command_name_label) as TextView
            val chapter = root.findViewById<View>(R.id.command_chapter_label) as TextView
            val moreActions = root.findViewById<View>(R.id.popup_menu) as ImageView

            command.visibility = View.GONE
            chapter.text = paragraph
            moreActions.visibility = View.GONE

            return root
        }
    }

    private inner class SearchResultArrayAdapter(data: SearchResultList) : ArrayAdapter<SearchResult>(requireContext(), R.layout.search_list_item, R.id.command_name_label, data.results) {

        private val cachedDescs: MutableMap<SearchResult, String> = HashMap(5)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val root = super.getView(position, convertView, parent)
            val searchRes = getItem(position)
            val chapterName = cachedChapters[searchRes!!.section]
            val command = root.findViewById<View>(R.id.command_name_label) as TextView
            val chapter = root.findViewById<View>(R.id.command_chapter_label) as TextView
            val description = root.findViewById<View>(R.id.description_text_web) as TextView

            command.text = searchRes.name
            chapter.text = chapterName
            description.setBackgroundColor(0)
            description.visibility = if (cachedDescs.containsKey(searchRes)) View.VISIBLE else View.GONE
            if (cachedDescs.containsKey(searchRes)) {
                description.text = cachedDescs[searchRes]
            }

            // download a description on question mark click
            val descriptionRequest = root.findViewById<View>(R.id.popup_menu) as ImageView
            descriptionRequest.setOnClickListener { v ->
                val pm = PopupMenu(activity, v)
                pm.inflate(R.menu.search_item_popup)
                if (cachedDescs.containsKey(searchRes)) { // hide description setting if we already loaded it
                    pm.menu.findItem(R.id.load_description_popup_menu_item).isVisible = false
                }
                pm.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.load_description_popup_menu_item -> {
                            description.visibility = View.VISIBLE
                            description.text = searchRes.description
                            cachedDescs[searchRes] = searchRes.description
                            return@OnMenuItemClickListener true
                        }
                        R.id.share_link_popup_menu_item -> {
                            val sendIntent = Intent(Intent.ACTION_SEND)
                            sendIntent.type = "text/plain"
                            sendIntent.putExtra(Intent.EXTRA_TITLE, searchRes.name)
                            sendIntent.putExtra(Intent.EXTRA_TEXT, searchRes.url)
                            startActivity(Intent.createChooser(sendIntent, getString(R.string.share_link)))
                            return@OnMenuItemClickListener true
                        }
                        R.id.copy_link_popup_menu_item -> {
                            val clipboard = activity!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            Toast.makeText(activity!!.applicationContext, getString(R.string.copied) + " " + searchRes.url, Toast.LENGTH_SHORT).show()
                            clipboard.setPrimaryClip(ClipData.newPlainText(searchRes.name, searchRes.url))
                            return@OnMenuItemClickListener true
                        }
                    }
                    false
                })
                pm.show()
            }
            return root
        }
    }

    companion object {
        private const val SEARCH_COMMAND_PREFIX = "https://www.mankier.com/api/v2/mans/?q="
        private const val SEARCH_ONE_LINER_PREFIX = "https://www.mankier.com/api/v2/explain/?cols=80&q="
        private const val SEARCH_DESCRIPTION_PREFIX = "https://www.mankier.com/api/v2/mans/"
    }
}