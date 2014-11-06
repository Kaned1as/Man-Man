package com.adonai.manman.views;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.support.annotation.NonNull;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ProgressBar;

import com.adonai.manman.R;

/**
 * This view is for showing progress bar under a actionbar
 * I looked through the github and found only progressbars bound to other projects
 * So it will be here in case someone needs it.
 * <br/>
 * SmoothProgressBar is easily replaceable with any other type in layout
 *
 * Original author should be Chris Banes
 *
 * @author Adonai
 */
public class ProgressBarWrapper {

    private AddHeaderViewRunnable mAddRunnable;
    private ProgressBar mProgressBar;
    private Activity mActivity;

    private boolean isShowing;

    public ProgressBarWrapper(@NonNull Activity context) {
        mActivity = context;
        mProgressBar = createProgressBar();
        mAddRunnable = new AddHeaderViewRunnable();
    }

    protected int getActionBarSize(Context context) {
        int[] attrs = {android.R.attr.actionBarSize};
        TypedArray values = context.getTheme().obtainStyledAttributes(attrs);
        try {
            return values.getDimensionPixelSize(0, 0);
        } finally {
            values.recycle();
        }
    }

    private ProgressBar createProgressBar() {
        ProgressBar pb = (ProgressBar) View.inflate(mActivity, R.layout.actionbar_progressbar, null);
        ShapeDrawable shape = new ShapeDrawable();
        shape.setShape(new RectShape());
        shape.getPaint().setColor(Color.parseColor("#FF33B5E5"));
        ClipDrawable clipDrawable = new ClipDrawable(shape, Gravity.CENTER, ClipDrawable.HORIZONTAL);
        pb.setProgressDrawable(clipDrawable);
        return pb;
    }

    public void show() {
        if(isShowing) {
            return;
        }

        if(mActivity.getWindow().getDecorView().getWindowToken() != null) { // activity is created and running
            addProgressBarToActivity();
        } else { // activity is not yet finished initialization - wait for it and attach
            mAddRunnable.start();
        }
    }

    public void hide() {
        mAddRunnable.finish();
        if (mProgressBar.getWindowToken() != null) {
            mActivity.getWindowManager().removeViewImmediate(mProgressBar);
        }
        isShowing = false;
    }

    public void onOrientationChanged() {
        if(isShowing) {
            hide();
            show();
        }
    }

    protected void addProgressBarToActivity() {
        // Get the Display Rect of the Decor View
        Rect bounds = new Rect();
        mActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(bounds);
        // Create LayoutParams for adding the View as a panel
        WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        wlp.x = 0;
        wlp.y = bounds.top + getActionBarSize(mActivity);
        wlp.gravity = Gravity.TOP;

        mActivity.getWindowManager().addView(mProgressBar, wlp);
        isShowing = true;
    }

    private class AddHeaderViewRunnable implements Runnable {
        @Override
        public void run() {
            if (getDecorView().getWindowToken() != null) {
            // The Decor View has a Window Token, so we can add the HeaderView!
                addProgressBarToActivity();
            } else {
                // The Decor View doesn't have a Window Token yet, post ourselves again...
                start();
            }
        }
        public void start() {
            getDecorView().post(this);
        }
        public void finish() {
            getDecorView().removeCallbacks(this);
        }
        private View getDecorView() {
            return mActivity.getWindow().getDecorView();
        }
    }

    public void setIndeterminate(boolean indeterminate) {
        mProgressBar.setIndeterminate(indeterminate);
    }

    public void setProgress(int progress) {
        mProgressBar.setProgress(progress);
    }
}
