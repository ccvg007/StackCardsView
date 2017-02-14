package com.beyondsw.lib.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import com.beyondsw.lib.widget.rebound.SimpleSpringListener;
import com.beyondsw.lib.widget.rebound.Spring;
import com.beyondsw.lib.widget.rebound.SpringConfig;
import com.beyondsw.lib.widget.rebound.SpringListener;
import com.beyondsw.lib.widget.rebound.SpringSystem;

/**
 * Created by wensefu on 17-2-12.
 */
public class SwipeTouchHelper implements ISwipeTouchHelper {

    //// TODO: 2017/2/14
    //1,速度大于一定值时卡片滑出消失
//    2，滑动距离超过一定值后卡片消失，消失过程中改变alpha值
//    4，卡片消失后数据刷新
//    5，滑动方向控制
//    6，view缓存
    // 7,多点触控处理

    private static final String TAG = "SwipeTouchHelper";

    private StackCardsView mSwipeView;
    private float mInitDownX;
    private float mInitDownY;
    private float mLastX;
    private float mLastY;
    private int mTouchSlop;
    private boolean mIsBeingDragged;
    private View mTouchChild;
    private float mChildInitX;
    private float mChildInitY;
    private float mAnimStartX;
    private float mAnimStartY;

    private boolean mShouldDisappear;

    private SpringSystem mSpringSystem;
    private Spring mSpring;

    public SwipeTouchHelper(StackCardsView view) {
        mSwipeView = view;
        final ViewConfiguration configuration = ViewConfiguration.get(view.getContext());
        mTouchSlop = configuration.getScaledTouchSlop();
        updateCoverInfo(null);
        mSpringSystem = SpringSystem.create();
    }

    private SpringListener mSpringListener = new SimpleSpringListener() {
        @Override
        public void onSpringUpdate(Spring spring) {
            float value = (float) spring.getCurrentValue();
            mTouchChild.setX(mAnimStartX - (mAnimStartX - mChildInitX) * value);
            mTouchChild.setY(mAnimStartY - (mAnimStartY - mChildInitY) * value);
            onCoverScrolled();
        }
    };

    private void updateCoverInfo(View cover) {
        if (cover == null) {
            if (mSwipeView.getChildCount() > 0) {
                cover = mSwipeView.getChildAt(0);
            }
        }
        mTouchChild = cover;
        if (mTouchChild != null) {
            mChildInitX = mTouchChild.getX();
            mChildInitY = mTouchChild.getY();
            Log.d(TAG, "updateCoverInfo: mChildInitX=" + mChildInitX + ",mChildInitY=" + mChildInitY);
        }
    }

    @Override
    public void onCoverChanged(View cover) {
        updateCoverInfo(cover);
    }

    private void requestParentDisallowInterceptTouchEvent(boolean disallowIntercept) {
        final ViewParent parent = mSwipeView.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private boolean isTouchOnFirstChild(float x, float y) {
        if (mTouchChild == null) {
            return false;
        }
        return x >= mTouchChild.getLeft() && x <= mTouchChild.getRight() && y >= mTouchChild.getTop() && y <= mTouchChild.getBottom();
    }

    private boolean canDrag(float dx, float dy) {
        Log.d(TAG, "canDrag: dx=" + dx + ",dy=" + dy);
        int direction = mSwipeView.getSwipeDirection();
        if (direction == StackCardsView.SWIPE_ALL) {
            return true;
        } else if (direction == 0) {
            return false;
        }
        //水平角度小于60度时，认为是水平滑动
        if (Math.abs(dx) * 1.732f > Math.abs(dy)) {
            if (dx > 0) {
                return (direction & StackCardsView.SWIPE_RIGHT) != 0;
            } else {
                return (direction & StackCardsView.SWIPE_LEFT) != 0;
            }
        } else {
            if (dy > 0) {
                return (direction & StackCardsView.SWIPE_DOWN) != 0;
            } else {
                return (direction & StackCardsView.SWIPE_UP) != 0;
            }
        }
    }

    private void performDrag(float dx, float dy) {
        View cover = mSwipeView.getChildAt(0);
        cover.setX(cover.getX() + dx);
        cover.setY(cover.getY() + dy);
        onCoverScrolled();
        Log.d(TAG, "performDrag: dx=" + dx + "dy=" + dy + "left=" + cover.getLeft());
    }

    private void animateToInitPos() {
        if (mTouchChild != null) {
            if (mSpring != null) {
                mSpring.removeAllListeners();
            }
            mAnimStartX = mTouchChild.getX();
            mAnimStartY = mTouchChild.getY();
            mSpring = mSpringSystem.createSpring();
            mSpring.setSpringConfig(SpringConfig.fromOrigamiTensionAndFriction(40, 5));
            mSpring.addListener(mSpringListener);
            mSpring.setEndValue(1);
        }
    }

    private void animateToDisappear() {
        if (mTouchChild == null) {
            return;
        }
        Rect rect = new Rect();
        mTouchChild.getGlobalVisibleRect(rect);
        float targetX;
        if (rect.left > 0) {
            targetX = mTouchChild.getX() + rect.width();
        } else {
            targetX = mTouchChild.getX() - rect.width();
        }
        ObjectAnimator animator = ObjectAnimator.ofFloat(mTouchChild, "x", targetX).setDuration(200);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mSwipeView.onCardDismissed();
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
            }
        });
        animator.start();
    }

    private void onCoverScrolled() {
        if (mTouchChild == null) {
            return;
        }
        float dx = mTouchChild.getX() - mChildInitX;
        float dy = mTouchChild.getY() - mChildInitY;
        if (dx == 0) {
            mTouchChild.setRotation(0);
        } else {
            float maxRotation = mSwipeView.getMaxRotation();
            float rotation = maxRotation * (2 * dx / mTouchChild.getWidth());
            if (rotation > maxRotation) {
                rotation = maxRotation;
            } else if (rotation < -maxRotation) {
                rotation = -maxRotation;
            }
            mTouchChild.setRotation(rotation);
        }
        double distance = Math.sqrt(dx * dx + dy * dy);
        int dismiss_distance = mSwipeView.getDismissDistance();
        if (distance >= dismiss_distance) {
            mSwipeView.onCoverScrolled(1);
            mShouldDisappear = true;
        } else {
            mSwipeView.onCoverScrolled((float) distance / dismiss_distance);
            mShouldDisappear = false;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mTouchChild == null) {
            return false;
        }
        final int action = ev.getAction() & MotionEvent.ACTION_MASK;
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            Log.d(TAG, "onInterceptTouchEvent: action=" + action + ",reset touch");
            mIsBeingDragged = false;
            return false;
        }
        if (mIsBeingDragged && action != MotionEvent.ACTION_DOWN) {
            return true;
        }
        final float x = ev.getX();
        final float y = ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                Log.d(TAG, "onInterceptTouchEvent: ACTION_DOWN,x=" + x);
                if (!isTouchOnFirstChild(x, y)) {
                    Log.d(TAG, "onInterceptTouchEvent: !isTouchOnFirstChild");
                    return false;
                }
                requestParentDisallowInterceptTouchEvent(true);
                if (mSpring != null && !mSpring.isAtRest()) {
                    mSpring.removeAllListeners();
                }
                mInitDownX = x;
                mInitDownY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                Log.d(TAG, "onInterceptTouchEvent: ACTION_MOVE");
                float dx = x - mInitDownX;
                float dy = y - mInitDownY;
                if (Math.sqrt(dx * dx + dy * dy) > mTouchSlop && canDrag(dx, dy)) {
                    Log.d(TAG, "onInterceptTouchEvent: mIsBeingDragged = true");
                    mIsBeingDragged = true;
                }
                mLastX = x;
                mLastY = y;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                Log.d(TAG, "onInterceptTouchEvent: ACTION_POINTER_DOWN");
                break;
            case MotionEvent.ACTION_POINTER_UP:
                Log.d(TAG, "onInterceptTouchEvent: ACTION_POINTER_UP");
                break;
        }
        return mIsBeingDragged;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction() & MotionEvent.ACTION_MASK;
        float x = ev.getX();
        float y = ev.getY();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                Log.d(TAG, "onTouchEvent: ACTION_DOWN");
                break;
            case MotionEvent.ACTION_MOVE:
                Log.d(TAG, "onTouchEvent: ACTION_MOVE,mIsBeingDragged=" + mIsBeingDragged);
                float dx = x - mLastX;
                float dy = y - mLastY;
                if (mIsBeingDragged) {
                    performDrag(dx, dy);
                } else {
                    Log.e(TAG, "onTouchEvent: ACTION_MOVE,mIsBeingDragged=false");
                }
                mLastX = x;
                mLastY = y;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                Log.d(TAG, "onTouchEvent: ACTION_POINTER_DOWN");
                break;
            case MotionEvent.ACTION_UP:
                Log.d(TAG, "onTouchEvent: ACTION_UP");
                if (mShouldDisappear) {
                    animateToDisappear();
                } else {
                    animateToInitPos();
                }
                mIsBeingDragged = false;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                Log.d(TAG, "onTouchEvent: ACTION_POINTER_UP");
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.d(TAG, "onTouchEvent: ACTION_CANCEL");
                mIsBeingDragged = false;
                break;
        }
        return true;
    }
}
