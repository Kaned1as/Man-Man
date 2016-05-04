package com.adonai.manman.database;

import android.content.Context;

import com.j256.ormlite.android.apptools.OpenHelperManager;

/**
 * Helper class for retrieving database instance
 *
 * @author Oleg Chernovskiy
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
