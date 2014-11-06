package com.adonai.manman;


import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.adonai.manman.adapters.ChapterContentsArrayAdapter;
import com.adonai.manman.adapters.ChapterContentsCursorAdapter;
import com.adonai.manman.adapters.ChaptersArrayAdapter;
import com.adonai.manman.database.DbProvider;
import com.adonai.manman.entities.ManSectionIndex;
import com.adonai.manman.entities.ManSectionItem;
import com.adonai.manman.misc.ManSectionExtractor;
import com.adonai.manman.views.ProgressBarWrapper;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.PreparedQuery;

import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.InputSource;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
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
public class ManChaptersFragment extends Fragment {
    public final static String CHAPTER_INDEX = "chapter.index";

    public final static String CHAPTER_COMMANDS_PREFIX = "https://www.mankier.com";

    private RetrieveContentsCallback mContentRetrieveCallback = new RetrieveContentsCallback();
    private ChaptersArrayAdapter mChaptersAdapter;

    private Map<String, String> mCachedChapters;

    private ListView mListView;
    private ProgressBarWrapper mProgress;
    /**
     * Click listener for selecting a chapter from the list.
     * The request is then sent to the loader to load chapter data asynchronously
     * <br/>
     * We don't have any headers at this point
     *
     * @see ManChaptersFragment.RetrieveContentsCallback
     */
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
    /**
     * Click listener for selecting a command from the list.
     * New instance of {@link com.adonai.manman.ManPageDialogFragment} then created and shown
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
                ManPageDialogFragment.newInstance(item.getName(), item.getUrl()).show(getFragmentManager(), "manPage");
            }
        }
    };

    @NonNull
    public static ManChaptersFragment newInstance() {
        ManChaptersFragment fragment = new ManChaptersFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public ManChaptersFragment() {
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
     * Loader callback for async loading of clicked chapter's contents and showing them in ListView afterwards
     * <br/>
     * The data is retrieved from local database (if cached there) or from network (if not)
     *
     * @see com.adonai.manman.entities.ManSectionItem
     */
    private class RetrieveContentsCallback implements LoaderManager.LoaderCallbacks<ManPageContentsResult> {
        @Override
        public Loader<ManPageContentsResult> onCreateLoader(int id, @NonNull final Bundle args) {
            return new ChapterContentsAsyncTaskLoader(args);
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
                    mListView.setAdapter(new ChapterContentsCursorAdapter(getActivity(), data.choiceDbCache.first, data.choiceDbCache.second, data.chapter));
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
    public void onDestroyView() {
        super.onDestroyView();
        mProgress.hide(); // always hide progressbar to avoid window leakage
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
        public int read(@NonNull byte[] buffer, int byteOffset, int byteCount) throws IOException {
            int res = super.read(buffer, byteOffset, byteCount);
            if(shouldWarn) {
                shouldWarn = false;
                if(length > (25 << 10)) { // 25 kbytes
                    Utils.showToastFromAnyThread(getActivity(), R.string.long_load_warn);
                }
            }

            if(shouldCount) {
                transferred += res;
                if(getActivity() != null) {
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
        private final List<ManSectionItem> choiceList; // from network
        private final Pair<RuntimeExceptionDao<ManSectionItem, String>, PreparedQuery<ManSectionItem>> choiceDbCache; // from DB
        private final String chapter;

        private ManPageContentsResult(@NonNull List<ManSectionItem> choiceList, @NonNull String chapter) {
            this.choiceList = choiceList;
            this.choiceDbCache = null;
            this.chapter = chapter;
        }

        private ManPageContentsResult(@NonNull RuntimeExceptionDao<ManSectionItem, String> dao, @NonNull PreparedQuery<ManSectionItem> query, @NonNull String chapter) {
            this.choiceDbCache = Pair.create(dao, query);
            this.choiceList = null;
            this.chapter = chapter;
        }

    }

    private class ChapterContentsAsyncTaskLoader extends AsyncTaskLoader<ManPageContentsResult> {
        private final Bundle args;

        public ChapterContentsAsyncTaskLoader(Bundle args) {
            super(getActivity());
            this.args = args;
        }

        @Override
        protected void onStartLoading() {
            if(args.containsKey(CHAPTER_INDEX)) {
                // show progressbar under actionbar
                mProgress.setIndeterminate(false);
                mProgress.setProgress(0);
                mProgress.show();

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
                PreparedQuery<ManSectionItem> query = DbProvider.getHelper().getManChaptersDao().queryBuilder().orderBy("name", true).where().eq("parentChapter", index).prepare();
                if(DbProvider.getHelper().getManChaptersDao().queryForFirst(query) != null) // we have it in cache
                return new ManPageContentsResult(DbProvider.getHelper().getManChaptersDao(), query, index);
            } catch (SQLException e) {
                Log.e("Man Man", "Database", e);
                Utils.showToastFromAnyThread(getActivity(), R.string.database_retrieve_error);
            }

            // If we're here, nothing is in DB for now
            List<ManSectionItem> results = loadFromNetwork(index, CHAPTER_COMMANDS_PREFIX + "/" + index);
            if(results != null) {
                Collections.sort(results);
                saveToDb(results);
                return new ManPageContentsResult(results, index);
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
                Log.e("Man Man", "Network", e);
                // can't show a toast from a thread without looper
                Utils.showToastFromAnyThread(getActivity(), R.string.connection_error);
            }
            return  null;
        }

        private void saveToDb(final List<ManSectionItem> items) {
            // save to DB for caching
            try {
                TransactionManager.callInTransaction(DbProvider.getHelper().getConnectionSource(), new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        for (ManSectionItem msi : items) {
                            DbProvider.getHelper().getManChaptersDao().create(msi);
                        }
                        List<ManSectionIndex> indexes = Utils.createIndexer(items);
                        for (ManSectionIndex index : indexes) {
                            DbProvider.getHelper().getManChapterIndexesDao().create(index);
                        }
                        return null;
                    }
                });
            } catch (SQLException e) {
                Log.e("Man Man", "Database", e);
                // can't show a toast from a thread without looper
                Utils.showToastFromAnyThread(getActivity(), R.string.database_save_error);
            }
        }
    }
}
