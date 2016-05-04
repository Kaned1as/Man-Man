package com.adonai.manman;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.adonai.manman.misc.FolderAddDialog;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * A dialog for showing and managing list of watched folders of local man archive.
 * Each folder is parsed recursively to retrieve list of man pages afterwards
 *
 * @see com.adonai.manman.ManLocalArchiveFragment
 * @author Oleg Chernovskiy
 */
public class FolderChooseFragment extends DialogFragment {

    private ImageView mAddButton;

    private Set<String> mStoredFolders;
    private SharedPreferences mSharedPrefs;
    private ListView mFolderList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mStoredFolders = new HashSet<>();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // get already stored folders from prefs...
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mStoredFolders.addAll(mSharedPrefs.getStringSet(MainPagerActivity.FOLDER_LIST_KEY, new HashSet<String>()));

        View title = View.inflate(getActivity(), R.layout.folder_list_dialog_title, null);
        TextView titleText = (TextView) title.findViewById(android.R.id.title);
        titleText.setText(R.string.watched_folders);
        mAddButton = (ImageView) title.findViewById(R.id.add_local_folder);
        mAddButton.setOnClickListener(new AddFolderClickListener());

        mFolderList = new ListView(getActivity());
        mFolderList.setAdapter(new FolderListArrayAdapter(getActivity(), mStoredFolders.toArray(new String[mStoredFolders.size()])));

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setCustomTitle(title);
        builder.setView(mFolderList);
        return builder.create();
    }

    private class AddFolderClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            FolderAddDialog folder = FolderAddDialog.newInstance(new FolderAddDialog.ResultFolderListener() {
                @Override
                public void receiveResult(File resultDir) {
                    // add dir to the list
                    mStoredFolders.add(resultDir.getAbsolutePath());
                    syncFolderList();
                }
            });
            folder.show(getFragmentManager(), "FolderChooseFragment");
        }
    }

    private class FolderListArrayAdapter extends ArrayAdapter<String> {

        public FolderListArrayAdapter(Context context, String[] objects) {
            super(context, R.layout.folder_list_dialog_item, android.R.id.title, objects);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final View cached = super.getView(position, convertView, parent);
            final String current = getItem(position);

            ImageView img = (ImageView) cached.findViewById(R.id.remove_local_folder);
            img.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mStoredFolders.remove(current);
                    syncFolderList();
                }
            });

            return cached;
        }
    }

    /**
     * Should be called from UI thread...
     */
    private void syncFolderList() {
        mFolderList.setAdapter(new FolderListArrayAdapter(getActivity(), mStoredFolders.toArray(new String[mStoredFolders.size()])));

        // sync with shared prefs
        mSharedPrefs.edit().putStringSet(MainPagerActivity.FOLDER_LIST_KEY, mStoredFolders).apply();
    }
}
