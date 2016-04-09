package com.adonai.manman;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import com.adonai.manman.adapters.LocalArchiveArrayAdapter;
import com.adonai.manman.misc.AbstractNetworkAsyncLoader;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.CountingInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Fragment for uploading and parsing local man page distributions
 *
 * @author Adonai
 */
public class ManLocalArchiveFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String LOCAL_ARCHIVE_URL = "https://github.com/Adonai/Man-Man/releases/download/1.4.0/manpages.tar.gz";

    private File mLocalArchive;
    private boolean mUserAgreedToDownload;

    private SharedPreferences mPreferences; // needed for folder list retrieval

    private ListView mLocalPageList;
    private SearchView mSearchLocalPage;
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

        mLocalArchive = new File(getActivity().getCacheDir(), "manpages.tar.gz");

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mPreferences.registerOnSharedPreferenceChangeListener(this);

        View root = inflater.inflate(R.layout.fragment_local_storage, container, false);
        mLocalPageList = (ListView) root.findViewById(R.id.local_storage_page_list);
        mLocalPageList.setOnItemClickListener(mManArchiveClickListener);
        mSearchLocalPage = (SearchView) root.findViewById(R.id.local_search_edit);
        mSearchLocalPage.setOnQueryTextListener(new FilterLocalStorage());

        getLoaderManager().initLoader(MainPagerActivity.LOCAL_PACKAGE_LOADER, null, mLocalArchiveParseCallback);
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mBroadcastHandler, new IntentFilter(MainPagerActivity.LOCAL_CHANGE_NOTIFY));

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void showFolderSettingsDialog() {
        new FolderChooseFragment().show(getFragmentManager(), "FolderListFragment");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.local_archive_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.folder_settings:
                showFolderSettingsDialog();
                return true;
            case R.id.download_archive:
                if(mLocalArchive.exists()) {
                    Toast.makeText(getActivity(), R.string.already_downloaded, Toast.LENGTH_SHORT).show();
                    return true;
                }
                downloadArchive();

        }
        return super.onOptionsItemSelected(item);
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

                @Override
                protected void onForceLoad() {
                    //Utils.showToastFromAnyThread(getActivity(), R.string.scanning_folders);
                    super.onForceLoad();
                }

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
                        // it's a tar-gzipped archive with standard structure
                        populateWithLocal(result);
                    }

                    // sort results alphabetically...
                    Collections.sort(result);
                    return result;
                }

                private void populateWithLocal(List<File> result) {
                    try {
                        GZIPInputStream gzis = new GZIPInputStream(new FileInputStream(mLocalArchive));
                        TarArchiveInputStream tis = new TarArchiveInputStream(gzis);
                        TarArchiveEntry tarEntry;
                        while ((tarEntry = tis.getNextTarEntry()) != null) {
                            if(tarEntry.isDirectory())
                                continue;

                            if(tarEntry.isFile()) {
                                result.add(new File("local:", tarEntry.getName()));
                            }
                        }
                        tis.close();
                    } catch (IOException e) {
                        Log.e(Utils.MM_TAG, "Exception while parsing local archive", e);
                        Utils.showToastFromAnyThread(getActivity(), R.string.error_parsing_local_archive);
                    }
                }

                public void walkFileTree(File directoryRoot, List<File> resultList) {
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
     * Load archive to app data folder from my github releases page
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
