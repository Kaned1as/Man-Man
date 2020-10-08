package com.adonai.manman.preferences;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.adonai.manman.R;
import com.adonai.manman.Utils;

/**
 * Activity for holding and showing preference fragments
 *
 * @author Oleg Chernovskiy
 */
public class PreferencesActivity extends AppCompatActivity {

    private Toolbar mActionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // should set theme prior to instantiating compat actionbar etc.
        Utils.setupTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        mActionBar = (Toolbar) findViewById(R.id.app_toolbar);
        setSupportActionBar(mActionBar);

        getFragmentManager().beginTransaction().replace(R.id.content_frame, new GlobalPrefsFragment()).commit();
    }

}
