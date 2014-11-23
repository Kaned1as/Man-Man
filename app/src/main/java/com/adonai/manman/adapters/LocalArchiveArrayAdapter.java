package com.adonai.manman.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.adonai.manman.R;

import java.io.File;
import java.util.List;

/**
 * Array adapter for showing files in local man page archive
 * The data retrieval is done through {@link com.adonai.manman.ManLocalArchiveFragment.LocalArchiveParserCallback}
 *
 * @see android.widget.ArrayAdapter
 * @see java.io.File
 * @author Adonai
 */
public class LocalArchiveArrayAdapter extends ArrayAdapter<File> {

    public LocalArchiveArrayAdapter(Context context, int resource, int textViewResourceId, List<File> objects) {
        super(context, resource, textViewResourceId, objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final File current = getItem(position);
        String effectiveName = current.getName().replaceAll("\\.gz$", "").replaceAll("\\.\\d$", "");
        View root = super.getView(position, convertView, parent);

        TextView command = (TextView) root.findViewById(R.id.command_name_label);
        command.setText(effectiveName);

        TextView url = (TextView) root.findViewById(R.id.command_description_label);
        url.setText(current.getParent());

        return root;
    }
}
