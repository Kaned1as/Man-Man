package com.adonai.manman.database

import android.content.Context
import com.j256.ormlite.android.apptools.OpenHelperManager

/**
 * Helper class for retrieving database instance
 *
 * @author Kanedias
 */
object DbProvider {
    lateinit var helper: PersistManager

    fun setHelper(context: Context?) {
        helper = OpenHelperManager.getHelper(context, PersistManager::class.java)
    }

    fun releaseHelper() {
        OpenHelperManager.releaseHelper()
    }
}