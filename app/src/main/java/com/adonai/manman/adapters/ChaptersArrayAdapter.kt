package com.adonai.manman.adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.adonai.manman.R

/**
 * This class represents an array adapter for showing man chapters
 * There are only about ten constant chapters, so it was convenient to place it to the string-array
 * <br></br>
 * The array is retrieved via [com.adonai.manman.Utils.parseStringArray]
 * and stored in [com.adonai.manman.ManChaptersFragment.mCachedChapters]
 *
 * @author Oleg Chernovskiy
 */
class ChaptersArrayAdapter(context: Context, resource: Int, textViewResourceId: Int, objects: List<Map.Entry<String, String>>) : ArrayAdapter<Map.Entry<String, String>>(context, resource, textViewResourceId, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val root = super.getView(position, convertView, parent)
        val current = getItem(position)

        val index = root.findViewById<View>(R.id.chapter_index_label) as TextView
        val name = root.findViewById<View>(R.id.chapter_name_label) as TextView

        index.text = current!!.key
        name.text = current.value

        return root
    }
}