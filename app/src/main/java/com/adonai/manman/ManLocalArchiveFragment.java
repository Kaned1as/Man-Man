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
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;

import com.adonai.manman.adapters.LocalArchiveArrayAdapter;
import com.adonai.manman.misc.AbstractNetworkAsyncLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fragment for uploading and parsing local man page distributions
 *
 * @author Adonai
 */
public class ManLocalArchiveFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences mPreferences; // needed for folder list retrieval

    private ListView mLocalPageList;
    private SearchView mSearchLocalPage;
    private LocalArchiveParserCallback mLocalArchiveParseCallback = new LocalArchiveParserCallback();

    /**
     * Click listener for loading man page from selected archive file (or show config if no folders are present)
     * <br/>
     * Archives are pretty small, so gzip decompression and parsing won't take loads of time...
     * <br/>
     * Long story short, let's try to do this in UI and look at the performance
     *
     */
    private AdapterView.OnItemClickListener mManArchiveClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            File data = (File) parent.getItemAtPosition(position);
            if(data == null) { // header is present, start config tool
                showFolderSettingsDialog();
            } else {
                ManPageDialogFragment.newInstance(data.getName(), data.getAbsolutePath()).show(getFragmentManager(), "manPage");
            }
        }
    };

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
        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mPreferences.registerOnSharedPreferenceChangeListener(this);

        View root = inflater.inflate(R.layout.fragment_local_storage, container, false);
        mLocalPageList = (ListView) root.findViewById(R.id.local_storage_page_list);
        mLocalPageList.setOnItemClickListener(mManArchiveClickListener);
        mSearchLocalPage = (SearchView) root.findViewById(R.id.local_search_edit);
        mSearchLocalPage.setOnQueryTextListener(null);

        getLoaderManager().initLoader(MainPagerActivity.LOCAL_PACKAGE_LOADER, null, mLocalArchiveParseCallback);
        setHasOptionsMenu(true);

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
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
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(MainPagerActivity.FOLDER_LIST_KEY)) { // the only needed key
            getLoaderManager().getLoader(MainPagerActivity.LOCAL_PACKAGE_LOADER).onContentChanged();
        }
    }

    private class LocalArchiveParserCallback implements LoaderManager.LoaderCallbacks<List<File>> {
        @Override
        public Loader<List<File>> onCreateLoader(int i, Bundle bundle) {
            return new AbstractNetworkAsyncLoader<List<File>>(getActivity()) {
                Set<String> mFolderList;

                @NonNull
                @Override
                public List<File> loadInBackground() {
                    mFolderList = mPreferences.getStringSet(MainPagerActivity.FOLDER_LIST_KEY, new HashSet<String>());
                    List<File> result = new ArrayList<>();
                    for(String path : mFolderList) {
                        File targetedFolder = new File(path);
                        if(targetedFolder.exists() && targetedFolder.isDirectory()) { // paranoid check, we already checked in dialog!
                            walkFileTree(targetedFolder, result);
                        }
                    }
                    // sort results alphabetically...
                    Collections.sort(result, new Comparator<File>() {
                        @Override
                        public int compare(File lhs, File rhs) {
                            return lhs.getName().compareTo(rhs.getName());
                        }
                    });
                    return result;
                }

                public void walkFileTree(File directoryRoot, List<File> resultList) {
                    File[] list = directoryRoot.listFiles();
                    for (File f : list) {
                        if (f.isDirectory()) {
                            walkFileTree(f, resultList);
                        } else if(f.getName().toLowerCase().endsWith("gz")) { // take only gzipped files
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
