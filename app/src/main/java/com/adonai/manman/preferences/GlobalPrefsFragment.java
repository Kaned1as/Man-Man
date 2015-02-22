package com.adonai.manman.preferences;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v4.content.LocalBroadcastManager;

import com.adonai.manman.MainPagerActivity;
import com.adonai.manman.R;
import com.adonai.manman.database.DbProvider;

/**
 * Fragment for showing and managing global preferences
 *
 * @author Adonai
 */
public class GlobalPrefsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.global_prefs);
        findPreference("clear.cache").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(R.string.confirm_action).setMessage(R.string.clear_cache_question)
                        .setNegativeButton(android.R.string.no, null).setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (DbProvider.getHelper() == null) { // main activity is paused, so DB is released, should bind
                            DbProvider.setHelper(getActivity());
                            DbProvider.getHelper().clearAllTables();
                            DbProvider.releaseHelper();
                        } else { // not paused
                            DbProvider.getHelper().clearAllTables();
                        }
                        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(new Intent(MainPagerActivity.DB_CHANGE_NOTIFY));
                    }
                }).create().show();
                return true;
            }
        });
    }
}
