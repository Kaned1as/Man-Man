package com.adonai.manman;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import com.adonai.manman.database.DbProvider;
import com.adonai.manman.entities.ManPage;
import com.j256.ormlite.stmt.PreparedQuery;

import java.sql.SQLException;
import java.util.List;

/**
 * Fragment to show cached man pages list
 * These pages can be viewed without touching the network
 */
public class ManPageCacheFragment extends Fragment implements AdapterView.OnItemClickListener {

    private final static String SEARCH_QUERY = "search.query";

    private CacheBrowseCallback mCacheBrowseCallback = new CacheBrowseCallback();
    private BroadcastReceiver mBroadcastHandler = new DbBroadcastReceiver();

    private SearchView mSearchCacheView;
    private ListView mCacheList;

    @NonNull
    public static ManPageCacheFragment newInstance() {
        ManPageCacheFragment fragment = new ManPageCacheFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public ManPageCacheFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle defaultLoadAll = new Bundle();
        defaultLoadAll.putString(SEARCH_QUERY, "");
        getLoaderManager().initLoader(MainPagerActivity.CACHE_RETRIEVER_LOADER, defaultLoadAll, mCacheBrowseCallback);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastHandler, new IntentFilter(MainPagerActivity.DB_CHANGE_NOTIFY));
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mBroadcastHandler);
    }

    @NonNull
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_cache_browse, container, false);

        mSearchCacheView = (SearchView) root.findViewById(R.id.cache_search_edit);
        mSearchCacheView.setOnQueryTextListener(new SearchInCacheListener());
        mCacheList = (ListView) root.findViewById(R.id.cached_pages_list);
        mCacheList.setOnItemClickListener(this);
//        mListView.setAdapter(mChaptersAdapter);
//        mListView.setOnItemClickListener(mChapterClickListener);
        return root;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ManPage manPage = (ManPage) parent.getItemAtPosition(position);
        ManPageDialogFragment.newInstance(manPage.getName(), manPage.getUrl()).show(getFragmentManager(), "manPage");
    }

    /**
     * Callback for loading matching cache pages from database
     * Since count of cached pages is far less than total chapter contents, we can retrieve it all at once
     */
    private class CacheBrowseCallback implements LoaderManager.LoaderCallbacks<List<ManPage>> {
        @Override
        public Loader<List<ManPage>> onCreateLoader(int i, final Bundle args) {
            return new AsyncTaskLoader<List<ManPage>>(getActivity()) {
                @Override
                protected void onStartLoading() {
                    if (args.containsKey(SEARCH_QUERY)) {
                        forceLoad();
                    }
                }

                @Nullable
                @Override
                public List<ManPage> loadInBackground() {
                    String queryString = args.getString(SEARCH_QUERY);
                    args.remove(SEARCH_QUERY); // load only once

                    // check the DB for cached pages
                    try {
                        PreparedQuery<ManPage> query = DbProvider.getHelper().getManPagesDao().queryBuilder().where().like("name", "%" + queryString + "%").prepare();
                        return DbProvider.getHelper().getManPagesDao().query(query);
                    } catch (SQLException e) {
                        Log.e("Man Man", "Database", e);
                        Utils.showToastFromAnyThread(getActivity(), R.string.database_retrieve_error);
                    }
                    return null;
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<List<ManPage>> objectLoader, List<ManPage> results) {
            if(results != null) {
                mCacheList.setAdapter(new CachedCommandsArrayAdapter(getActivity(), R.layout.chapter_command_list_item, R.id.command_name_label, results));
            }
        }

        @Override
        public void onLoaderReset(Loader<List<ManPage>> objectLoader) {

        }
    }

    /**
     * Array adapter for showing cached commands in ListView
     * The data retrieval is done through {@link com.adonai.manman.ManPageCacheFragment.CacheBrowseCallback}
     *
     * @see android.widget.ArrayAdapter
     * @see com.adonai.manman.entities.ManPage
     */
    private class CachedCommandsArrayAdapter extends ArrayAdapter<ManPage> {

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

    private class SearchInCacheListener implements SearchView.OnQueryTextListener {
        private String currentText;

        @Override
        public boolean onQueryTextSubmit(String query) {
            currentText = query;
            fireLoader();
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if(TextUtils.equals(currentText, newText))
                return false;

            currentText = newText;
            fireLoader();
            return true;
        }

        private void fireLoader() {
            final Bundle argsForLoader = new Bundle();
            argsForLoader.putString(SEARCH_QUERY, currentText);
            getLoaderManager().restartLoader(MainPagerActivity.SEARCH_COMMAND_LOADER, argsForLoader, mCacheBrowseCallback);
        }
    }

    /**
     * Handler to receive notifications for changes in database (to update cache list view)
     */
    private class DbBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle args = new Bundle();
            args.putString(SEARCH_QUERY, mSearchCacheView.getQuery().toString());
            getLoaderManager().restartLoader(MainPagerActivity.CACHE_RETRIEVER_LOADER, args, mCacheBrowseCallback);
        }
    }
}
