package com.adonai.manman.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.adonai.manman.entities.ManPage
import com.adonai.manman.entities.ManSectionIndex
import com.adonai.manman.entities.ManSectionItem
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper
import com.j256.ormlite.dao.Dao
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
    val manChaptersDao: Dao<ManSectionItem, String> = getDao(ManSectionItem::class.java)
    val manChapterIndexesDao: Dao<ManSectionIndex, String> = getDao(ManSectionIndex::class.java)
    val manPagesDao: Dao<ManPage, String> = getDao(ManPage::class.java)

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
            TableUtils.clearTable(connectionSource, ManSectionItem::class.java)
            TableUtils.clearTable(connectionSource, ManSectionIndex::class.java)
            TableUtils.clearTable(connectionSource, ManPage::class.java)
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