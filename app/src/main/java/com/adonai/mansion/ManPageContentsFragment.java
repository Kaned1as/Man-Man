package com.adonai.mansion;


import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.adonai.mansion.adapters.OrmLiteCursorAdapter;
import com.adonai.mansion.database.DbProvider;
import com.adonai.mansion.entities.ManSectionItem;
import com.adonai.mansion.views.ProgressBarWrapper;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.PreparedQuery;

import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;


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
    private ProgressBarWrapper mProgress;
    /**
     * Click listener for selecting a chapter from the list.
     * The request is then sent to the loader to load chapter data asynchronously
     * <br/>
     * We don't have any headers at this point
     *
     * @see com.adonai.mansion.ManPageContentsFragment.RetrieveContentsCallback
     */
    private AdapterView.OnItemClickListener mChapterClickListener = new AdapterView.OnItemClickListener() {

        @Override
        @SuppressWarnings("unchecked")
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Map.Entry<String, String> item = (Map.Entry<String, String>) parent.getItemAtPosition(position);
            Bundle args = new Bundle();
            args.putString(CHAPTER_INDEX, item.getKey());
            getLoaderManager().restartLoader(MainPagerActivity.CONTENTS_RETRIEVER_LOADER, args, mContentRetrieveCallback);

            // show progressbar under actionbar
            mProgress.setIndeterminate(false);
            mProgress.setProgress(0);
            mProgress.show();
        }
    };
    /**
     * Click listener for selecting a command from the list.
     * New instance of {@link com.adonai.mansion.ManPageDialogFragment} then created and shown
     * for loading ful command man page
     * <br/>
     * We have a header "To contents" so handle this case
     *
     */
    private AdapterView.OnItemClickListener mCommandClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            ManSectionItem item = (ManSectionItem) parent.getItemAtPosition(position);
            if(item == null) { // header
                mListView.removeHeaderView(view);
                mListView.setAdapter(mChaptersAdapter);
                mListView.setOnItemClickListener(mChapterClickListener);
            } else {
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
        mProgress = new ProgressBarWrapper(getActivity());
        return root;
    }

    /**
     * This class represents an array adapter for showing man chapters
     * There are only about ten constant chapters, so it was convenient to place it to the string-array
     * <br/>
     * The array is retrieved via {@link Utils#parseStringArray(android.content.Context, int)}
     * and stored in {@link #mCachedChapters}
     */
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

    /**
     * Cursor adapter for showing large lists of commands from DB
     * For example, General commands chapter has about 14900 ones
     * so we should load only a window of those
     * <br/>
     * The data retrieval is done through {@link com.adonai.mansion.ManPageContentsFragment.RetrieveContentsCallback}
     *
     * @see com.adonai.mansion.adapters.OrmLiteCursorAdapter
     */
    private class ChapterContentsCursorAdapter extends OrmLiteCursorAdapter<ManSectionItem> {

        public ChapterContentsCursorAdapter(RuntimeExceptionDao<ManSectionItem, String> dao, PreparedQuery<ManSectionItem> query) {
            super(getActivity(), dao, query);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ManSectionItem current = getItem(position);
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

            return view;
        }
    }

    /**
     * Array adapter for showing commands with their description in ListView
     * It's convenient whet all the data is retrieved via network,
     * so we have complete command list at hand
     * <br/>
     * The data retrieval is done through {@link com.adonai.mansion.ManPageContentsFragment.RetrieveContentsCallback}
     *
     * @see android.widget.ArrayAdapter
     * @see com.adonai.mansion.entities.ManSectionItem
     */
    private class ChapterContentsArrayAdapter extends ArrayAdapter<ManSectionItem> {

        public ChapterContentsArrayAdapter(Context context, int resource, int textViewResourceId, List<ManSectionItem> objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ManSectionItem current = getItem(position);
            View root = super.getView(position, convertView, parent);

            TextView command = (TextView) root.findViewById(R.id.command_name_label);
            command.setText(current.getName());

            TextView desc = (TextView) root.findViewById(R.id.command_description_label);
            desc.setText(current.getDescription());

            return root;
        }
    }


    /**
     * Loader callback for async loading of clicked chapter's contents and showing them in ListView afterwards
     * <br/>
     * The data is retrieved from local database (if cached there) or from network (if not)
     *
     * @see com.adonai.mansion.entities.ManSectionItem
     */
    private class RetrieveContentsCallback implements LoaderManager.LoaderCallbacks<ManPageContentsResult> {
        @Override
        public Loader<ManPageContentsResult> onCreateLoader(int id, @NonNull final Bundle args) {
            return new AsyncTaskLoader<ManPageContentsResult>(getActivity()) {
                @Override
                protected void onStartLoading() {
                    if(args.containsKey(CHAPTER_INDEX)) {
                        forceLoad();
                    }
                }

                /**
                 * Loads chapter page from DB or network asynchronously
                 *
                 * @return list of commands with their descriptions and urls
                 * or null on error/no input provided
                 */
                @Nullable
                @Override
                public ManPageContentsResult loadInBackground() {
                    // retrieve chapter content
                    String index = args.getString(CHAPTER_INDEX);
                    args.remove(CHAPTER_INDEX); // load only once

                    // check the DB for cached pages first
                    try {
                        PreparedQuery<ManSectionItem> query = DbProvider.getHelper().getManPagesDao().queryBuilder().where().eq("parentChapter", index).prepare();
                        if(DbProvider.getHelper().getManPagesDao().queryForFirst(query) != null) // we have it in cache
                        return new ManPageContentsResult(DbProvider.getHelper().getManPagesDao(), query);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        Utils.showToastFromAnyThread(getActivity(), R.string.database_retrieve_error);
                    }

                    // If we're here, nothing is in DB for now
                    List<ManSectionItem> results = loadFromNetwork(index, CHAPTER_COMMANDS_PREFIX + "/" + index);
                    if(results != null) {
                        saveToDb(results);
                        return new ManPageContentsResult(results);
                    }

                    return null;
                }

                @Nullable
                private List<ManSectionItem> loadFromNetwork(final String index, String link) {
                    try {
                        // load chapter page with command links
                        URLConnection conn = new URL(link).openConnection();
                        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
                        conn.setReadTimeout(10000);
                        conn.setConnectTimeout(5000);
                        // count the bytes and show progress
                        InputStream is = new GZIPInputStream(new CountingInputStream(conn.getInputStream(), conn.getContentLength()), conn.getContentLength());
                        final Parser parser = new Parser();
                        final List<ManSectionItem> msItems = new ArrayList<>(500);
                        parser.setContentHandler(new ManSectionExtractor(index, msItems));
                        parser.setFeature(Parser.namespacesFeature, false);
                        parser.parse(new InputSource(is));
                        return msItems;
                    } catch (Exception e) {
                        e.printStackTrace();
                        // can't show a toast from a thread without looper
                        Utils.showToastFromAnyThread(getActivity(), R.string.connection_error);
                    }
                    return  null;
                }

                private void saveToDb(final List<ManSectionItem> msItems) {
                    // save to DB for caching
                    try {
                        TransactionManager.callInTransaction(DbProvider.getHelper().getConnectionSource(), new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                for (ManSectionItem msi : msItems) {
                                    DbProvider.getHelper().getManPagesDao().createOrUpdate(msi);
                                }
                                return null;
                            }
                        });
                    } catch (SQLException e) {
                        e.printStackTrace();
                        // can't show a toast from a thread without looper
                        Utils.showToastFromAnyThread(getActivity(), R.string.database_save_error);
                    }
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<ManPageContentsResult> loader, ManPageContentsResult data) {
            mProgress.hide();
            if(data != null) { // if no error happened
                View text = View.inflate(getActivity(), R.layout.back_header, null);
                if(mListView.getAdapter() instanceof ChapterContentsCursorAdapter) {
                    // close opened cursor prior to adapter change
                    ((ChapterContentsCursorAdapter) mListView.getAdapter()).closeCursor();
                }
                mListView.setAdapter(null);
                mListView.addHeaderView(text);
                if(data.choiceDbCache != null) {
                    mListView.setAdapter(new ChapterContentsCursorAdapter(data.choiceDbCache.first, data.choiceDbCache.second));
                } else {
                    mListView.setAdapter(new ChapterContentsArrayAdapter(getActivity(), R.layout.chapter_command_list_item, R.id.command_name_label, data.choiceList));
                }
                mListView.setOnItemClickListener(mCommandClickListener);
            }
        }

        @Override
        public void onLoaderReset(Loader<ManPageContentsResult> loader) {
        }

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mProgress.onOrientationChanged();
    }

    /**
     * Convenience class for counting progress in cases we have
     * exact length of what we want to receive
     *
     * @see java.io.FilterInputStream
     */
    private class CountingInputStream extends FilterInputStream {

        private final int length;
        private int transferred;
        private boolean shouldCount = true;
        private boolean shouldWarn = true;

        CountingInputStream(InputStream in, int totalBytes) throws IOException {
            super(in);
            this.length = totalBytes;
        }

        @Override
        public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            int res = super.read(buffer, byteOffset, byteCount);
            if(shouldWarn) {
                shouldWarn = false;
                if(length > (25 << 10)) { // 25 kbytes
                    Utils.showToastFromAnyThread(getActivity(), R.string.long_load_warn);
                }
            }

            if(shouldCount) {
                transferred += res;
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int progress = transferred * 100 / length;
                        mProgress.setProgress(progress);
                        if (progress == 100) {
                            mProgress.setIndeterminate(true);
                            shouldCount = false; // don't count further, it's pointless
                        }
                    }
                });
            }
            return res;
        }
    }

    /**
     * Convenience class for selecting exclusively one of the result types
     * <br/>
     * The first is for network load and the second is the DB retrieval
     *
     */
    private static class ManPageContentsResult {

        private ManPageContentsResult(@NonNull List<ManSectionItem> choiceList) {
            this.choiceList = choiceList;
            choiceDbCache = null;
        }

        private ManPageContentsResult(@NonNull RuntimeExceptionDao<ManSectionItem, String> dao, @NonNull PreparedQuery<ManSectionItem> query) {
            this.choiceDbCache = Pair.create(dao, query);
            choiceList = null;
        }

        private final List<ManSectionItem> choiceList; // from network
        private final Pair<RuntimeExceptionDao<ManSectionItem, String>, PreparedQuery<ManSectionItem>> choiceDbCache; // from DB
    }

    private class ManSectionExtractor extends DefaultHandler {
        private final String index;
        private final List<ManSectionItem> msItems;

        private StringBuilder holder;
        private boolean flagCommand;
        private boolean flagUrl;
        private boolean flagDescription;

        public ManSectionExtractor(String index, List<ManSectionItem> msItems) {
            this.index = index;
            this.msItems = msItems;
            holder = new StringBuilder(50);
            flagCommand = false;
            flagUrl = false;
            flagDescription = false;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if("div".equals(qName) && "e".equals(attributes.getValue("class"))) {
                ManSectionItem msi = new ManSectionItem();
                msi.setParentChapter(index);
                msItems.add(msi);
                flagCommand = true;
            } else if(flagCommand && "a".equals(qName)) {
                msItems.get(msItems.size() - 1).setUrl(CHAPTER_COMMANDS_PREFIX + attributes.getValue("href"));
                flagUrl = true;
            } else if(flagCommand && "span".equals(qName)) {
                flagDescription = true;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if(flagUrl || flagDescription) {
                holder.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if("div".equals(qName) && flagCommand) {
                flagCommand = false;
            } else if("a".equals(qName) && flagUrl) {
                msItems.get(msItems.size() - 1).setName(holder.toString());
                holder.setLength(0);
                flagUrl = false;
            } else if("span".equals(qName) && flagDescription) {
                msItems.get(msItems.size() - 1).setDescription(holder.toString());
                holder.setLength(0);
                flagDescription = false;
            }
        }
    }
}
