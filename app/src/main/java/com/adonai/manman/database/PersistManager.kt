package com.adonai.manman.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.adonai.manman.database.DbProvider.helper
import com.adonai.manman.entities.ManPage
import com.adonai.manman.entities.ManSectionIndex
import com.adonai.manman.entities.ManSectionItem
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.dao.RuntimeExceptionDao
import com.j256.ormlite.support.ConnectionSource
import com.j256.ormlite.table.TableUtils
import java.sql.SQLException

/**
 * Helper class for managing OrmLite database and DAOs
 *
 * @author Kanedias
 */
class PersistManager(context: Context) : OrmLiteSqliteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    //Dao fast access links
    lateinit var manChaptersDao: Dao<ManSectionItem, String>
    lateinit var manChapterIndexesDao: Dao<ManSectionIndex, String>
    lateinit var manPagesDao: Dao<ManPage, String>

    override fun onCreate(db: SQLiteDatabase, connectionSource: ConnectionSource) {
        try {
            TableUtils.createTable(connectionSource, ManSectionItem::class.java)
            TableUtils.createTable(connectionSource, ManSectionIndex::class.java)
            TableUtils.createTable(connectionSource, ManPage::class.java)
        } catch (e: SQLException) {
            Log.e(TAG, "error creating DB $DATABASE_NAME")
            throw RuntimeException(e)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, connectionSource: ConnectionSource, oldVer: Int, newVer: Int) {}

    fun clearAllTables() {
        try {
            TableUtils.clearTable(helper.getConnectionSource(), ManSectionItem::class.java)
            TableUtils.clearTable(helper.getConnectionSource(), ManSectionIndex::class.java)
            TableUtils.clearTable(helper.getConnectionSource(), ManPage::class.java)
        } catch (e: SQLException) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private val TAG = PersistManager::class.java.simpleName
        private const val DATABASE_NAME = "manman.db"
        private const val DATABASE_VERSION = 1
    }
}