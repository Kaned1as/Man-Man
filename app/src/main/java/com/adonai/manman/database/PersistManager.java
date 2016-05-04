package com.adonai.manman.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.util.Log;

import com.adonai.manman.entities.ManPage;
import com.adonai.manman.entities.ManSectionIndex;
import com.adonai.manman.entities.ManSectionItem;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import java.sql.SQLException;

/**
 * Helper class for managing OrmLite database and DAOs
 *
 * @author Oleg Chernovskiy
 */
public class PersistManager extends OrmLiteSqliteOpenHelper {
    private static final String TAG = PersistManager.class.getSimpleName();

    private static final String DATABASE_NAME ="manman.db";

    private static final int DATABASE_VERSION = 1;

    //Dao fast access links
    private RuntimeExceptionDao<ManSectionItem, String> manChaptersDao;
    private RuntimeExceptionDao<ManSectionIndex, String> manChapterIndexesDao;
    private RuntimeExceptionDao<ManPage, String> manPagesDao;

    public PersistManager(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db, ConnectionSource connectionSource) {
        try {
            TableUtils.createTable(connectionSource, ManSectionItem.class);
            TableUtils.createTable(connectionSource, ManSectionIndex.class);
            TableUtils.createTable(connectionSource, ManPage.class);
        } catch (SQLException e) {
            Log.e(TAG, "error creating DB " + DATABASE_NAME);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, ConnectionSource connectionSource, int oldVer, int newVer) {

    }

    @NonNull
    public RuntimeExceptionDao<ManSectionItem, String> getManChaptersDao() {
        if (manChaptersDao == null) {
            manChaptersDao = getRuntimeExceptionDao(ManSectionItem.class);
        }
        return manChaptersDao;
    }

    @NonNull
    public RuntimeExceptionDao<ManPage, String> getManPagesDao() {
        if (manPagesDao == null) {
            manPagesDao = getRuntimeExceptionDao(ManPage.class);
        }
        return manPagesDao;
    }

    @NonNull
    public RuntimeExceptionDao<ManSectionIndex, String> getManChapterIndexesDao() {
        if (manChapterIndexesDao == null) {
            manChapterIndexesDao = getRuntimeExceptionDao(ManSectionIndex.class);
        }
        return manChapterIndexesDao;
    }

    public void clearAllTables() {
        try {
            TableUtils.clearTable(DbProvider.getHelper().getConnectionSource(), ManSectionItem.class);
            TableUtils.clearTable(DbProvider.getHelper().getConnectionSource(), ManSectionIndex.class);
            TableUtils.clearTable(DbProvider.getHelper().getConnectionSource(), ManPage.class);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        super.close();
    }
}
