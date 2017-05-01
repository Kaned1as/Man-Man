package com.adonai.manman;


import android.content.*;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.adonai.manman.adapters.ChapterContentsArrayAdapter;
import com.adonai.manman.adapters.ChapterContentsCursorAdapter;
import com.adonai.manman.adapters.ChaptersArrayAdapter;
import com.adonai.manman.database.DbProvider;
import com.adonai.manman.entities.ManSectionIndex;
import com.adonai.manman.entities.ManSectionItem;
import com.adonai.manman.misc.AbstractNetworkAsyncLoader;
import com.adonai.manman.views.ProgressBarWrapper;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.misc.TransactionManager;
import com.j256.ormlite.stmt.PreparedQuery;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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

import static com.adonai.manman.Utils.MM_TAG;


/**
 * Fragment to show table of contents and navigate into it
 * Note: works slower that just search!
 *
 * @author Oleg Chernovskiy
 */
public class ManChaptersFragment extends Fragment {
    public final static String CHAPTER_INDEX = "chapter.index";
    public final static String CHAPTER_PACKAGE = "chapter.package";

    public final static String CHAPTER_COMMANDS_PREFIX = "https://www.mankier.com";

    private RetrieveChapterContentsCallback mContentRetrieveCallback = new RetrieveChapterContentsCallback();
    private RetrievePackageContentsCallback mPackageRetrieveCallback = new RetrievePackageContentsCallback();
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
     * @see RetrieveChapterContentsCallback
     */
    private AdapterView.OnItemClickListener mChapterClickListener = new AdapterView.OnItemClickListener() {

        @Override
        @SuppressWarnings("unchecked")
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Map.Entry<String, String> item = (Map.Entry<String, String>) parent.getItemAtPosition(position);
            Bundle args = new Bundle();
            args.putString(CHAPTER_INDEX, item.getKey());
            // show progressbar under actionbar
            mProgress.show();
            getLoaderManager().restartLoader(MainPagerActivity.CHAPTER_RETRIEVER_LOADER, args, mContentRetrieveCallback);
        }
    };

    /**
     * Click listener for selecting a package from the list.
     * Usable only when list view shows list of packages.
     *
     * After picking a package a list of commands will show up that user can choose from.
     *
     * New instance of {@link com.adonai.manman.ManPageDialogFragment} is then created and shown
     * for loading full command man page.
     *
     */
    private AdapterView.OnItemClickListener mPackageClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            ManSectionItem item = (ManSectionItem) parent.getItemAtPosition(position);
            Bundle args = new Bundle();
            args.putString(CHAPTER_INDEX, item.getParentChapter());
            args.putString(CHAPTER_PACKAGE, item.getUrl());
            // show progressbar under actionbar
            mProgress.show();
            getLoaderManager().restartLoader(MainPagerActivity.PACKAGE_RETRIEVER_LOADER, args, mPackageRetrieveCallback);
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
        getLoaderManager().initLoader(MainPagerActivity.CHAPTER_RETRIEVER_LOADER, Bundle.EMPTY, mContentRetrieveCallback);
        getLoaderManager().initLoader(MainPagerActivity.PACKAGE_RETRIEVER_LOADER, Bundle.EMPTY, mPackageRetrieveCallback);
        return root;
    }


    /**
     * Loader callback for async loading of clicked chapter's contents and showing them in ListView afterwards
     * <br/>
     * The data is retrieved from local database (if cached there) or from network (if not)
     *
     * @see ManSectionItem
     */
    private class RetrieveChapterContentsCallback implements LoaderManager.LoaderCallbacks<ManPageContentsResult> {
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
                 * @return list of packages with their descriptions and urls
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
                        Log.e(MM_TAG, "Exception while querying for cached pages", e);
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

                            final List<ManSectionItem> msItems = new ArrayList<>(500);
                            Document doc = Jsoup.parse(is, "UTF-8", link);
                            Elements rows = doc.select("div.section-index-content > table tr");
                            for (Element row : rows) {
                                msItems.add(sectionItemFromRow(index, row));
                            }
                            return msItems;
                        }
                    } catch (Exception e) {
                        Log.e(MM_TAG, "Exception while loading man pages from network", e);
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
                        Log.e(MM_TAG, "Exception while saving cached page to DB", e);
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
                mListView.setOnItemClickListener(mPackageClickListener);
                LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastHandler, new IntentFilter(MainPagerActivity.BACK_BUTTON_NOTIFY));
            }

            // don't start this loader again on resume, it's one-shot
            getLoaderManager().restartLoader(MainPagerActivity.CHAPTER_RETRIEVER_LOADER, Bundle.EMPTY, mPackageRetrieveCallback);
        }

        @Override
        public void onLoaderReset(Loader<ManPageContentsResult> loader) {
        }

    }

    /**
     * Loader callback for async loading of clicked package's contents and showing them in a dialog afterwards
     * <br/>
     * The data is retrieved from local database (if cached there) or from network (if not)
     *
     * @see ManSectionItem
     */
    private class RetrievePackageContentsCallback implements LoaderManager.LoaderCallbacks<List<ManSectionItem>> {
        @Override
        public Loader<List<ManSectionItem>> onCreateLoader(int id, @NonNull final Bundle args) {
            return new AbstractNetworkAsyncLoader<List<ManSectionItem>>(getActivity()) {

                @Override
                protected void onStartLoading() {
                    if(args.containsKey(CHAPTER_INDEX)) {
                        super.onStartLoading();
                    }
                }

                /**
                 * Loads package page from network asynchronously
                 *
                 * @return list of commands with their descriptions and urls
                 * or null on error/no input provided
                 */
                @Nullable
                @Override
                public List<ManSectionItem> loadInBackground() {
                    // retrieve package content
                    String index = args.getString(CHAPTER_INDEX);
                    String url = args.getString(CHAPTER_PACKAGE);
                    if(!isStarted()) // task was cancelled
                        return Collections.emptyList();

                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder().url(url).build();
                    try {
                        Response response = client.newCall(request).execute();
                        if (response.isSuccessful()) {
                            String result = response.body().string();
                            Document root = Jsoup.parse(result, CHAPTER_COMMANDS_PREFIX);
                            Elements rows = root.select(String.format("caption:has(a[href=/%s/]) ~ tbody > tr", index));
                            List<ManSectionItem> manPages = new ArrayList<>(rows.size());
                            for (Element row : rows) {
                                manPages.add(sectionItemFromRow(index, row));
                            }
                            return manPages;
                        }
                    } catch (IOException e) {
                        Log.e(MM_TAG, "Exception while parsing package page " + url, e);
                        return Collections.emptyList();
                    }

                    return Collections.emptyList();
                }

                @Override
                public void deliverResult(List<ManSectionItem> data) {
                    mProgress.hide();
                    super.deliverResult(data);
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<List<ManSectionItem>> loader, List<ManSectionItem> data) {
            // finished loading - show selector dialog to the user
            final ArrayAdapter<ManSectionItem> adapter = new ChapterContentsArrayAdapter(getContext(),
                    R.layout.package_command_list_item, R.id.command_name_label, data);
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.select_command)
                    .setAdapter(adapter, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ManSectionItem item = adapter.getItem(which);
                            ManPageDialogFragment mpdf = ManPageDialogFragment.newInstance(item.getName(), item.getUrl());
                            getFragmentManager()
                                    .beginTransaction()
                                    .addToBackStack("PageFromChapterPackage")
                                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                                    .replace(R.id.replacer, mpdf)
                                    .commit();
                        }
                    }).create().show();

            // don't start this loader again on resume, it's one-shot
            getLoaderManager().restartLoader(MainPagerActivity.PACKAGE_RETRIEVER_LOADER, Bundle.EMPTY, mPackageRetrieveCallback);
        }

        @Override
        public void onLoaderReset(Loader<List<ManSectionItem>> loader) {
        }

    }

    @NonNull
    private ManSectionItem sectionItemFromRow(String chapterIndex, Element row) {
        Elements cells = row.select("td");
        Element anchor = cells.first().child(0);
        ManSectionItem msi = new ManSectionItem();
        msi.setParentChapter(chapterIndex);
        msi.setName(anchor.text());
        msi.setUrl(CHAPTER_COMMANDS_PREFIX + anchor.attr("href"));
        msi.setDescription(cells.last().text());
        return msi;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mProgress.hide(); // always hide progressbar to avoid window leakage
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mProgress.onOrientationChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        // if we're pausing this fragment and have active listener, we should no longer receive back button feedback
        if(!getUserVisibleHint() && mListView.getOnItemClickListener() == mPackageClickListener) {
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mBroadcastHandler);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // if we're resuming this fragment while in command list, we re-register to receive back button feedback
        if(getUserVisibleHint() && mListView.getOnItemClickListener() == mPackageClickListener) {
            LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastHandler, new IntentFilter(MainPagerActivity.BACK_BUTTON_NOTIFY));
        }
    }

    @Override
    public void onDestroy() { // if application is forcibly closed
        super.onDestroy();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mBroadcastHandler);
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
                            if (length <= 0) { // if no length provided
                                stopCounting();
                                return;
                            }

                            int progress = transferred * 100 / length;
                            if (progress == 100) { // download is complete
                                stopCounting();
                                return;
                            }

                            mProgress.setIndeterminate(false);
                            mProgress.setProgress(progress);
                        }
                    });
                }
            }
            return res;
        }

        // don't count further, show only animation
        private void stopCounting() {
            mProgress.setIndeterminate(true);
            shouldCount = false;
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
