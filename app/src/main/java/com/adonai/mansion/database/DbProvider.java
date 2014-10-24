package com.adonai.mansion.database;

import android.content.Context;

import com.j256.ormlite.android.apptools.OpenHelperManager;

/**
 * Created by adonai on 29.06.14.
 */
public class DbProvider {
    private static PersistManager databaseHelper;

    public static PersistManager getHelper(){
        return databaseHelper;
    }

    public static void setHelper(Context context) {
        databaseHelper = OpenHelperManager.getHelper(context, PersistManager.class);
    }

    public static void releaseHelper() {
        OpenHelperManager.releaseHelper();
        databaseHelper = null;
    }
}
