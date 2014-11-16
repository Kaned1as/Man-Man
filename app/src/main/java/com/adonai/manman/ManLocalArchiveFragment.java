package com.adonai.manman;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SearchView;

import com.adonai.manman.adapters.LocalArchiveArrayAdapter;
import com.adonai.manman.misc.AbstractNetworkAsyncLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fragment for uploading and parsing local man page distributions
 *
 * @author Adonai
 */
public class ManLocalArchiveFragment extends Fragment {

    private ListView mLocalPageList;
    private SearchView mSearchLocalPage;
    private LocalArchiveParserCallback mLocalArchiveParseCallback = new LocalArchiveParserCallback();

    @NonNull
    public static ManLocalArchiveFragment newInstance() {
        ManLocalArchiveFragment fragment = new ManLocalArchiveFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    public ManLocalArchiveFragment() {
    }

    @NonNull
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_local_storage, container, false);
        mLocalPageList = (ListView) root.findViewById(R.id.local_storage_page_list);
        mSearchLocalPage = (SearchView) root.findViewById(R.id.local_search_edit);
        mSearchLocalPage.setOnQueryTextListener(null);

        getLoaderManager().initLoader(MainPagerActivity.LOCAL_PACKAGE_LOADER, null, mLocalArchiveParseCallback);
        setHasOptionsMenu(true);

        return root;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.local_archive_fragment_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.folder_settings:
                showFolderSettingsDialog();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFolderSettingsDialog() {
        new FolderChooseFragment().show(getFragmentManager(), "FolderListFragment");
        getLoaderManager().restartLoader(MainPagerActivity.LOCAL_PACKAGE_LOADER, null, mLocalArchiveParseCallback);
    }

    private class LocalArchiveParserCallback implements LoaderManager.LoaderCallbacks<List<File>> {
        @Override
        public Loader<List<File>> onCreateLoader(int i, Bundle bundle) {
            return new AbstractNetworkAsyncLoader<List<File>>(getActivity()) {
                Set<String> mFolderList;

                @Override
                protected void onStartLoading() {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    mFolderList = prefs.getStringSet(MainPagerActivity.FOLDER_LIST_KEY, new HashSet<String>());
                    super.onStartLoading();
                }

                @NonNull
                @Override
                public List<File> loadInBackground() {
                    List<File> result = new ArrayList<>();
                    for(String path : mFolderList) {
                        File targetedFolder = new File(path);
                        if(targetedFolder.exists() && targetedFolder.isDirectory()) { // paranoid check, we already checked in dialog!
                            walkFileTree(targetedFolder, result);
                        }
                    }
                    return result;
                }

                public void walkFileTree(File directoryRoot, List<File> resultList) {
                    File[] list = directoryRoot.listFiles();
                    for (File f : list) {
                        if (f.isDirectory()) {
                            walkFileTree(f, resultList);
                        } else if(f.getName().toLowerCase().endsWith("gz")) { // take only zipped files
                            resultList.add(f);
                        }
                    }
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<List<File>> loader, List<File> manPageFiles) {
            if(mLocalPageList.getHeaderViewsCount() > 0) {
                mLocalPageList.removeHeaderView(mLocalPageList.getChildAt(0));
            }
            mLocalPageList.setAdapter(new LocalArchiveArrayAdapter(getActivity(), R.layout.chapter_command_list_item, R.id.command_name_label, manPageFiles));
            if(manPageFiles.isEmpty()) {
                View header = View.inflate(getActivity(), R.layout.add_folder_header, null);
                mLocalPageList.addHeaderView(header);
            }
        }

        @Override
        public void onLoaderReset(Loader<List<File>> loader) {

        }
    }
}
