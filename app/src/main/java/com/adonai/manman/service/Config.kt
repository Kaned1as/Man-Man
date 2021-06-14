package com.adonai.manman.service

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * Main shared configuration holder class
 *
 * @author Kanedias
 *
 * Created on 2020-01-05
 */
object Config {

    const val APP_THEME = "app.theme"

    lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx.applicationContext)
    }

    var appTheme: String
        get() = prefs.getString(APP_THEME, "default")!!
        set(theme) = prefs.edit().putString(APP_THEME, theme).apply()
}