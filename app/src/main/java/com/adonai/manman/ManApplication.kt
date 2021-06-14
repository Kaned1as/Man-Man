package com.adonai.manman

import android.app.Application
import com.adonai.manman.database.DbProvider
import com.adonai.manman.service.Config

/**
 * Place to initialize all data prior to launching activities
 *
 * @author Kanedias
 */
class ManApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Config.init(this)
        DbProvider.setHelper(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        DbProvider.releaseHelper()
    }
}