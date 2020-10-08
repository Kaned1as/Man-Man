package com.adonai.manman.misc;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.adonai.manman.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A helper fragment responsible for picking folders of local manpage archives for further parsing
 * Returns selected folder on successful completion via ResultFolderListener interface
 *
 * @author Oleg Chernovskiy
 * @see com.adonai.manman.FolderChooseFragment
 */
public class FolderAddDialog extends DialogFragment implements DialogInterface.OnClickListener, AdapterView.OnItemClickListener {

    public interface ResultFolderListener {
        void receiveResult(File resultDir);
    }

    private ListView mFolderList;
    private TextView mFolderTitle;

    private File pwd;
    private ResultFolderListener listener;

    public static FolderAddDialog newInstance(ResultFolderListener listener) {
        FolderAddDialog fragment = new FolderAddDialog();
        fragment.listener = listener;
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        File external = Environment.getExternalStorageDirectory();
        pwd = external.exists() && external.canRead() ? external : new File("/");

        View folderSelector = View.inflate(getActivity(), R.layout.folder_selector_dialog, null);
        mFolderList = (ListView) folderSelector.findViewById(R.id.folder_list);
        mFolderList.setOnItemClickListener(this);
        mFolderTitle = (TextView) folderSelector.findViewById(R.id.folder_title);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setPositiveButton(R.string.select, this);
        builder.setNegativeButton(android.R.string.cancel, this);
        builder.setView(folderSelector);
        builder.setTitle(R.string.select_folder);
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        cdInto(pwd);
    }

    private void cdInto(File currentDir) {
        mFolderTitle.setText(getString(R.string.current_folder) + currentDir.getPath());
        List<File> shownFolders = new ArrayList<>();
        File[] files = currentDir.listFiles();

        if(currentDir.getParent() != null) {
            shownFolders.add(currentDir.getParentFile());
        }

        for(File file : files) {
            if(file.isDirectory()) {
                shownFolders.add(file);
            }
        }

        ArrayAdapter<File> fileList = new FolderArrayAdapter(getActivity(), R.layout.folder_list_item, R.id.folder_item_title, shownFolders);
        mFolderList.setAdapter(fileList);
        pwd = currentDir;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                listener.receiveResult(pwd);
                return;
            default:
                break;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        File dir = (File) parent.getItemAtPosition(position);
        if(dir.canRead())
            cdInto(dir);
    }

    private class FolderArrayAdapter extends ArrayAdapter<File> {

        public FolderArrayAdapter(Context context, int resource, int textViewResourceId, List<File> objects) {
            super(context, resource, textViewResourceId, objects);
        }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            File current = getItem(pos);
            View view;
            if (convertView == null)
                view = View.inflate(getContext(), R.layout.folder_list_item, null);
            else
                view = convertView;

            TextView title = (TextView)view.findViewById(R.id.folder_item_title);
            if(pos == 0 && current.getPath().equals(pwd.getParent())) {
                title.setText("..");
            } else {
                String relative = pwd.toURI().relativize(current.toURI()).getPath();
                title.setText(relative);
            }
            return view;
        }
    }

}
