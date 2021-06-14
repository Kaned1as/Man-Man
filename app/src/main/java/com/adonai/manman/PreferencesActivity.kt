package com.adonai.manman

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

/**
 * Activity for holding and showing preference fragments
 *
 * @author Kanedias
 */
class PreferencesActivity : ThemedActivity() {

    private lateinit var mActionBar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        // should set theme prior to instantiating compat actionbar etc.
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        mActionBar = findViewById<View>(R.id.app_toolbar) as Toolbar
        setSupportActionBar(mActionBar)

        supportFragmentManager.beginTransaction().replace(R.id.content_frame, GlobalPrefsFragment()).commit()
    }
}