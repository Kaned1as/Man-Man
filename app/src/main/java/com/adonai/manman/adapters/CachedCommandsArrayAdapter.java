package com.adonai.manman.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.adonai.manman.R;
import com.adonai.manman.entities.ManPage;

import java.util.List;

/**
 * Array adapter for showing cached commands in ListView
 * The data retrieval is done through {@link com.adonai.manman.ManPageCacheFragment.CacheBrowseCallback}
 *
 * @see android.widget.ArrayAdapter
 * @see com.adonai.manman.entities.ManPage
 */
public class CachedCommandsArrayAdapter extends ArrayAdapter<ManPage> {

    public CachedCommandsArrayAdapter(Context context, int resource, int textViewResourceId, List<ManPage> objects) {
        super(context, resource, textViewResourceId, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ManPage current = getItem(position);
        View root = super.getView(position, convertView, parent);

        TextView command = (TextView) root.findViewById(R.id.command_name_label);
        command.setText(current.getName());

        TextView url = (TextView) root.findViewById(R.id.command_description_label);
        url.setText(current.getUrl());

        return root;
    }
}
