package com.adonai.manman;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.adonai.manman.adapters.ChapterContentsArrayAdapter;
import com.adonai.manman.adapters.ChapterContentsCursorAdapter;
import com.adonai.manman.adapters.ChaptersArrayAdapter;
import com.adonai.manman.database.DbProvider;
import com.adonai.manman.entities.ManSectionIndex;
import com.adonai.manman.entities.ManSectionItem;
import com.adonai.manman.misc.AbstractNetworkAsyncLoader;
import com.adonai.manman.misc.ManSectionExtractor;
import com.adonai.manman.views.ProgressBarWrapper;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.PreparedQuery;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.ccil.cowan.tagsoup.Parser;
import org.xml.sax.InputSource;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
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
@SuppressWarnings("FieldCanBeLocal")
public class ManChaptersFragment extends Fragment {
    public final static String CHAPTER_INDEX = "chapter.index";

    public final static String CHAPTER_COMMANDS_PREFIX = "https://www.mankier.com";

    private RetrieveContentsCallback mContentRetrieveCallback = new RetrieveContentsCallback();
    private BroadcastReceiver mBroadcastHandler = new BackButtonBroadcastReceiver();
    private ChaptersArrayAdapter mChaptersAdapter;

    private Map<String, String> mCachedChapters;

    private FrameLayout mFrame;
    private ListView mListView;
    private ProgressBarWrapper mProgress;
    /**
     * Click listener for selecting a chapter from the list.
     * Usable only when list view shows list of chapters
     * The request is then sent to the loader to load chapter data asynchronously
     * <br/>
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
            // show progressbar under actionbar
            mProgress.setIndeterminate(false);
            mProgress.setProgress(0);
            mProgress.show();
            getLoaderManager().restartLoader(MainPagerActivity.CONTENTS_RETRIEVER_LOADER, args, mContentRetrieveCallback);
        }
    };
    /**
     * Click listener for selecting a command from the list.
     * Usable only when list view shows list of commands
     * New instance of {@link com.adonai.manman.ManPageDialogFragment} then created and shown
     * for loading ful command man page
     * <br/>
     *
     */
    private AdapterView.OnItemClickListener mCommandClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            ManSectionItem item = (ManSectionItem) parent.getItemAtPosition(position);
            ManPageDialogFragment mpdf = ManPageDialogFragment.newInstance(item.getName(), item.getUrl());
            getFragmentManager()
                    .beginTransaction()
                    .addToBackStack("PageFromSearch")
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.replacer, mpdf)
                    .commit();
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

    @NonNull
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mCachedChapters = Utils.parseStringArray(getActivity(), R.array.man_page_chapters);
        mChaptersAdapter = new ChaptersArrayAdapter(getActivity(), R.layout.chapters_list_item, R.id.chapter_index_label, new ArrayList<>(mCachedChapters.entrySet()));
        View root = inflater.inflate(R.layout.fragment_man_contents, container, false);

        mListView = (ListView) root.findViewById(R.id.chapter_commands_list);
        mListView.setAdapter(mChaptersAdapter);
        mListView.setOnItemClickListener(mChapterClickListener);

        mFrame = (FrameLayout) root.findViewById(R.id.chapter_fragment_frame);

        mProgress = new ProgressBarWrapper(getActivity());
        getLoaderManager().initLoader(MainPagerActivity.CONTENTS_RETRIEVER_LOADER, Bundle.EMPTY, mContentRetrieveCallback);
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
            return new AbstractNetworkAsyncLoader<ManPageContentsResult>(getActivity()) {

                @Override
                protected void onStartLoading() {
                    if(args.containsKey(CHAPTER_INDEX)) {
                        super.onStartLoading();
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
                    if(!isStarted()) // task was cancelled
                        return null;

                    // check the DB for cached pages first
                    try {
                        PreparedQuery<ManSectionItem> query = DbProvider.getHelper().getManChaptersDao().queryBuilder().orderBy("name", true).where().eq("parentChapter", index).prepare();
                        if(DbProvider.getHelper().getManChaptersDao().queryForFirst(query) != null) // we have it in cache
                            return new ManPageContentsResult(DbProvider.getHelper().getManChaptersDao(), query, index);
                    } catch (SQLException e) {
                        Log.e("Man Man", "Database", e);
                        Utils.showToastFromAnyThread(getActivity(), R.string.database_retrieve_error);
                    }

                    if(!isStarted()) // task was cancelled
                        return null;

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
                        OkHttpClient client = new OkHttpClient();
                        Request request = new Request.Builder()
                                .header("Accept-Encoding", "gzip, deflate")
                                .url(link)
                                .build();
                        Response response = client.newCall(request).execute();
                        if (response.isSuccessful()) {
                            // count the bytes and show progress
                            InputStream is;
                            if(response.header("Content-Length") != null) {
                                is = new GZIPInputStream(
                                        new CountingInputStream(response.body().byteStream(),
                                                (int) response.body().contentLength()),
                                                (int) response.body().contentLength());
                            } else {
                                is = new GZIPInputStream(new CountingInputStream(response.body().byteStream(),
                                        (int) response.body().contentLength()));
                            }

                            final Parser parser = new Parser();
                            final List<ManSectionItem> msItems = new ArrayList<>(500);
                            parser.setContentHandler(new ManSectionExtractor(index, msItems));
                            parser.setFeature(Parser.namespacesFeature, false);
                            parser.parse(new InputSource(is));
                            return msItems;
                        }
                    } catch (Exception e) {
                        Log.e("Man Man", "Network", e);
                        // can't show a toast from a thread without looper
                        Utils.showToastFromAnyThread(getActivity(), R.string.connection_error);
                    }
                    return  null;
                }

                private void saveToDb(final List<ManSectionItem> items) {
                    if(!isStarted()) // task was cancelled
                        return;

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

                @Override
                public void deliverResult(ManPageContentsResult data) {
                    mProgress.hide();
                    super.deliverResult(data);
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<ManPageContentsResult> loader, ManPageContentsResult data) {
            if(data != null) { // if no error happened
                if(mListView.getAdapter() instanceof ChapterContentsCursorAdapter) {
                    // close opened cursor prior to adapter change
                    ((ChapterContentsCursorAdapter) mListView.getAdapter()).closeCursor();
                }
                mListView.setFastScrollEnabled(false);
                mListView.setAdapter(null);
                swapListView();
                if(data.choiceDbCache != null) {
                    mListView.setAdapter(new ChapterContentsCursorAdapter(getActivity(), data.choiceDbCache.first, data.choiceDbCache.second, data.chapter));
                } else {
                    mListView.setAdapter(new ChapterContentsArrayAdapter(getActivity(), R.layout.chapter_command_list_item, R.id.command_name_label, data.choiceList));
                }
                mListView.setFastScrollEnabled(true);
                mListView.setOnItemClickListener(mCommandClickListener);
                LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastHandler, new IntentFilter(MainPagerActivity.BACK_BUTTON_NOTIFY));
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

    @Override
    public void onPause() {
        super.onPause();
        // if we're pausing this fragment and have active listener, we should no longer receive back button feedback
        if(!getUserVisibleHint() && mListView.getOnItemClickListener() == mCommandClickListener) {
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mBroadcastHandler);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // if we're resuming this fragment while in command list, we re-register to receive back button feedback
        if(getUserVisibleHint() && mListView.getOnItemClickListener() == mCommandClickListener) {
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastHandler, new IntentFilter(MainPagerActivity.BACK_BUTTON_NOTIFY));
        }
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
                if(length <= 0 || length > (25 << 10)) { // if no length provided or it's more than 25 kbytes
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
                            if (length <= 0 || progress == 100) { // if no length provided or download is complete
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

    /**
     * Handler to receive notifications for back button press (to return list view to chapter show)
     */
    private class BackButtonBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mListView.setAdapter(mChaptersAdapter);
            mListView.setOnItemClickListener(mChapterClickListener);
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(this);
        }
    }

    /**
     * Workaround for <a href="http://stackoverflow.com/questions/20730301/android-refresh-listview-sections-overlay-not-working-in-4-4">this</a>
     * <br/>
     * Swaps the list view prior to setting adapter to invalidate fast scroller
     */
    private void swapListView() {
        //save layout params
        ViewGroup.LayoutParams listViewParams;
        if (mListView != null) {
            listViewParams = mListView.getLayoutParams();
        } else {
            listViewParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        //frame is a FrameLayout around the ListView
        mFrame.removeView(mListView);

        mListView = new ListView(getActivity());
        mListView.setLayoutParams(listViewParams);
        //other ListView initialization code like divider settings
        mListView.setDivider(null);

        mFrame.addView(mListView);
    }
}
