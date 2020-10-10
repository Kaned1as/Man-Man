package com.adonai.manman.misc

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.adonai.manman.R
import java.io.File
import java.util.*

/**
 * A helper fragment responsible for picking folders of local manpage archives for further parsing
 * Returns selected folder on successful completion via ResultFolderListener interface
 *
 * @see com.adonai.manman.FolderChooseFragment
 *
 * @author Kanedias
 */
class FolderAddDialog : DialogFragment(), DialogInterface.OnClickListener, OnItemClickListener {
    interface ResultFolderListener {
        fun receiveResult(resultDir: File?)
    }

    private lateinit var mFolderList: ListView
    private lateinit var mFolderTitle: TextView

    private lateinit var pwd: File
    private lateinit var listener: (dir: File) -> Unit

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val external = Environment.getExternalStorageDirectory()
        pwd = if (external.exists() && external.canRead()) external else File("/")

        val folderSelector = View.inflate(activity, R.layout.folder_selector_dialog, null)
        mFolderList = folderSelector.findViewById<View>(R.id.folder_list) as ListView
        mFolderList.onItemClickListener = this

        mFolderTitle = folderSelector.findViewById<View>(R.id.folder_title) as TextView

        val builder = AlertDialog.Builder(activity)
        builder.setPositiveButton(R.string.select, this)
        builder.setNegativeButton(android.R.string.cancel, this)
        builder.setView(folderSelector)
        builder.setTitle(R.string.select_folder)

        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        cdInto(pwd)
    }

    private fun cdInto(currentDir: File) {
        mFolderTitle.text = getString(R.string.current_folder) + currentDir.path
        val shownFolders: MutableList<File> = ArrayList()

        val files = currentDir.listFiles()!!
        if (currentDir.parent != null) {
            shownFolders.add(currentDir.parentFile!!)
        }

        for (file in files) {
            if (file.isDirectory) {
                shownFolders.add(file)
            }
        }

        val fileList: ArrayAdapter<File> = FolderArrayAdapter(requireContext(), R.layout.folder_list_item, R.id.folder_item_title, shownFolders)
        mFolderList.adapter = fileList
        pwd = currentDir
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        when (which) {
            DialogInterface.BUTTON_POSITIVE -> {
                listener.invoke(pwd)
                return
            }
            else -> {
            }
        }
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val dir = parent.getItemAtPosition(position) as File
        if (dir.canRead()) cdInto(dir)
    }

    private inner class FolderArrayAdapter(context: Context, resource: Int, textViewResourceId: Int, objects: List<File>?) : ArrayAdapter<File>(context, resource, textViewResourceId, objects!!) {
        override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
            val current = getItem(pos)
            val view = convertView ?: View.inflate(context, R.layout.folder_list_item, null)
            val title = view.findViewById<View>(R.id.folder_item_title) as TextView
            if (pos == 0 && current!!.path == pwd.parent) {
                title.text = ".."
            } else {
                val relative = pwd.toURI().relativize(current!!.toURI()).path
                title.text = relative
            }
            return view
        }
    }

    companion object {
        fun newInstance(listener: (dir: File) -> Unit): FolderAddDialog {
            val fragment = FolderAddDialog()
            fragment.listener = listener
            return fragment
        }
    }
}