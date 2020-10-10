package com.adonai.manman.adapters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.adonai.manman.MainPagerActivity
import com.adonai.manman.ManCacheFragment
import com.adonai.manman.R
import com.adonai.manman.database.DbProvider.helper
import com.adonai.manman.entities.ManPage

/**
 * Array adapter for showing cached commands in ListView
 * The data retrieval is done through [ManCacheFragment.CacheBrowseCallback]
 *
 * @see android.widget.ArrayAdapter
 * @see com.adonai.manman.entities.ManPage
 *
 * @author Kanedias
 */
class CachedCommandsArrayAdapter(context: Context, resource: Int, textViewResourceId: Int, objects: List<ManPage>) : ArrayAdapter<ManPage>(context, resource, textViewResourceId, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val current = getItem(position)
        val root = super.getView(position, convertView, parent)

        val command = root.findViewById<View>(R.id.command_name_label) as TextView
        val url = root.findViewById<View>(R.id.command_description_label) as TextView
        val moreActions = root.findViewById<View>(R.id.popup_menu) as ImageView

        command.text = current!!.name
        url.text = current.url
        moreActions.setOnClickListener { v ->
            val pm = PopupMenu(context, v)
            pm.inflate(R.menu.cached_item_popup)
            pm.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.share_link_popup_menu_item -> {
                        val sendIntent = Intent(Intent.ACTION_SEND)
                        sendIntent.type = "text/plain"
                        sendIntent.putExtra(Intent.EXTRA_TITLE, current.name)
                        sendIntent.putExtra(Intent.EXTRA_TEXT, current.url)
                        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_link)))
                        return@OnMenuItemClickListener true
                    }
                    R.id.copy_link_popup_menu_item -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        Toast.makeText(context.applicationContext, context.getString(R.string.copied) + " " + current.url, Toast.LENGTH_SHORT).show()
                        clipboard.setPrimaryClip(ClipData.newPlainText(current.name, current.url))
                        return@OnMenuItemClickListener true
                    }
                    R.id.delete_popup_menu_item -> {
                        helper.manPagesDao.delete(current)
                        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(MainPagerActivity.DB_CHANGE_NOTIFY))
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