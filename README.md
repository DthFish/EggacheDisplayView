# MenuDisplayView -- 一个带轮播功能的菜单

***本篇文章已授权微信公众号 guolin_blog （郭霖）独家发布**

好久没有写过文章了，借着前几天 UI 提出的一个“简单”的动画需求,写写自己实现的 **[EggacheDisplayView](https://github.com/DthFish/EggacheDisplayView)**。哦！你没看错项目的名称就是这个，因为看到 UI 动效的时候就是这样的心情，至于标题咱只是想告诉大家这是一篇正经的文章。

原本想着改改动画就算了，结果写完之后发现把自定义 ViewGroup 该用的知识差不多都涉及到了，那么就写篇文章来一起复习一下自定义一个 ViewGroup 的流程。接下来看看我们要实现的效果，咱们就开始进入正题了。

![MenuDisplayView.gif](https://upload-images.jianshu.io/upload_images/5463583-4f21faf7c75a6115.gif?imageMogr2/auto-orient/strip)

### 简单的分析

1. 需求里的 menu item 其实没有像 demo 上显示的那样——都是同样类型的，事实上有直接添加在 xml 布局里面的，也有从接口请求下来之后再添加进去的。所以需要定义一个 ViewGroup，不管子 View 是啥样子的往里面添加就是。
2. ViewGroup 需要两种不同的布局方式：一种是展开时候的，看上去和 LinearLayout 一致；另一种是收起时候的，看上去像我们常用的 ViewPager 轮播一样。根据两种不同的模式，需要实现两种不同的测量和布局方式。
3. 收起和展开按钮需要在 EggacheDisplayView 构造的时候动态添加进去。

### 简单的准备

在讲具体的 onMeasure 和 onLayout 方法之前，需要简单的看下构造方法。

```java
    public EggacheDisplayView(@NonNull Context context, @Nullable AttributeSet attrs, 
                              int    defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray typedArray = context.obtainStyledAttributes(
            attrs, R.styleable.EggacheDisplayView, 0, 0);
        mBtnSpacing = typedArray.getDimensionPixelSize(
            R.styleable.EggacheDisplayView_btn_spacing, dpToPx(context, 10));
        int layoutCollapse = typedArray.getResourceId(
            R.styleable.EggacheDisplayView_collapse_layout, 
            R.layout.layout_collapse_button);
        int layoutExpand = typedArray.getResourceId(
            R.styleable.EggacheDisplayView_expand_layout, R.layout.layout_expand_button);
        mClickLoopToExpand = typedArray.getBoolean(
            R.styleable.EggacheDisplayView_click_loop_to_expand, false);
        typedArray.recycle();
        createCollapseAndExpandButton(context, layoutCollapse, layoutExpand);
    }

```
这里只有简单的几个自定义属性，mBtnSpacing 是每个子 view 之间的间隔，后面测量和布局的时候会用到，至于两个 layout 则分别是我们 createCollapseAndExpandButton 方法要添加进去的两个按钮。

另外定义了一个枚举类，表示是在展开状态还是在轮播状态。

``` java
public enum DisplayMode {
        LIST,
        LOOP
}
```

### onMeasure 方法

``` java
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = 0;
        int height = 0;
        mMaxButtonWidth = 0;
        mMaxButtonHeight = 0;
		// 1.
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            mMaxButtonWidth = Math.max(mMaxButtonWidth, child.getMeasuredWidth());
            mMaxButtonHeight = Math.max(mMaxButtonHeight, child.getMeasuredHeight());
        }
        // 2.
        if (mDisplayMode == DisplayMode.LIST) {
            // DisplayMode.LIST 相当于 LinearLayout
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;

                height += child.getMeasuredHeight();
                // 最后一个
                if (i != getChildCount() - 1) {
                    height += mBtnSpacing;
                }
            }

        } else if (mDisplayMode == DisplayMode.LOOP) {
            // DisplayMode.LOOP 高度只包括 最高的子 view 的高度 + mBtnExpand 的高度
            height += mMaxButtonHeight + mBtnSpacing + mBtnExpand.getMeasuredHeight();
        }
        width = mMaxButtonWidth + getPaddingLeft() + getPaddingRight();
        height += getPaddingTop() + getPaddingBottom();
		// 3.
        if (getLayoutParams().width == LayoutParams.MATCH_PARENT) {
            width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        }
        if (getLayoutParams().height == LayoutParams.MATCH_PARENT) {
            height = getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        }
        setMeasuredDimension(width, height);
    }

```

从之前的分析来看，其实不适合继承 LinearLayout 等常用的 ViewGroup，因为不同的模式有不同的要求：

* List 模式

  宽：最宽的子 View 的宽 + 左右 padding

  高：所有非 Gone 的子 View 的高之和 + 上下 padding + （子类个数 - 1）* mBtnSpacing

* Loop 模式

  宽：最宽的子 View 的宽 + 左右 padding

  高：最高的子 View 的高 + 展开按钮的高 + 上下 padding +  mBtnSpacing

所以上面的代码就可以简单的分为三步：

1. 遍历所有子 View，用 measureChildWithMargins 方法测量，然后保存子 View 中最大的宽度和高度。

2. 根据不同的模式计算出不同的高度，宽度是一致的不用进行特别的区分。

3. 对 LayoutParams.MATCH_PARENT 的处理，设置测量后的宽高。

这样分析之后是不是看起来也没那么麻烦？其实咱也是看开源项目学习过来的。下边马上开始讲 onLayout 方法。

### onLayout 方法

##### 一、List 模式 layout

``` java
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
 
        if (mDisplayMode == DisplayMode.LIST) {
            int centerHorizontalX = (right - left) / 2;
            int nextY = top + getPaddingTop();
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE || child == mBtnExpand) {
                    continue;
                }
                // 1.DisplayMode.LOOP 模式时，onLayout 会把 menuView 进行横坐标的偏移，
                // 这里把坐标回置
                child.setTranslationX(0);
                int l = centerHorizontalX - child.getMeasuredWidth() / 2;
                int t = nextY;
                int r = l + child.getMeasuredWidth();
                int b = t + child.getMeasuredHeight();
                child.layout(l, t, r, b);
                nextY += child.getMeasuredHeight() + mBtnSpacing;
            }
            mBtnExpand.layout(0, 0, 0, 0);
        } else if (mDisplayMode == DisplayMode.LOOP) {
            // 先省略……
        }
    }

```

这里先看简单的 List 模式，主要是依次把 子 View 垂直并水平居中排列，对于 List 模式中不需要展示的展开按钮（mBtnExpand）直接上下左右都赋值为0。

至于中间标【1】的地方，暂时先忽略，后面讲动画的时候会涉及到。

##### 二、Loop 模式 layout

``` java
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

        if (mDisplayMode == DisplayMode.LIST) {
            // 省略……
        } else if (mDisplayMode == DisplayMode.LOOP) {
            int centerHorizontalX = (right - left) / 2;
            int nextY = top + getPaddingTop();
            boolean hasLayoutFirstVisibleMenu = false;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                // 1.
                if (child == mBtnExpand || child == mBtnCollapse) {
                    int l = centerHorizontalX - child.getMeasuredWidth() / 2;
                    int t = nextY;
                    int r = l + child.getMeasuredWidth();
                    int b = t + child.getMeasuredHeight();
                    child.layout(l, t, r, b);
                    nextY += mMaxButtonHeight + mBtnSpacing;
                } else {
                    // 2.DisplayMode.LIST 模式时，动画 会把 menuView 进行纵坐标的偏移，
                    // 透明度设置，这里把坐标，透明度回置
                    child.setAlpha(1);
                    child.setTranslationY(0);
                    // 3.除去第一个item 设置到可见位置，其余的设置到控件右边不可见位置
                    if (hasLayoutFirstVisibleMenu) {
                        int l = centerHorizontalX - child.getMeasuredWidth() / 2;
                        int t = top + getPaddingTop();
                        int r = l + child.getMeasuredWidth();
                        int b = t + child.getMeasuredHeight();
                        child.layout(l, t, r, b);
                        child.setTranslationX(mMaxButtonWidth);
                    } else {
                        int l = centerHorizontalX - child.getMeasuredWidth() / 2;
                        int t = top + getPaddingTop();
                        int r = l + child.getMeasuredWidth();
                        int b = t + child.getMeasuredHeight();
                        child.layout(l, t, r, b);
                        hasLayoutFirstVisibleMenu = true;
                    }
                }
            }
        }
    }

```

* 标【1】的地方，仅仅是把展开和收起按钮依次做垂直并水平居中排列。
* 标【3】的地方，其余的除去第一个item 设置到可见位置（和顶部对其，水平居中），其余的设置到控件右边不可见位置，这里没有通过 layout 方法把它们到控件看不见的地方，而是**通过 child.setTranslationX(mMaxButtonWidth) 方法移动到右侧不可见的位置**。

### 动态设置动画

现在自定义 ViewGroup 最重要的两个方法已经实现了，现在就是设置动画了，那么我们在什么时机设置动画呢？答案是 onSizeChanged，当它调用的时候往往就是存在子 View Gone、Visiable、remove 或者 add 的时候，这时候也恰好是我们需要重新计算每个子 View 的动画距离的时候。

``` java
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        createViewsAnimation();
    }

    private void createViewsAnimation() {
        if (mDisplayMode == DisplayMode.LIST) {
            createListModeAnimation();
        } else if (mDisplayMode == DisplayMode.LOOP) {
            createLoopModeAnimation();
        }
    }
```

当然这里的动画也要根据不同的模式分别设置。

##### 一、List 模式动画

~~~ java
    private void createListModeAnimation() {
        // 1.清空之前的动画
        mExpendAnimators = new ArrayList<>();
        mCollapseAnimators = new ArrayList<>();
        // 第一个item舍弃透明度变化
        boolean hasDropFirstItemAlpha = false;
        int beginY = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == mBtnCollapse && mBtnCollapse.getVisibility() != GONE) {
                // 2.
                beginY -= mBtnCollapse.getMeasuredHeight();
                beginY -= mBtnSpacing;
            } else if (child == mBtnExpand) {
                // 7.
                ObjectAnimator animator = ObjectAnimator.ofFloat(
                    child, "translationY", -mBtnExpand.getMeasuredHeight(), 0);
                animator.setStartDelay(400);
                animator.setDuration(800);
                ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(
                    child, "alpha", 0, 1);
                alphaAnimator.setStartDelay(400);
                alphaAnimator.setDuration(800);
                mExpandBtnAnimatorSet.playTogether(animator, alphaAnimator);
            } else if (child.getVisibility() == GONE) {
                // do nothing
            } else {
                // 3.
                ObjectAnimator animator = ObjectAnimator.ofFloat(
                    child, "translationY", beginY, 0);
                mExpendAnimators.add(animator);
                // 4.
                if (hasDropFirstItemAlpha) {
                    ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(
                        child, "alpha", 0, 1);
                    mExpendAnimators.add(alphaAnimator);
                }
                ObjectAnimator animator2 = ObjectAnimator.ofFloat(
                    child, "translationY", 0, beginY);
                mCollapseAnimators.add(animator2);
                if (hasDropFirstItemAlpha) {
                    ObjectAnimator alphaAnimator2 = ObjectAnimator.ofFloat(
                        child, "alpha", 1, 0);
                    mCollapseAnimators.add(alphaAnimator2);
                }
                hasDropFirstItemAlpha = true;
                beginY -= child.getMeasuredHeight();
                beginY -= mBtnSpacing;
            }
        }
        if (mExpandListener == null) {
            mExpandListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    // 5.展开,这里会引发重绘
                    mDisplayMode = DisplayMode.LIST;
                    mBtnExpand.setVisibility(GONE);
                    mBtnCollapse.setVisibility(VISIBLE);
                }
            };
            mExpandAnimatorSet.addListener(mExpandListener);
        }
        if (mCollapseListener == null) {
            mCollapseListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    // 6.收起,这里会引发重绘
                    mBtnExpand.setAlpha(0);
                    mDisplayMode = DisplayMode.LOOP;
                    mBtnExpand.setVisibility(VISIBLE);
                    mBtnCollapse.setVisibility(INVISIBLE);
                    mExpandBtnAnimatorSet.start();
                }

                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                }
            };
            mCollapseAnimatorSet.addListener(mCollapseListener);
        }
    }

~~~

这里代码看起来很长：

* 标记【2】虽然收起按钮 mBtnCollapse 自身不需要动画，但是其他子 View 的移动距离需要算上它的高度和 mBtnSpacing。
* 标记【3】添加垂直向下移动的展开动画，顺便添加一个相反的收起动画，并把自身的高度累加到下一个子 View 需要移动的动画距离。
* 标记【4】观察 Demo（UI 动效）可以知道，第一个子 Menu View ，不需要透明度动画，所以用布尔值hasDropFirstItemAlpha 来控制。
* 标记【5】当开始展开的时候事实上我们需要修改 ViewGroup 的测量和布局模式，所以我们先把模式设置为 DisplayMode.LIST，再调用 mBtnExpand.setVisibility(GONE) 这里引发了重绘，再改变布局方式之后再呈现了展开的动画。
* 标记【6】和上一点一样，模式设置为 DisplayMode.LOOP，引发重绘，顺便调用标记【7】里面设置的显示展开按钮（mBtnExpand）的动画。 

##### 二、Loop 模式动画

~~~ java
    private void createLoopModeAnimation() {
        start();
    }
    public void start() {
        if (!isStarted && mMenuViews.size() > 1) {
            isStarted = true;
            // 1.
            mCurrentView = mMenuViews.get(0);
            mNextView = mMenuViews.get(1);
            // 2.
            postDelayed(mRunnable, mLoopGap);// mRunnable 为 AnimRunnable
        }
    }
    private class AnimRunnable implements Runnable {
        @Override
        public void run() {
            performSwitch();
        }
    }
    private void performSwitch() {
        if (!isStarted) {
            return;
        }
        // 3.
        ObjectAnimator animator1 = ObjectAnimator.ofFloat(
            mCurrentView, "translationX", -mMaxButtonWidth);
        ObjectAnimator animator2 = ObjectAnimator.ofFloat(
            mNextView, "translationX", 0);
        mLoopAnimationSet = new AnimatorSet();
        mLoopAnimationSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                // 5.
                if (mCurrentView != null) {
                    mCurrentView.setTranslationX(0);
                }
                if (mNextView != null) {
                    mNextView.setTranslationX(0);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // 4.
                mCurrentView.setTranslationX(mMaxButtonWidth);
                mCurrentView = mNextView;
                int index = mMenuViews.indexOf(mCurrentView);
                mNextView = index == mMenuViews.size() - 1 ?
                    mMenuViews.get(0) : mMenuViews.get(index + 1);
                postDelayed(mRunnable, mLoopGap);
            }

        });
        mLoopAnimationSet.setDuration(mLoopDuration);
        mLoopAnimationSet.playTogether(animator1, animator2);
        mLoopAnimationSet.start();
    }

~~~

* 标记【1】mMenuViews 里面包括可除去展开和收起按钮之外的所有 menu view。获取其中前两个 view 赋值给 mCurrentView 和 mNextView，以便后面动画使用

* 标记【2】动画其实就是发送一个延迟消息，去触发动画效果，延迟消息的时间就是 mLoopGap。

* 标记【3】所要做的动画就是把 mCurrentView 和 mNextView 分别向左移动，整个空间的宽度的距离（mMaxButtonWidth）这个距离就是我们在 onMeasure 的时候得到的。

* 这里为什么直接向左移动就可以做到类似 ViewPager 的动画呢？回顾我们在 **Loop 模式 layout 标记【3】**地方对子 View 事先做的 **child.setTranslationX(mMaxButtonWidth) **操作就是为了这里的动画作准备。

* 标记【4】当 mCurrentView 移出我们的视野之后马上又把它移动到 ViewGroup 右侧，等待下一次从右向左移入；把 mNextView 引用的对象赋值给 mCurrentView 表示当前可见；查找到下一个移入的 View 准备播放从右向左移入动画；发送延迟消息准备下一次动画。

* 标记【5】对取消的动画的 View 位置还原。

##### 三、动画小结

看似复杂的动画其实都是多个简单的动画拼凑起来的，值得注意的是在 **onLayout** 方法中，做了或多或少的预处理以及动画之后子 View 相关属性的复原的工作（见之前省略的标记），来保证切换 DisplayMode 之后的动画需求。

其实咱对 Loop 模式动画的支持还是有点小瑕疵，因为缺少对 Gone 的子 View 的处理，想象一下，应该会出现展示空白的情况吧……嗯...不过少侠既然你不想展示那还是 remove 掉吧！

### onInterceptTouchEvent 方法

在最开始的构造方法中有个变量 mClickLoopToExpand，考虑到展示按钮可能太小，在 Loop 模式下不方便点击到，所以还是做一些拦截处理——如果需要的话，点击整个 ViewGroup 先进行展开。

~~~ java
	@Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return (mClickLoopToExpand && mDisplayMode == DisplayMode.LOOP) ||
            super.onInterceptTouchEvent(ev);
    }
    private void createCollapseAndExpandButton(Context context, 
                                               int layoutCollapse, int layoutExpand) {
        // 省略其余代码...
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mClickLoopToExpand && mDisplayMode == DisplayMode.LOOP) {
                    expand();
                }
            }
        });
    }
~~~

处理还是很简单，看代码就知道了。

### 动态添加子 View 与 布局添加子 View

**注意：**可以在布局中直接添加你所需要的 Menu Item，但是如果部分 item 需要动态添加/更新的话还是建议通过下面这个方法，不要直接调用 addView。因为处理动画的时候是通过 mMenuViews 这个 List 集合去获取的。

~~~ java
    /**
     * @param views          menu view
     * @param keepChildCount 保留在 xml 里面的添加的子view 数量，因为要除去
     *						 mBtnCollapse/mBtnExpand，所以第三个开始计算
     */
    public void setMenuViews(List<View> views, int keepChildCount) {
        if (views == null || views.isEmpty()) {
            return;
        }
        stop();// 不管怎样暂停 loop 动画
        if (getChildCount() - 2 >= keepChildCount) {
            List<View> tempViews = new ArrayList<>();
            for (int i = 0; i < keepChildCount; i++) {
                tempViews.add(mMenuViews.get(i));
            }
            mMenuViews.clear();
            mMenuViews.addAll(tempViews);
            mMenuViews.addAll(views);
            int start = 2 + keepChildCount;
            removeViews(start, getChildCount() - start);
            for (View view : views) {
                addView(view);
            }
        } else {
            setMenuViews(views);
        }
    }
~~~

### 最后

在应用到项目当中的时候出现了一个小插曲：包裹 EggacheDiaplayView 的 ViewGroup 被使用值动画不断改变 marginTop 来使整个布局下移的过程中，影响到了 Loop 模式的属性动画，不过把调整 marginTop 改成 调整 translationY 之后这个现象就被解决了。原因……暂时母鸡呀。

其实最初的想法是做一个支持从上/下/左/右展开，从右到左/从下到上轮播的控件的，但是还是容我偷下懒。

再说一句这个控件的名称是 **EggacheDisplayView**！

另外如果大家有发现问题或者意见还望提醒我改正，谢谢！

如果有喜欢这篇文章的同学，请务必给我一个赞呀！这是对广大写博客的同学的最大的肯定！
### 更新

##### 2018年 6月 13日

最近在使用控件的过程中出现了如果用 LinearLayout 包裹 EggacheDispalyView，并在 EggacheDispalyView 前面添加一个 View，就会出现 EggacheDisplayView 里面的 itemView 布局错误的现象。原因是最初我 layout 的时候多加了 top 值，去掉就好了，可以看下面的标记错误的地方。

~~~ java
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // 上往下展开
        if (mDisplayMode == DisplayMode.LIST) {
            int centerHorizontalX = (right - left) / 2;
            int nextY = top + getPaddingTop();// 【这里错误】
            // 省略代码...
        } else if (mDisplayMode == DisplayMode.LOOP) {
            int centerHorizontalX = (right - left) / 2;
            int nextY = top + getPaddingTop();// 【这里错误】
			// 省略代码...
            boolean hasLayoutFirstVisibleMenu = false;
            for (int i = 0; i < getChildCount(); i++) {
                // 省略代码...
                if (child == mBtnCollapse || child == mBtnExpand) {
                 	// 省略代码...   
                } else {               
                    child.setAlpha(1);
                    child.setTranslationY(0);
                    if (hasLayoutFirstVisibleMenu) {
                        int l = centerHorizontalX - child.getMeasuredWidth() / 2;
                        int t = top + getPaddingTop();// 【这里错误】
                        int r = l + child.getMeasuredWidth();
                        int b = t + child.getMeasuredHeight();
                        child.layout(l, t, r, b);
                        child.setTranslationX(mMaxButtonWidth);
                    } else {
                        int l = centerHorizontalX - child.getMeasuredWidth() / 2;
                        int t = top + getPaddingTop();// 【这里错误】
                        int r = l + child.getMeasuredWidth();
                        int b = t + child.getMeasuredHeight();
                        child.layout(l, t, r, b);
                        hasLayoutFirstVisibleMenu = true;

                    }
                }
            }
        }
    }

~~~

**[EggacheDisplayView 源码](https://github.com/DthFish/EggacheDisplayView)**

**[原文地址](https://www.jianshu.com/p/7ef4187d1efd)**













