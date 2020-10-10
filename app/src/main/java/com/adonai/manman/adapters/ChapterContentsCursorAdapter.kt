package com.adonai.manman.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SectionIndexer
import android.widget.TextView
import com.adonai.manman.R
import com.adonai.manman.database.DbProvider
import com.adonai.manman.entities.ManSectionIndex
import com.adonai.manman.entities.ManSectionItem
import com.adonai.manman.misc.ManChapterItemOnClickListener
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.stmt.PreparedQuery

/**
 * Cursor adapter for showing large lists of commands from DB
 * For example, General commands chapter has about 14900 ones
 * so we should load only a window of those
 * <br></br>
 * The data retrieval is done through [com.adonai.manman.ManChaptersFragment.RetrieveContentsCallback]
 *
 * @see OrmLiteCursorAdapter
 *
 * @author Kanedias
 */
class ChapterContentsCursorAdapter(context: Context, dao: Dao<ManSectionItem, String>, query: PreparedQuery<ManSectionItem>, chapter: String) : OrmLiteCursorAdapter<ManSectionItem>(context, dao, query), SectionIndexer {

    private val indexes: List<ManSectionIndex> = DbProvider.helper.manChapterIndexesDao.queryForEq("parentChapter", chapter)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(mContext).inflate(R.layout.chapter_command_list_item, parent, false)
        val current = getItem(position)

        val command = view.findViewById<View>(R.id.command_name_label) as TextView
        val desc = view.findViewById<View>(R.id.command_description_label) as TextView
        val moreActions = view.findViewById<View>(R.id.popup_menu) as ImageView

        command.text = current.name
        desc.text = current.description
        moreActions.setOnClickListener(ManChapterItemOnClickListener(mContext, current))

        return view
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