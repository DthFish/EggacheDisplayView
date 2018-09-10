package com.dthfish.eggachedispalyview;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * Description
 * Author DthFish
 * Date  2018/9/10.
 */
public abstract class ListStrategy {

    protected ViewGroup mParent;
    protected View mBtnExpand;
    protected View mBtnCollapse;
    protected int mSpacing;

    void attach(ViewGroup parent, View btnExpand, View btnCollapse, int spacing) {
        mParent = parent;
        mBtnExpand = btnExpand;
        mBtnCollapse = btnCollapse;
        mSpacing = spacing;
    }

    public abstract int getListHeightWithoutPadding(int maxButtonHeight, int maxButtonWidth);

    public abstract int getListWidthWithoutPadding(int maxButtonHeight, int maxButtonWidth);

    public abstract int getLoopHeightWithoutPadding(int maxButtonHeight, int maxButtonWidth);

    public abstract int getLoopWidthWithoutPadding(int maxButtonHeight, int maxButtonWidth);

    public abstract void onListLayout(boolean changed, int left, int top, int right, int bottom);

    public abstract void onLoopLayout(int maxBtnHeight, int maxBtnWidth, boolean changed, int left, int top, int right, int bottom);

    public abstract void createListModeAnimation(ArrayList<Animator> expendAnimators, ArrayList<Animator> collapseAnimators, AnimatorSet expandBtnAnimatorSet);

}
