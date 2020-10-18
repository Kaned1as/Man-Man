package com.adonai.manman.adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.SectionIndexer
import android.widget.TextView
import com.adonai.manman.R
import com.adonai.manman.ManChaptersFragment
import com.adonai.manman.Utils
import com.adonai.manman.entities.ManSectionIndex
import com.adonai.manman.entities.ManSectionItem
import com.adonai.manman.misc.ManChapterItemOnClickListener

/**
 * Array adapter for showing commands with their description in ListView
 * It's convenient whet all the data is retrieved via network,
 * so we have complete command list at hand
 *
 * The data retrieval is done through [ManChaptersFragment.loadChapterFromNetwork]
 *
 * @see ArrayAdapter
 * @see ManSectionItem
 *
 * @author Kanedias
 */
class ChapterContentsArrayAdapter(context: Context, resource: Int, textViewResourceId: Int, objects: List<ManSectionItem>) : ArrayAdapter<ManSectionItem>(context, resource, textViewResourceId, objects), SectionIndexer {

    private val indexes: List<ManSectionIndex> = Utils.createIndexer(objects)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val root = super.getView(position, convertView, parent)
        val current = getItem(position)

        val command = root.findViewById<View>(R.id.command_name_label) as TextView
        val desc = root.findViewById<View>(R.id.command_description_label) as TextView
        val moreActions = root.findViewById<View>(R.id.popup_menu) as ImageView

        command.text = current!!.name
        desc.text = current.description
        moreActions.setOnClickListener(ManChapterItemOnClickListener(context, current))

        return root
    }

    override fun getSections(): Array<Char> {
        val chars = CharArray(indexes.size)
        for (i in indexes.indices) {
            chars[i] = indexes[i].letter
        }
        return chars.toTypedArray()
    }

    override fun getPositionForSection(sectionIndex: Int): Int {
        return indexes[sectionIndex].index
    }

    override fun getSectionForPosition(position: Int): Int {
        for (i in indexes.indices) {
            if (indexes[i].index > position) return i - 1
        }
        return 0
    }

}