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
    const val USER_LEARNED_SLIDER = "user.learned.slider"
    const val FONT_PREF_KEY = "webview.font.size"

    lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx.applicationContext)
    }

    var appTheme: String
        get() = prefs.getString(APP_THEME, "default")!!
        set(theme) = prefs.edit().putString(APP_THEME, theme).apply()

    var userLearnedSlider: Boolean
        get() = prefs.getBoolean(USER_LEARNED_SLIDER, false)
        set(learned) = prefs.edit().putBoolean(USER_LEARNED_SLIDER, learned).apply()

    var fontSize: Int
        get() = prefs.getString(FONT_PREF_KEY, "12")!!.toIntOrNull() ?: 12
        set(selectedSize) = prefs.edit().putString(FONT_PREF_KEY, selectedSize.toString()).apply()
}