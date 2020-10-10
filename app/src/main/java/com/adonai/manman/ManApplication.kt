package com.adonai.manman

import android.app.Application
import com.adonai.manman.database.DbProvider

/**
 * Place to initialize all data prior to launching activities
 *
 * @author Kanedias
 */
class ManApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DbProvider.setHelper(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        DbProvider.releaseHelper()
    }
}