package com.adonai.manman.adapters;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.adonai.manman.R;
import com.adonai.manman.database.DbProvider;
import com.adonai.manman.entities.ManSectionIndex;
import com.adonai.manman.entities.ManSectionItem;
import com.adonai.manman.misc.ManChapterItemOnClickListener;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.PreparedQuery;

import java.util.List;

/**
 * Cursor adapter for showing large lists of commands from DB
 * For example, General commands chapter has about 14900 ones
 * so we should load only a window of those
 * <br/>
 * The data retrieval is done through {@link com.adonai.manman.ManChaptersFragment.RetrieveContentsCallback}
 *
 * @see OrmLiteCursorAdapter
 * @author Adonai
 */
public class ChapterContentsCursorAdapter extends OrmLiteCursorAdapter<ManSectionItem> implements SectionIndexer {
    private final List<ManSectionIndex> indexes;

    public ChapterContentsCursorAdapter(Activity context, RuntimeExceptionDao<ManSectionItem, String> dao, PreparedQuery<ManSectionItem> query, String chapter) {
        super(context, dao, query);
        indexes = DbProvider.getHelper().getManChapterIndexesDao().queryForEq("parentChapter", chapter);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ManSectionItem current = getItem(position);
        final View view;
        final LayoutInflater inflater = LayoutInflater.from(mContext);

        if (convertView == null)
            view = inflater.inflate(R.layout.chapter_command_list_item, parent, false);
        else
            view = convertView;

        TextView command = (TextView) view.findViewById(R.id.command_name_label);
        command.setText(current.getName());

        TextView desc = (TextView) view.findViewById(R.id.command_description_label);
        desc.setText(current.getDescription());

        final ImageView moreActions = (ImageView) view.findViewById(R.id.popup_menu);
        moreActions.setOnClickListener(new ManChapterItemOnClickListener(mContext, current));

        return view;
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
