package com.adonai.manman.preferences;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.adonai.manman.R;

import java.util.List;

/**
 * Activity for holding and showing preference fragments
 *
 * @author Adonai
 */
public class PreferencesActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(android.R.style.Theme_Holo);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return true;
    }

    /**
     * Populate the activity with the top-level headers.
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.global_prefs_headers, target);
    }

}
