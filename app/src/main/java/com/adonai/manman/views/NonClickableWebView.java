package com.adonai.manman.views;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebView;

/**
 * Helper class created to workaround issues with WebViews in ListView items
 *
 * @author Adonai
 */
public class NonClickableWebView extends WebView {
    public NonClickableWebView(Context context) {
        super(context);
    }

    public NonClickableWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NonClickableWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public NonClickableWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @SuppressWarnings("deprecation")
    public NonClickableWebView(Context context, AttributeSet attrs, int defStyleAttr, boolean privateBrowsing) {
        super(context, attrs, defStyleAttr, privateBrowsing);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }
}
