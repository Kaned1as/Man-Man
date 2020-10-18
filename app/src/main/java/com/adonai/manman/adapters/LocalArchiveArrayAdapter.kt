package com.adonai.manman.adapters

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ImageView
import android.widget.TextView
import com.adonai.manman.R
import com.adonai.manman.ManLocalArchiveFragment
import java.io.File
import java.util.*

/**
 * Array adapter for showing files in local man page archive
 * The data retrieval is done through [ManLocalArchiveFragment.doLoadContent]
 *
 * @see ArrayAdapter
 * @see File
 *
 * @author Kanedias
 */
class LocalArchiveArrayAdapter(context: Context, resource: Int, textViewResourceId: Int, private val originals: List<File>) : ArrayAdapter<File>(context, resource, textViewResourceId, originals) {

    private var filtered: List<File> = originals

    override fun getCount(): Int {
        return filtered.size
    }

    override fun getItem(position: Int): File {
        return filtered[position]
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val root = super.getView(position, convertView, parent)
        val current = getItem(position)
        val effectiveName = current.name.replace("\\.gz$".toRegex(), "").replace("\\.\\d$".toRegex(), "")

        val command = root.findViewById<View>(R.id.command_name_label) as TextView
        val url = root.findViewById<View>(R.id.command_description_label) as TextView
        val popup = root.findViewById<View>(R.id.popup_menu) as ImageView

        command.text = effectiveName
        url.text = current.parent
        popup.visibility = View.GONE // save for future, hide for now
        return root
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence): FilterResults {
                val fr = FilterResults()

                if (TextUtils.isEmpty(constraint)) { // special case for empty filter
                    fr.values = originals
                    fr.count = originals.size
                    return fr
                }

                val tempFilteredValues: MutableList<File> = ArrayList()
                for (f in originals) {
                    if (f.name.startsWith(constraint.toString())) {
                        tempFilteredValues.add(f)
                    }
                }

                fr.values = tempFilteredValues
                fr.count = tempFilteredValues.size
                return fr
            }

            override fun publishResults(constraint: CharSequence, results: FilterResults) {
                filtered = results.values as List<File>
                notifyDataSetChanged()
            }
        }
    }
}