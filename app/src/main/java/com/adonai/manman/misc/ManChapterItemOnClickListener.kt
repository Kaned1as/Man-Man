package com.adonai.manman.misc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import com.adonai.manman.R
import com.adonai.manman.entities.ManSectionItem

/**
 * Click listener to call popup menu on chapter item click
 *
 * @see com.adonai.manman.adapters.ChapterContentsArrayAdapter
 *
 * @author Kanedias
 */
class ManChapterItemOnClickListener(private val mContext: Context, private val current: ManSectionItem) : View.OnClickListener {

    override fun onClick(v: View) {
        val pm = PopupMenu(mContext, v)
        pm.inflate(R.menu.chapter_item_popup)
        pm.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.share_link_popup_menu_item -> {
                    val sendIntent = Intent(Intent.ACTION_SEND)
                    sendIntent.type = "text/plain"
                    sendIntent.putExtra(Intent.EXTRA_TITLE, current.name)
                    sendIntent.putExtra(Intent.EXTRA_TEXT, current.url)
                    mContext.startActivity(Intent.createChooser(sendIntent, mContext.getString(R.string.share_link)))
                    return@OnMenuItemClickListener true
                }
                R.id.copy_link_popup_menu_item -> {
                    val clipboard = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    Toast.makeText(mContext.applicationContext, mContext.getString(R.string.copied) + " " + current.url, Toast.LENGTH_SHORT).show()
                    clipboard.setPrimaryClip(ClipData.newPlainText(current.name, current.url))
                    return@OnMenuItemClickListener true
                }
            }
            false
        })
        pm.show()
    }
}