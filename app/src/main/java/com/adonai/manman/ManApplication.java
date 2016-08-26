package com.adonai.manman;

import android.app.Application;

import com.adonai.manman.database.DbProvider;

/**
 * Place to initialize all data prior to launching activities
 * @author Adonai
 */
public class ManApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DbProvider.setHelper(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        DbProvider.releaseHelper();
    }
}
