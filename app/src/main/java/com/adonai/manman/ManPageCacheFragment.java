package com.adonai.manman;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;

import com.adonai.manman.adapters.CachedCommandsArrayAdapter;
import com.adonai.manman.database.DbProvider;
import com.adonai.manman.entities.ManPage;
import com.adonai.manman.misc.AbstractNetworkAsyncLoader;
import com.j256.ormlite.stmt.PreparedQuery;

import java.sql.SQLException;
import java.util.List;

/**
 * Fragment to show cached man pages list
 * These pages can be viewed without touching the network
 *
 * @author Oleg Chernovskiy
 */
public class ManPageCacheFragment extends Fragment implements AdapterView.OnItemClickListener {

    private CacheBrowseCallback mCacheBrowseCallback = new CacheBrowseCallback();
    private BroadcastReceiver mBroadcastHandler = new DbBroadcastReceiver();

    private SearchView mSearchCache;
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

    @NonNull
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_cache_browse, container, false);

        mSearchCache = (SearchView) root.findViewById(R.id.cache_search_edit);
        mSearchCache.setOnQueryTextListener(new SearchInCacheListener());
        mCacheList = (ListView) root.findViewById(R.id.cached_pages_list);
        mCacheList.setOnItemClickListener(this);
        getLoaderManager().initLoader(MainPagerActivity.CACHE_RETRIEVER_LOADER, null, mCacheBrowseCallback);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastHandler, new IntentFilter(MainPagerActivity.DB_CHANGE_NOTIFY));
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mBroadcastHandler);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        mSearchCache.clearFocus(); // otherwise we have to click "back" twice

        ManPage manPage = (ManPage) parent.getItemAtPosition(position);
        ManPageDialogFragment mpdf = ManPageDialogFragment.newInstance(manPage.getName(), manPage.getUrl());
        getFragmentManager()
                .beginTransaction()
                .addToBackStack("PageFromCache")
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .replace(R.id.replacer, mpdf)
                .commit();
    }

    /**
     * Callback for loading matching cache pages from database
     * Since count of cached pages is far less than total chapter contents, we can retrieve it all at once
     */
    private class CacheBrowseCallback implements LoaderManager.LoaderCallbacks<List<ManPage>> {
        @Override
        public Loader<List<ManPage>> onCreateLoader(int i, Bundle args) {
            return new AbstractNetworkAsyncLoader<List<ManPage>>(getActivity()) {

                @Nullable
                @Override
                public List<ManPage> loadInBackground() {
                    // check the DB for cached pages
                    try {
                        PreparedQuery<ManPage> query = DbProvider.getHelper().getManPagesDao().queryBuilder().where().like("name", "%" + mSearchCache.getQuery().toString() + "%").prepare();
                        return DbProvider.getHelper().getManPagesDao().query(query);
                    } catch (SQLException e) {
                        Log.e(Utils.MM_TAG, "Exception while querying DB for cached page", e);
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
            getLoaderManager().getLoader(MainPagerActivity.CACHE_RETRIEVER_LOADER).onContentChanged();
        }
    }

    /**
     * Handler to receive notifications for changes in database (to update cache list view)
     */
    private class DbBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            getLoaderManager().getLoader(MainPagerActivity.CACHE_RETRIEVER_LOADER).onContentChanged();
        }
    }
}
