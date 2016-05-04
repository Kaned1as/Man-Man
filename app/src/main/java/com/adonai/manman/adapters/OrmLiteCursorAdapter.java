package com.adonai.manman.adapters;

import android.app.Activity;
import android.database.Cursor;
import android.widget.BaseAdapter;

import com.j256.ormlite.android.AndroidDatabaseResults;
import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.stmt.PreparedQuery;

import java.sql.SQLException;

/**
 * Convenient class for creating ListView adapters
 * <br/>
 * This task could be achieved using just ArrayAdapter with OrmLite query results
 * but for some chapters with 15000 commands in them it takes too much time
 *
 * @author Oleg Chernovskiy
 */
public abstract class OrmLiteCursorAdapter<T> extends BaseAdapter {

    private RuntimeExceptionDao<T, ?> mDao;
    private PreparedQuery<T> mQuery = null;

    protected CloseableIterator<T> mCursor;
    protected Activity mContext;

    public OrmLiteCursorAdapter(Activity context, RuntimeExceptionDao<T, ?> dao, PreparedQuery<T> query) {
        mContext = context;
        mDao = dao;
        mQuery = query;
        updateQuery();
    }

    @Override
    public int getCount() {
        return ((AndroidDatabaseResults) mCursor.getRawResults()).getCount();
    }

    @Override
    public T getItem(int position) {
        try {
            Cursor cur = getRawCursor();
            cur.moveToPosition(position);
            return mQuery.mapRow(new AndroidDatabaseResults(cur, mCursor.getRawResults().getObjectCache()));
        } catch (SQLException e) {
            return null;
        }
    }

    public Cursor getRawCursor() {
        return ((AndroidDatabaseResults) mCursor.getRawResults()).getRawCursor();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private void updateQuery() {
        try {
            if(mCursor != null) { // close old cursor if exists
                mCursor.close();
            }

            mCursor = mDao.iterator(mQuery);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void closeCursor() {
        mCursor.closeQuietly();
    }
}
