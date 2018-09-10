package com.dthfish.eggachedispalyview;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;

import java.util.ArrayList;


/**
 * Description
 * Author DthFish
 * Date  2018/9/10.
 */
public class TopListStrategy extends ListStrategy {

    @Override
    public int getListHeightWithoutPadding(int maxButtonHeight, int maxButtonWidth) {
        int height = 0;
        for (int i = 0; i < mParent.getChildCount(); i++) {
            View child = mParent.getChildAt(i);
            if (child.getVisibility() == View.GONE) continue;

            height += child.getMeasuredHeight();
            // 最后一个不加
            if (i != mParent.getChildCount() - 1) {
                height += mSpacing;
            }
        }
        return height;
    }

    @Override
    public int getListWidthWithoutPadding(int maxButtonHeight, int maxButtonWidth) {
        return maxButtonWidth;
    }

    @Override
    public int getLoopHeightWithoutPadding(int maxButtonHeight, int maxButtonWidth) {
        return maxButtonHeight + mSpacing + mBtnExpand.getMeasuredHeight();
    }

    @Override
    public int getLoopWidthWithoutPadding(int maxButtonHeight, int maxButtonWidth) {
        return maxButtonWidth;
    }

    @Override
    public void onListLayout(boolean changed, int left, int top, int right, int bottom) {

        int centerHorizontalX = (right - left) / 2;
        int nextY = mParent.getPaddingTop();
        // 从后往前遍历
        for (int i = mParent.getChildCount() - 1; i >= 0; i--) {
            View child = mParent.getChildAt(i);
            if (child.getVisibility() == View.GONE || child == mBtnExpand) {
                continue;
            }
            // DisplayMode.LOOP 模式时，onListLayout 会把 menuView 进行横坐标的偏移，这里把坐标回置
            child.setTranslationX(0);
            int l = centerHorizontalX - child.getMeasuredWidth() / 2;
            int t = nextY;
            int r = l + child.getMeasuredWidth();
            int b = t + child.getMeasuredHeight();
            child.layout(l, t, r, b);
            nextY += child.getMeasuredHeight() + mSpacing;
        }
        mBtnExpand.layout(0, 0, 0, 0);

    }

    @Override
    public void onLoopLayout(int maxBtnHeight, int maxBtnWidth, boolean changed, int left, int top, int right, int bottom) {
        int centerHorizontalX = (right - left) / 2;
        int nextY = bottom - top - mParent.getPaddingBottom();

        boolean hasLayoutFirstVisibleMenu = false;
        for (int i = 0; i < mParent.getChildCount(); i++) {
            View child = mParent.getChildAt(i);

            if (child.getVisibility() == View.GONE) {
                continue;
            }
            if (child == mBtnExpand || child == mBtnCollapse) {

                int l = centerHorizontalX - child.getMeasuredWidth() / 2;
                int r = l + child.getMeasuredWidth();
                int b = nextY;//t + child.getMeasuredHeight();
                int t = nextY - child.getMeasuredHeight();
                child.layout(l, t, r, b);
                nextY -= maxBtnHeight + mSpacing;
            } else {
                // DisplayMode.LIST 模式时，动画 会把 menuView 进行纵坐标的偏移，透明度设置，这里把坐标，透明度回置
                child.setAlpha(1);
                child.setTranslationY(0);
                // 除去第一个item 设置到可见位置，其余的设置到控件右边不可见位置
                if (hasLayoutFirstVisibleMenu) {
//                        int l = centerHorizontalX + child.getMeasuredWidth() / 2 + mMaxButtonWidth;
                    int l = centerHorizontalX - child.getMeasuredWidth() / 2;
                    int r = l + child.getMeasuredWidth();
                    int b = bottom - top - mParent.getPaddingBottom();//t + child.getMeasuredHeight();
                    int t = b - child.getMeasuredHeight();
                    child.layout(l, t, r, b);
                    child.setTranslationX(maxBtnWidth);
                } else {
                    int l = centerHorizontalX - child.getMeasuredWidth() / 2;
                    int r = l + child.getMeasuredWidth();
                    int b = bottom - top - mParent.getPaddingBottom();
                    int t = b - child.getMeasuredHeight();
                    child.layout(l, t, r, b);
                    hasLayoutFirstVisibleMenu = true;

                }
            }
        }
    }

    @Override
    public void createListModeAnimation(ArrayList<Animator> expendAnimators, ArrayList<Animator> collapseAnimators, AnimatorSet expandBtnAnimatorSet) {
        // 第一个item舍弃透明度变化
        boolean hasDropFirstItemAlpha = false;
        int beginY = 0;
        for (int i = 0; i < mParent.getChildCount(); i++) {
            View child = mParent.getChildAt(i);
            if (child == mBtnCollapse && mBtnCollapse.getVisibility() != View.GONE) {
                beginY += mBtnCollapse.getMeasuredHeight();
                beginY += mSpacing;
            } else if (child == mBtnExpand) {
                ObjectAnimator animator = ObjectAnimator.ofFloat(child, "translationY", mBtnExpand.getMeasuredHeight(), 0);
                animator.setStartDelay(400);
                animator.setDuration(800);
                ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(child, "alpha", 0, 1);
                alphaAnimator.setStartDelay(400);
                alphaAnimator.setDuration(800);
                expandBtnAnimatorSet.playTogether(animator, alphaAnimator);

            } else if (child.getVisibility() == View.GONE) {
                // do nothing
            } else {
                ObjectAnimator animator = ObjectAnimator.ofFloat(child, "translationY", beginY, 0);
                expendAnimators.add(animator);
                if (hasDropFirstItemAlpha) {
                    ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(child, "alpha", 0, 1);
                    expendAnimators.add(alphaAnimator);
                }

                ObjectAnimator animator2 = ObjectAnimator.ofFloat(child, "translationY", 0, beginY);
                collapseAnimators.add(animator2);
                if (hasDropFirstItemAlpha) {
                    ObjectAnimator alphaAnimator2 = ObjectAnimator.ofFloat(child, "alpha", 1, 0);
                    collapseAnimators.add(alphaAnimator2);
                }
                hasDropFirstItemAlpha = true;
                beginY += child.getMeasuredHeight();
                beginY += mSpacing;
            }
        }

    }
}
