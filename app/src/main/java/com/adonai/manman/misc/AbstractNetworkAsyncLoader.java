package com.adonai.manman.misc;

import android.content.Context;
import android.support.v4.content.AsyncTaskLoader;

/**
 * Almost full copy-paste from
 * <a href="http://www.androiddesignpatterns.com/2012/08/implementing-loaders.html">Implementing loaders</a>
 * blog post
 */
public abstract class AbstractNetworkAsyncLoader<D> extends AsyncTaskLoader<D> {

    private D mData;

    public AbstractNetworkAsyncLoader(Context context) {
        super(context);
    }

    @Override
    protected void onStopLoading() {
        super.onStopLoading();
        cancelLoad();
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        if (takeContentChanged() || mData == null) {
            forceLoad();
        }
    }

    @Override
    public void deliverResult(D data) {
        if (isReset()) {
            // The Loader has been reset; ignore the result and invalidate the data.
            return;
        }

        // Hold a reference to the old data so it doesn't get garbage collected.
        // We must protect it until the new data has been delivered.
        mData = data;

        if (isStarted()) {
            // If the Loader is in a started state, deliver the results to the
            // client. The superclass method does this for us.
            super.deliverResult(data);
        }
    }
}
