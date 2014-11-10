package com.adonai.manman.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.adonai.manman.R;
import com.adonai.manman.Utils;
import com.adonai.manman.entities.ManSectionIndex;
import com.adonai.manman.entities.ManSectionItem;
import com.adonai.manman.misc.ManChapterItemOnClickListener;

import java.util.List;

/**
 * Array adapter for showing commands with their description in ListView
 * It's convenient whet all the data is retrieved via network,
 * so we have complete command list at hand
 * <br/>
 * The data retrieval is done through {@link com.adonai.manman.ManChaptersFragment.RetrieveContentsCallback}
 *
 * @see android.widget.ArrayAdapter
 * @see com.adonai.manman.entities.ManSectionItem
 * @author Adonai
 */
public class ChapterContentsArrayAdapter extends ArrayAdapter<ManSectionItem> implements SectionIndexer {
    private final List<ManSectionIndex> indexes;

    public ChapterContentsArrayAdapter(Context context, int resource, int textViewResourceId, List<ManSectionItem> objects) {
        super(context, resource, textViewResourceId, objects);
        indexes = Utils.createIndexer(objects);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ManSectionItem current = getItem(position);
        View root = super.getView(position, convertView, parent);

        TextView command = (TextView) root.findViewById(R.id.command_name_label);
        command.setText(current.getName());

        TextView desc = (TextView) root.findViewById(R.id.command_description_label);
        desc.setText(current.getDescription());

        final ImageView moreActions = (ImageView) root.findViewById(R.id.popup_menu);
        moreActions.setOnClickListener(new ManChapterItemOnClickListener(getContext(), current));

        return root;
    }

    @Override
    public Object[] getSections() {
        Character[] chars = new Character[indexes.size()];
        for(int i = 0; i < indexes.size(); ++i) {
            chars[i] = indexes.get(i).getLetter();
        }
        return chars;
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        return indexes.get(sectionIndex).getIndex();
    }

    @Override
    public int getSectionForPosition(int position) {
        for(int i = 0; i < indexes.size(); ++i) {
            if(indexes.get(i).getIndex() > position)
                return i - 1;
        }
        return 0;
    }
}
