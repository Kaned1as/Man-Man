package com.adonai.manman.views;

import android.content.Context;
import android.support.v4.widget.SlidingPaneLayout;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Extension for sliding pane layout class
 * Don't even dare to take control over touch events if slidable view (webview actually) can be scrolled!
 *
 * @author Adonai
 */
public class PassiveSlidingPane extends SlidingPaneLayout {

    private static final int LEFT = -1;

    private View mTrackedView;

    public PassiveSlidingPane(Context context) {
        super(context);
        init();
    }

    public PassiveSlidingPane(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PassiveSlidingPane(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
    }

    public void setTrackedView(View v) {
        mTrackedView = v;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if(isOpen()) // we're open, do whatever we want
            return super.onInterceptTouchEvent(ev);
        else if(mTrackedView != null) {
            if (mTrackedView.canScrollHorizontally(LEFT)) { // if we can scroll left in child, don't even try to open pane!
                return false;
            }
        }
        return super.onInterceptTouchEvent(ev);
    }
}
