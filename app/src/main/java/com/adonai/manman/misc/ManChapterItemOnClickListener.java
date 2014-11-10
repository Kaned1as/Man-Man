package com.adonai.manman.misc;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.adonai.manman.R;
import com.adonai.manman.entities.ManSectionItem;

/**
* Click listener to call popup menu on chapter item click
 *
 * @author Adonai
*/
public class ManChapterItemOnClickListener implements View.OnClickListener {
    private final ManSectionItem current;
    private final Context mContext;

    public ManChapterItemOnClickListener(Context context, ManSectionItem current) {
        this.current = current;
        this.mContext = context;
    }

    @Override
    public void onClick(final View v) {
        PopupMenu pm = new PopupMenu(mContext, v);
        pm.inflate(R.menu.chapter_item_popup);
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.share_link_popup_menu_item:
                        Intent sendIntent = new Intent(Intent.ACTION_SEND);
                        sendIntent.setType("text/plain");
                        sendIntent.putExtra(Intent.EXTRA_TITLE, current.getName());
                        sendIntent.putExtra(Intent.EXTRA_TEXT, current.getUrl());
                        mContext.startActivity(Intent.createChooser(sendIntent, mContext.getString(R.string.share_link)));
                        return true;
                    case R.id.copy_link_popup_menu_item:
                        ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
                        Toast.makeText(mContext.getApplicationContext(), mContext.getString(R.string.copied) + " " + current.getUrl(), Toast.LENGTH_SHORT).show();
                        clipboard.setPrimaryClip(ClipData.newPlainText(current.getName(), current.getUrl()));
                        return true;
                }
                return false;
            }
        });
        pm.show();
    }
}
