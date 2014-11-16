package com.adonai.manman;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.adonai.manman.misc.FolderAddDialog;

import java.io.File;

/**
 * A dialog for showing and managing list of watched folders of local man archive.
 * Each folder is parsed recursively to retrieve list of man pages afterwards
 *
 * @see com.adonai.manman.ManLocalArchiveFragment
 * @author Adonai
 */
public class FolderChooseFragment extends DialogFragment {

    private ImageView mAddButton;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View title = View.inflate(getActivity(), R.layout.folder_list_dialog_title, null);
        TextView titleText = (TextView) title.findViewById(android.R.id.title);
        titleText.setText(R.string.watched_folders);
        mAddButton = (ImageView) title.findViewById(R.id.add_local_folder);
        mAddButton.setOnClickListener(new AddFolderClickListener());


        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setCustomTitle(title);
        return builder.create();
    }

    private class AddFolderClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            FolderAddDialog folder = FolderAddDialog.newInstance(new FolderAddDialog.ResultFolderListener() {
                @Override
                public void receiveResult(File resultDir) {

                }
            });
            folder.show(getFragmentManager(), "FolderChooseFragment");
        }
    }
}
