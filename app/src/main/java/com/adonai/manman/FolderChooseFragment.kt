package com.adonai.manman

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.adonai.manman.misc.FolderAddDialog
import java.util.*

/**
 * A dialog for showing and managing list of watched folders of local man archive.
 * Each folder is parsed recursively to retrieve list of man pages afterwards
 *
 * @see ManLocalArchiveFragment
 *
 * @author Kanedias
 */
class FolderChooseFragment : DialogFragment() {

    private lateinit var mAddButton: ImageView
    private lateinit var mFolderList: ListView

    private lateinit var mSharedPrefs: SharedPreferences
    private var mStoredFolders  = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mStoredFolders = HashSet()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // get already stored folders from prefs...
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        mStoredFolders.addAll(mSharedPrefs.getStringSet(MainPagerActivity.FOLDER_LIST_KEY, HashSet())!!)

        val title = View.inflate(requireContext(), R.layout.folder_list_dialog_title, null)
        val titleText = title.findViewById<View>(android.R.id.title) as TextView
        titleText.setText(R.string.watched_folders)

        mAddButton = title.findViewById<View>(R.id.add_local_folder) as ImageView
        mAddButton.setOnClickListener(AddFolderClickListener())

        mFolderList = ListView(requireContext())
        mFolderList.adapter = FolderListArrayAdapter(requireContext(), mStoredFolders.toTypedArray())

        val builder = AlertDialog.Builder(requireContext())
        builder.setCustomTitle(title)
        builder.setView(mFolderList)

        return builder.create()
    }

    private inner class AddFolderClickListener : View.OnClickListener {
        override fun onClick(v: View) {
            val folder = FolderAddDialog.newInstance {
                // add dir to the list
                mStoredFolders.add(it.absolutePath)
                syncFolderList()
            }
            folder.show(parentFragmentManager, "FolderChooseFragment")
        }
    }

    private inner class FolderListArrayAdapter(context: Context, objects: Array<String>) : ArrayAdapter<String?>(context, R.layout.folder_list_dialog_item, android.R.id.title, objects) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cached = super.getView(position, convertView, parent)
            val current = getItem(position)
            val img = cached.findViewById<View>(R.id.remove_local_folder) as ImageView
            img.setOnClickListener {
                mStoredFolders.remove(current)
                syncFolderList()
            }
            return cached
        }
    }

    /**
     * Should be called from UI thread...
     */
    private fun syncFolderList() {
        mFolderList.adapter = FolderListArrayAdapter(requireContext(), mStoredFolders.toTypedArray())

        // sync with shared prefs
        mSharedPrefs.edit().putStringSet(MainPagerActivity.FOLDER_LIST_KEY, mStoredFolders).apply()
    }
}