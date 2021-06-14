package com.adonai.manman

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adonai.manman.misc.setupTheme
import com.adonai.manman.service.Config
import kotlinx.coroutines.launch

/**
 * Activity that's themed and tracks theme change
 *
 * @author Kanedias
 *
 * Created on 2020-01-23
 */
abstract class ThemedActivity: AppCompatActivity() {

    private lateinit var themeChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupTheme()
        themeChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != Config.APP_THEME)
                return@OnSharedPreferenceChangeListener

            lifecycleScope.launch {
                setupTheme()
                recreate()
            }
        }
        Config.prefs.registerOnSharedPreferenceChangeListener(themeChangeListener)
    }
}