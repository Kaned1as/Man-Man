package com.adonai.manman.misc

import android.content.Context
import androidx.loader.content.AsyncTaskLoader

/**
 * Almost full copy-paste from
 * [Implementing loaders](http://www.androiddesignpatterns.com/2012/08/implementing-loaders.html)
 * blog post
 *
 * @author Kanedias
 */
abstract class AbstractNetworkAsyncLoader<D>(context: Context) : AsyncTaskLoader<D?>(context) {
    private var mData: D? = null

    override fun onStopLoading() {
        cancelLoad()
    }

    override fun onStartLoading() {
        if (mData != null) {
            // Deliver any previously loaded data immediately.
            deliverResult(mData)
        }
        if (takeContentChanged() || mData == null) {
            forceLoad()
        }
    }

    override fun onLoadInBackground(): D? {
        mData = loadInBackground()
        return mData
    }

    override fun deliverResult(data: D?) {
        if (isReset) {
            // The Loader has been reset; ignore the result and invalidate the data.
            return
        }
        if (isStarted) {
            // If the Loader is in a started state, deliver the results to the
            // client. The superclass method does this for us.
            super.deliverResult(data)
        }
    }

    override fun onReset() {
        mData = null
    }
}