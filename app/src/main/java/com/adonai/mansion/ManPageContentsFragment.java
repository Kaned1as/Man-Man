package com.adonai.mansion;


import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.adonai.mansion.entities.ManSectionItem;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Fragment to show table of contents and navigate into it
 * Note: works slower that just search!
 *
 * @author Adonai
 */
public class ManPageContentsFragment extends Fragment {
    private final static String CHAPTER_INDEX = "chapter.index";

    private final static String CHAPTER_COMMANDS_PREFIX = "https://www.mankier.com/";

    private RetrieveContentsCallback mContentRetrieveCallback = new RetrieveContentsCallback();
    private ChaptersArrayAdapter mChaptersAdapter;

    private Map<String, String> mCachedChapters;
    private Map<String, List<ManSectionItem>> mCachedChapterContents = new HashMap<>();

    private ListView mListView;
    private AdapterView.OnItemClickListener mChapterClickListener = new AdapterView.OnItemClickListener() {

        @Override
        @SuppressWarnings("unchecked")
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Map.Entry<String, String> item = (Map.Entry<String, String>) parent.getItemAtPosition(position);
            Bundle args = new Bundle();
            args.putString(CHAPTER_INDEX, item.getKey());
            getLoaderManager().restartLoader(MainPagerActivity.CONTENTS_RETRIEVER_LOADER, args, mContentRetrieveCallback);
        }
    };
    private AdapterView.OnItemClickListener mCommandClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if(position == 0) { // header
                mListView.removeHeaderView(view);
                mListView.setAdapter(mChaptersAdapter);
                mListView.setOnItemClickListener(mChapterClickListener);
            } else {
                ManSectionItem item = (ManSectionItem) parent.getItemAtPosition(position - mListView.getHeaderViewsCount());
                ManPageDialogFragment.newInstance(item.getUrl()).show(getFragmentManager(), "manPage");
            }
        }
    };

    @NonNull
    public static ManPageContentsFragment newInstance() {
        ManPageContentsFragment fragment = new ManPageContentsFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public ManPageContentsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(MainPagerActivity.CONTENTS_RETRIEVER_LOADER, Bundle.EMPTY, mContentRetrieveCallback);
    }

    @NonNull
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mCachedChapters = Utils.parseStringArray(getActivity(), R.array.man_page_chapters);
        mChaptersAdapter = new ChaptersArrayAdapter(getActivity(), R.layout.chapters_list_item, R.id.chapter_index_label, new ArrayList<>(mCachedChapters.entrySet()));
        View root = inflater.inflate(R.layout.fragment_man_contents, container, false);

        mListView = (ListView) root.findViewById(R.id.chapter_commands_list);
        mListView.setAdapter(mChaptersAdapter);
        mListView.setOnItemClickListener(mChapterClickListener);
        return root;
    }

    private class ChaptersArrayAdapter extends ArrayAdapter<Map.Entry<String, String>> {

        public ChaptersArrayAdapter(Context context, int resource, int textViewResourceId, List<Map.Entry<String, String>> objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Map.Entry<String, String> current = getItem(position);
            View root = super.getView(position, convertView, parent);

            TextView index = (TextView) root.findViewById(R.id.chapter_index_label);
            index.setText(current.getKey());

            TextView name = (TextView) root.findViewById(R.id.chapter_name_label);
            name.setText(current.getValue());

            return root;
        }
    }


    private class RetrieveContentsCallback implements LoaderManager.LoaderCallbacks<List<ManSectionItem>> {
        @Override
        public Loader<List<ManSectionItem>> onCreateLoader(int id, final Bundle args) {
            return new AsyncTaskLoader<List<ManSectionItem>>(getActivity()) {
                @Override
                protected void onStartLoading() {
                    forceLoad();
                }

                @Override
                public List<ManSectionItem> loadInBackground() {
                    if(args.containsKey(CHAPTER_INDEX)) { // retrieve chapter content
                        String index = args.getString(CHAPTER_INDEX);
                        try {
                            Document root = Jsoup.connect(CHAPTER_COMMANDS_PREFIX + "/" + index).timeout(10000).get();
                            Elements commands = root.select("div.e");
                            if(!commands.isEmpty()) {
                                List<ManSectionItem> msItems = new ArrayList<>(commands.size());
                                for(Element command : commands) {
                                    ManSectionItem msi = new ManSectionItem();
                                    msi.setName(command.child(0).text());
                                    msi.setUrl(CHAPTER_COMMANDS_PREFIX + command.child(0).attr("href"));
                                    msi.setDescription(command.child(2).text());
                                    msItems.add(msi);
                                }
                                return msItems;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(getActivity(), R.string.connection_error, Toast.LENGTH_SHORT).show();
                        }
                    }
                    return null;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<List<ManSectionItem>> loader, List<ManSectionItem> data) {
            View text = View.inflate(getActivity(), R.layout.back_header, null);
            mListView.addHeaderView(text);
            //mListView.setAdapter();
            mListView.setOnItemClickListener(mCommandClickListener);
        }

        @Override
        public void onLoaderReset(Loader<List<ManSectionItem>> loader) {

        }
    }
}
