package com.adonai.manman;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
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
import com.adonai.manman.views.ProgressBarWrapper;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.apache.commons.compress.utils.CountingInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Fragment for uploading and parsing local man page distributions
 *
 * @author Oleg Chernovskiy
 */
public class ManLocalArchiveFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String LOCAL_ARCHIVE_URL = "https://github.com/Adonai/Man-Man/releases/download/1.6.0/manpages.zip";

    private File mLocalArchive;
    private boolean mUserAgreedToDownload;

    private SharedPreferences mPreferences; // needed for folder list retrieval

    private ListView mLocalPageList;
    private SearchView mSearchLocalPage;
    private ProgressBarWrapper mProgress; // TODO: move progress bar to activity (and duplicate in chapters fragment too)
    private BroadcastReceiver mBroadcastHandler = new LocalArchiveBroadcastReceiver();
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
                switch (position) {
                    case 0: // Watch folders
                        showFolderSettingsDialog();
                        break;
                    case 1: // Download archive
                        downloadArchive();
                        break;
                }
            } else {
                ManPageDialogFragment mpdf = ManPageDialogFragment.newInstance(data.getName(), data.getPath());
                getFragmentManager()
                        .beginTransaction()
                        .addToBackStack("PageFromLocalArchive")
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .replace(R.id.replacer, mpdf)
                        .commit();
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
        setHasOptionsMenu(true);

        mLocalArchive = new File(getActivity().getCacheDir(), "manpages.zip");

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mPreferences.registerOnSharedPreferenceChangeListener(this);

        View root = inflater.inflate(R.layout.fragment_local_storage, container, false);
        mLocalPageList = (ListView) root.findViewById(R.id.local_storage_page_list);
        mLocalPageList.setOnItemClickListener(mManArchiveClickListener);
        mSearchLocalPage = (SearchView) root.findViewById(R.id.local_search_edit);
        mSearchLocalPage.setOnQueryTextListener(new FilterLocalStorage());
        mProgress = new ProgressBarWrapper(getActivity());

        getLoaderManager().initLoader(MainPagerActivity.LOCAL_PACKAGE_LOADER, null, mLocalArchiveParseCallback);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastHandler, new IntentFilter(MainPagerActivity.LOCAL_CHANGE_NOTIFY));

        return root;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mProgress.onOrientationChanged();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mProgress.hide();
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.local_archive_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // don't show it if we already have archive
        menu.findItem(R.id.download_archive).setVisible(!mLocalArchive.exists());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.folder_settings:
                showFolderSettingsDialog();
                return true;
            case R.id.download_archive:
                downloadArchive();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(MainPagerActivity.FOLDER_LIST_KEY)) { // the only needed key
            getLoaderManager().getLoader(MainPagerActivity.LOCAL_PACKAGE_LOADER).onContentChanged();
        }
    }

    private void showFolderSettingsDialog() {
        new FolderChooseFragment().show(getFragmentManager(), "FolderListFragment");
    }

    private class LocalArchiveParserCallback implements LoaderManager.LoaderCallbacks<List<File>> {
        @Override
        public Loader<List<File>> onCreateLoader(int i, Bundle bundle) {
            return new AbstractNetworkAsyncLoader<List<File>>(getActivity()) {
                Set<String> mFolderList;

                @NonNull
                @Override
                public List<File> loadInBackground() {
                    // results from locally-defined folders
                    mFolderList = mPreferences.getStringSet(MainPagerActivity.FOLDER_LIST_KEY, new HashSet<String>());
                    List<File> result = new ArrayList<>();
                    for(String path : mFolderList) {
                        File targetedFolder = new File(path);
                        if(targetedFolder.exists() && targetedFolder.isDirectory()) { // paranoid check, we already checked in dialog!
                            walkFileTree(targetedFolder, result);
                        }
                    }

                    // results from local archive, if exists
                    if(mLocalArchive.exists()) {
                        // show progress bar
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mProgress.show();
                            }
                        });
                        // it's a tar-gzipped archive with standard structure
                        populateWithLocal(result);
                    }

                    // sort results alphabetically...
                    Collections.sort(result);
                    return result;
                }

                private void populateWithLocal(List<File> result) {
                    try {
                        ZipFile zip = new ZipFile(mLocalArchive);
                        Enumeration<? extends ZipEntry> entries = zip.entries();
                        while (entries.hasMoreElements()) {
                            ZipEntry zEntry = entries.nextElement();
                            if(zEntry.isDirectory())
                                continue;

                            result.add(new File("local:", zEntry.getName()));
                        }
                    } catch (IOException e) {
                        Log.e(Utils.MM_TAG, "Exception while parsing local archive", e);
                        Utils.showToastFromAnyThread(getActivity(), R.string.error_parsing_local_archive);
                    }
                }

                private void walkFileTree(File directoryRoot, List<File> resultList) {
                    File[] list = directoryRoot.listFiles();
                    if(list == null) // unknown, happens on some devices
                        return;

                    for (File f : list) {
                        if (f.isDirectory()) {
                            walkFileTree(f, resultList);
                        } else if(f.getName().toLowerCase().endsWith(".gz")) { // take only gzipped files
                            resultList.add(f);
                        }
                    }
                }

                @Override
                public void deliverResult(List<File> data) {
                    mProgress.hide();
                    super.deliverResult(data);
                }
            };
        }

        @Override
        public void onLoadFinished(Loader<List<File>> loader, List<File> manPageFiles) {
            if(mLocalPageList.getHeaderViewsCount() > 0) {
                mLocalPageList.removeHeaderView(mLocalPageList.getChildAt(0));
                mLocalPageList.removeHeaderView(mLocalPageList.getChildAt(1));
            }
            mLocalPageList.setAdapter(null); // for android < kitkat for header to work properly

            if(manPageFiles.isEmpty()) {
                mSearchLocalPage.setVisibility(View.GONE);
                View header1 = View.inflate(getActivity(), R.layout.add_folder_header, null);
                View header2 = View.inflate(getActivity(), R.layout.load_archive_header, null);
                mLocalPageList.addHeaderView(header1);
                mLocalPageList.addHeaderView(header2);
            } else {
                mSearchLocalPage.setVisibility(View.VISIBLE);
            }
            mLocalPageList.setAdapter(new LocalArchiveArrayAdapter(getActivity(), R.layout.chapter_command_list_item, R.id.command_name_label, manPageFiles));
        }

        @Override
        public void onLoaderReset(Loader<List<File>> loader) {

        }
    }

    /**
     * Load archive to app data folder from my GitHub releases page
     */
    private void downloadArchive() {
        if(mLocalArchive.exists()) {
            return;
        }

        if(!mUserAgreedToDownload) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.confirm_action)
                    .setMessage(R.string.confirm_action_load_archive)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mUserAgreedToDownload = true;
                            downloadArchive();
                        }
                    }).setNegativeButton(android.R.string.no, null)
                    .create().show();
            return;
        }

        // kind of stupid to make a loader just for oneshot DL task...
        // OK, let's do it old way...
        AsyncTask<String, Long, Void> dlTask = new AsyncTask<String, Long, Void>() {

            private Exception possibleEncountered;
            private ProgressDialog pd;

            @Override
            protected void onPreExecute() {
                pd = ProgressDialog.show(getActivity(),
                        getString(R.string.downloading),
                        getString(R.string.please_wait), true);
            }

            @Override
            protected Void doInBackground(String... params) {
                try {
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder().url(params[0]).build();
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        publishProgress(-2L);
                        return null;
                    }

                    Long contentLength = response.body().contentLength();
                    CountingInputStream cis = new CountingInputStream(response.body().byteStream());
                    FileOutputStream fos = new FileOutputStream(mLocalArchive);
                    byte[] buffer = new byte[8096];
                    int read;
                    while ((read = cis.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        publishProgress(cis.getBytesRead() * 100 / contentLength);
                    }
                    fos.close();
                    cis.close();
                } catch (IOException e) {
                    Log.e(Utils.MM_TAG, "Exception while downloading man pages archive", e);
                    possibleEncountered = e;
                    publishProgress(-1L);
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Long... values) {
                if(values[0] == -1) { // exception
                    Utils.showToastFromAnyThread(getActivity(), possibleEncountered.getLocalizedMessage());
                }

                if(values[0] == -2) { // response is not OK
                    Utils.showToastFromAnyThread(getActivity(), R.string.no_archive_on_server);
                }

                pd.setMessage(getString(R.string.please_wait) + " " + values[0] + "%");
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                pd.dismiss();
                getLoaderManager().getLoader(MainPagerActivity.LOCAL_PACKAGE_LOADER).onContentChanged();
            }
        };
        dlTask.execute(LOCAL_ARCHIVE_URL);
    }

    private class FilterLocalStorage implements SearchView.OnQueryTextListener {

        @Override
        public boolean onQueryTextSubmit(String query) {
            applyFilter(query);
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            applyFilter(newText);
            return true;
        }

        private void applyFilter(CharSequence text) {
            // safe to cast, we have only this type of adapter here
            LocalArchiveArrayAdapter adapter = (LocalArchiveArrayAdapter) mLocalPageList.getAdapter();
            if(adapter != null) { // another paranoid check?
                adapter.getFilter().filter(text);
            }
        }
    }

    /**
     * Handler to receive notifications for changes in local archive (to update local list view)
     */
    private class LocalArchiveBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            getLoaderManager().getLoader(MainPagerActivity.LOCAL_PACKAGE_LOADER).onContentChanged();
        }
    }
}
