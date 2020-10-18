package com.adonai.manman.adapters

import android.content.Context
import android.database.Cursor
import android.widget.BaseAdapter
import com.j256.ormlite.android.AndroidDatabaseResults
import com.j256.ormlite.dao.CloseableIterator
import com.j256.ormlite.dao.Dao
import com.j256.ormlite.stmt.PreparedQuery
import java.io.IOException

/**
 * Convenient class for creating ListView adapters
 *
 * This task could be achieved using just ArrayAdapter with OrmLite query results
 * but for some chapters with 15000 commands in them it takes too much time
 *
 * @author Kanedias
 */
abstract class OrmLiteCursorAdapter<T>(protected var mContext: Context, private val mDao: Dao<T, *>, private val mQuery: PreparedQuery<T>) : BaseAdapter() {
    protected var mCursor: CloseableIterator<T>? = null

    override fun getCount(): Int {
        return (mCursor!!.rawResults as AndroidDatabaseResults).count
    }

    override fun getItem(position: Int): T {
        val cur = rawCursor
        cur.moveToPosition(position)
        return mQuery.mapRow(AndroidDatabaseResults(cur, mCursor!!.rawResults.objectCacheForRetrieve, false))
    }

    private val rawCursor: Cursor
        get() = (mCursor!!.rawResults as AndroidDatabaseResults).rawCursor

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun updateQuery() {
        mCursor = try {
            if (mCursor != null) { // close old cursor if exists
                closeCursor()
            }
            mDao.iterator(mQuery)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    fun closeCursor() {
        mCursor!!.closeQuietly()
    }

    init {
        updateQuery()
    }
}