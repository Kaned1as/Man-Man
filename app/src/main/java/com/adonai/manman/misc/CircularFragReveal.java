package com.adonai.manman.misc;

import android.animation.Animator;
import android.animation.TimeInterpolator;
import android.annotation.TargetApi;
import android.graphics.Color;
import android.os.Build;
import androidx.annotation.NonNull;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.DecelerateInterpolator;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;

/**
 * Created by Tin Megali on 21/02/16.
 * https://github.com/tinmegali
 *
 * based on https://gist.github.com/ferdy182/d9b3525aa65b5b4c468a
 *
 * Add a Reveal/UnReveal circular transition animation effect to a fragment.
 * It's possible to personalize:
 *  - BG color {@linkplain Builder#setRevealColor(int)}
 *  - Animation time {@linkplain Builder#setRevealTime(int)} {@linkplain Builder#setUnrevealTime(int)}
 *  - Interpolator {@linkplain Builder#setRevelInt(TimeInterpolator)} {@linkplain Builder#setUnrevealInt(TimeInterpolator)}
 *  - Choose to use or not a onTouch un reveal {@linkplain Builder#setOnToouchUnreveal(boolean)}
 *
 * Using:
 *  1 - Build and personalize an instance using
 *      {@link Builder}
 *  2 - To begin revealing
 *      {@linkplain #startReveal(int, int, OnCircularReveal)}
 *  3 - To terminate
 *      {@linkplain #startUnreveal(int, int, OnCircularReveal)}
 *  4 - Events will be sent using
 *      {@link OnCircularReveal}
 *
 *  IMPORTANT: CircularFragReveal won't exclude the fragment.
 *      You'll need to call this action after the animation ends
 *      using it's events
 *      {@link OnCircularReveal#onFragCircRevealStart()}
 *      {@link OnCircularReveal#onFragCircRevealEnded()}
 *      {@link OnCircularReveal#onFragCircUnRevealStart()}
 *      {@link OnCircularReveal#onFragCircUnRevealEnded()}
 *
 */
public class CircularFragReveal {

    private static final String TAG = CircularFragReveal.class.getSimpleName();

    private int mRevealDuration;
    private int mUnrevealDuration;
    private TimeInterpolator mRevealInterpolator, mUnrevealInterpolator;
    private Animator mRevealAnimator;
    private Animator mUnrevealAnimator;

    private WeakReference<View> mRootView;
    private int mRevealColor;
    private boolean mUseOnTouchUnreveal;
    private boolean mIsRevealing, mIsUnrevealing;


    private WeakReference<OnCircularReveal> mCallback;

    public interface OnCircularReveal {
        void onFragCircRevealStart();
        void onFragCircRevealEnded();
        void onFragCircUnRevealStart();
        void onFragCircUnRevealEnded();

    }

    /**
     * Constructor
     * Uses a Builder
     */
    private CircularFragReveal(Builder builder) {
        mRootView = new WeakReference<>(builder.getRootView());
        mRevealDuration = builder.getREVEAL_DURATION();
        mUnrevealDuration = builder.getUNREVEAL_DURATION();
        mRevealInterpolator = builder.getRevealInterpolator();
        mUnrevealInterpolator = builder.getUnrevealInterpolator();
        mRevealColor = builder.getRevealBgColor();
        mUseOnTouchUnreveal = builder.isTouchUnreveal();
    }

    /**
     * Start Circular unReveal animation
     *
     * @param centerX       Animation Center X
     * @param centerY       Animation Center Y
     * @param listener Unreveal listener
     */
    public void startReveal(int centerX, int centerY, @NonNull OnCircularReveal listener) {
        Log.d(TAG, "startReveal(centerX["+centerX+"], centerY["+centerY+"], callback["+listener+"])");
        mCallback = new WeakReference<>(listener);
        startRevealAnimation(centerX, centerY);
    }

    /**
     * Cancel Reveal animator
     */
    public void cancelReveal() {
        Log.d(TAG, "cencelReveal");
        mRevealAnimator.cancel();
    }

    /**
     * Current animation state
     */
    public boolean isRevealing() { return mIsRevealing; }
    public boolean isUnrevealing() { return mIsUnrevealing; }


    /**
     * Start Circular unReveal animation
     *
     * @param centerX       Animation Center X
     * @param centerY       Animation Center Y
     * @param listener Unreveal listener
     */
    public void startUnreveal(int centerX, int centerY, OnCircularReveal listener) {
        Log.d(TAG, "startUnreveal(cx[" + centerX + "], cy[" + centerY + "])");
        mCallback = new WeakReference<>(listener);
        try {
            mUnrevealAnimator = prepareUnrevealAnimator(centerX, centerY);
            mUnrevealAnimator.start();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Cancel unReveal animator
     */
    public void cancelUnreveal() {
        Log.d(TAG, "cancelUnreveal()");
        mUnrevealAnimator.cancel();
    }


    /**
     * Circular Reveal Builder
     */
    public static class Builder {

        private final String TAG = Builder.class.getSimpleName();

        private int REVEAL_DURATION = 200;
        private int UNREVEAL_DURATION = 200;
        private TimeInterpolator revealInterpolator;
        private TimeInterpolator unrevealInterpolator;
        private View rootView;
        private int revealBgColor;
        private boolean touchUnreveal;

        public Builder(View rootView) {
            this.rootView = rootView;
            this.revealInterpolator = new DecelerateInterpolator(2f);
            this.unrevealInterpolator = new AnticipateInterpolator(2f);
            this.revealBgColor = Color.parseColor("#e9fafafa");
        }

        public Builder setRevealTime(int duration) {
            Log.d(TAG, "setRevealTime(duration[" + duration + "])");
            this.REVEAL_DURATION = duration;
            return this;
        }

        public Builder setUnrevealTime(int duration) {
            Log.d(TAG, "setUnrevealTime(duration[" + duration + "])");
            this.UNREVEAL_DURATION = duration;
            return this;
        }

        public Builder setRevelInt(TimeInterpolator interpolator) {
            Log.d(TAG, "setRevelInt(interpolator[" + interpolator + "])");
            this.revealInterpolator = interpolator;
            return this;
        }

        public Builder setUnrevealInt(TimeInterpolator interpolator) {
            Log.d(TAG, "setUnrevealInt(interpolator[" + interpolator + "])");
            this.unrevealInterpolator = interpolator;
            return this;
        }

        public Builder setRevealColor(int color) {
            Log.d(TAG, "setRevealColor(color[" + color + "])");
            this.revealBgColor = color;
            return this;
        }

        public Builder setOnToouchUnreveal(boolean onTouchOn) {
            Log.d(TAG, "setOnToouchUnreveal(onTouch[" + onTouchOn + "])");
            this.touchUnreveal = onTouchOn;
            return this;
        }


        /**
         * Builder method
         */
        public CircularFragReveal build() {
            Log.d(TAG, "build()");
            return new CircularFragReveal(this);
        }

        public View getRootView() {
            return rootView;
        }

        public int getREVEAL_DURATION() {
            return REVEAL_DURATION;
        }

        public TimeInterpolator getRevealInterpolator() {
            return revealInterpolator;
        }

        public int getUNREVEAL_DURATION() {
            return UNREVEAL_DURATION;
        }

        public TimeInterpolator getUnrevealInterpolator() {
            return unrevealInterpolator;
        }

        public int getRevealBgColor() {
            return revealBgColor;
        }

        public boolean isTouchUnreveal() {
            return touchUnreveal;
        }
    }


    /**
     * Configures and start Reveal animation
     */
    private void startRevealAnimation(final int cx, final int cy) {
        Log.d(TAG, "startRevealAnimation()");
        if (Build.VERSION.SDK_INT >= 21) {
            // define BG color
            getView().setBackgroundColor(mRevealColor);

            // To run the animation as soon as the view is layout in the view hierarchy we add this
            // listener and remove it
            // as soon as it runs to prevent multiple animations if the view changes bounds
            getView().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    Log.d(TAG, "startRevealAnimation() | onLayoutChange");
                    v.removeOnLayoutChangeListener(this);

                    int radius = (int) Math.hypot(right, bottom);

                    int duration = mRevealDuration;
                    mRevealAnimator = ViewAnimationUtils.createCircularReveal(v, cx, cy, 0, radius);
                    mRevealAnimator.setInterpolator(mRevealInterpolator);
                    mRevealAnimator.setDuration(duration);
                    mRevealAnimator.addListener(mRevealAnimListener);
                    mRevealAnimator.start();
                }
            });

        } else {
            Log.w(TAG, "Cannot run CircularReveal on this SDK version[" + Build.VERSION.SDK_INT + "]. " +
                    "CircularReveal run on version 21 and greater.");
        }
    }

    private View getView() {
        Log.d(TAG, "getView()");
        return mRootView.get();
    }


    /**
     * Prepare UnReveal animation
     */
    private Animator prepareUnrevealAnimator(float cx, float cy) throws IllegalAccessException {
        Log.d(TAG, "prepareUnrevealAnimator(cx[" + cx + "], cy[" + cy + "]");
        if (Build.VERSION.SDK_INT >= 21) {
            int radius = getEnclosingCircleRadius(getView(), (int) cx, (int) cy);
            @SuppressWarnings("unchecked")
            Animator animator = ViewAnimationUtils.createCircularReveal(getView(), (int) cx, (int) cy, radius, 0);
            animator.setInterpolator(mUnrevealInterpolator);
            animator.setDuration(mUnrevealDuration);
            animator.addListener(mUnRevealAnimListener);
            return animator;
        } else
            throw new IllegalAccessException("Cannot prepare UnReveal with version[" + Build.VERSION.SDK_INT + "]");
    }

    private int getEnclosingCircleRadius(View v, int cx, int cy) {
        Log.d(TAG, "getEnclosingCircleRadius(view[" + v.getId() + "], cx[" + cx + "], cy[" + cy + "])");
        int realCenterX = cx + v.getLeft();
        int realCenterY = cy + v.getTop();
        int distTopLeft = (int) Math.hypot(realCenterX - v.getLeft(), realCenterY - v.getTop());
        int distTopRight = (int) Math.hypot(v.getRight() - realCenterX, realCenterY - v.getTop());
        int distBottomLeft = (int) Math.hypot(realCenterX - v.getLeft(), v.getBottom() - realCenterY);
        int distBottomRIght = (int) Math.hypot(v.getRight() - v.getLeft(), v.getBottom() - realCenterY);

        Integer[] distances = new Integer[]{distTopLeft, distTopRight, distBottomLeft, distBottomRIght};
        return Collections.max(Arrays.asList(distances));
    }


    /**
     * OnTouch UnReveal Listener
     */
    private View.OnTouchListener mRootTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.d(TAG, "onTouch(view[" + v.getId() + "], event[" + event.getAction() + "])");
            getView().setOnTouchListener(null);
            startUnreveal((int)event.getX(), (int)event.getY(), mCallback.get());
            return true;
        }
    };
    /**
     * Turns onTouch unReveal listener ON
     */
    public void onTouchUnRevealOn() {
        Log.d(TAG, "onTouchUnRevealON()");
        getView().setOnTouchListener(mRootTouchListener);
    }
    /**
     * Turns ontouch unReveal listener OFF
     */
    public void onTouchUnRevealOff(){
        Log.d(TAG, "onTouchUnRevealOFF()");
        getView().setOnTouchListener(null);
    }


    /**
     * UnReveal Animator Listener
     */
    private Animator.AnimatorListener mUnRevealAnimListener = new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
            Log.d(TAG, "onFragCircUnRevealStart");
            mCallback.get().onFragCircUnRevealStart();
            onTouchUnRevealOff();
            mIsUnrevealing = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            Log.d(TAG, "onFragCircUnRevealEnded");
            getView().setVisibility(View.INVISIBLE);
            mCallback.get().onFragCircUnRevealEnded();
            mIsUnrevealing = false;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            Log.d(TAG, "onAddFragUnReveal cancel");
            mIsUnrevealing = false;
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
            Log.d(TAG, "onAddFragUnReveal repeat");
        }
    };

    /**
     * Reveal Animator Listener
     */
    private Animator.AnimatorListener mRevealAnimListener = new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
            Log.d(TAG, "onAnimationStart");
            mCallback.get().onFragCircRevealStart();
            mIsRevealing = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            Log.d(TAG, "onAnimationEnd");
            mCallback.get().onFragCircRevealEnded();
            mIsRevealing = false;
            if (mUseOnTouchUnreveal) {
                Log.d(TAG, "turning touchListener ON");
                onTouchUnRevealOn();
            }
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            Log.d(TAG, "onAnimationCancel");
            onTouchUnRevealOff();
            mIsRevealing = false;
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
            Log.d(TAG, "onAnimationRepeat");
        }
    };

}