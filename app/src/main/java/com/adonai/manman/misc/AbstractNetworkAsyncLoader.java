package com.adonai.manman.misc;

import android.content.Context;
import androidx.loader.content.AsyncTaskLoader;

/**
 * Almost full copy-paste from
 * <a href="http://www.androiddesignpatterns.com/2012/08/implementing-loaders.html">Implementing loaders</a>
 * blog post
 *
 * @author Oleg Chernovskiy
 */
public abstract class AbstractNetworkAsyncLoader<D> extends AsyncTaskLoader<D> {

    private D mData;

    public AbstractNetworkAsyncLoader(Context context) {
        super(context);
    }

    @Override
    protected void onStopLoading() {
        cancelLoad();
    }

    @Override
    protected void onStartLoading() {
        if (mData != null) {
            // Deliver any previously loaded data immediately.
            deliverResult(mData);
        }

        if (takeContentChanged() || mData == null) {
            forceLoad();
        }
    }

    @Override
    protected D onLoadInBackground() {
        mData = loadInBackground();
        return mData;
    }

    @Override
    public void deliverResult(D data) {
        if (isReset()) {
            // The Loader has been reset; ignore the result and invalidate the data.
            return;
        }

        if (isStarted()) {
            // If the Loader is in a started state, deliver the results to the
            // client. The superclass method does this for us.
            super.deliverResult(data);
        }
    }

    @Override
    protected void onReset() {
        mData = null;
    }
}
