package br.com.wdc.shopping.nativeui.android.toolkit

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * DSL builder for constructing Android View hierarchies programmatically.
 * Mirrors UIKitDom pattern: addToParent THEN configure/builder.
 */
class AndroidDom private constructor(
    private val context: Context,
    private val parent: ViewGroup
) {
    companion object {
        fun build(context: Context, builder: AndroidDom.() -> Unit): View {
            val root = FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            AndroidDom(context, root).builder()
            return root
        }

        fun render(context: Context, root: ViewGroup, builder: AndroidDom.() -> Unit) {
            AndroidDom(context, root).builder()
        }
    }

    // MARK: - Containers

    fun vStack(
        spacing: Int = 0,
        configure: (LinearLayout.() -> Unit)? = null,
        builder: AndroidDom.() -> Unit
    ): LinearLayout {
        val ll = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (spacing > 0) {
                showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
                dividerDrawable = SpacerDrawable(spacing)
            }
        }
        addToParent(ll)
        configure?.invoke(ll)
        AndroidDom(context, ll).builder()
        return ll
    }

    fun hStack(
        spacing: Int = 0,
        configure: (LinearLayout.() -> Unit)? = null,
        builder: AndroidDom.() -> Unit
    ): LinearLayout {
        val ll = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            if (spacing > 0) {
                showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
                dividerDrawable = SpacerDrawable(spacing)
            }
        }
        addToParent(ll)
        configure?.invoke(ll)
        AndroidDom(context, ll).builder()
        return ll
    }

    fun frame(
        configure: (FrameLayout.() -> Unit)? = null,
        builder: (AndroidDom.() -> Unit)? = null
    ): FrameLayout {
        val fl = FrameLayout(context)
        addToParent(fl)
        configure?.invoke(fl)
        if (builder != null) AndroidDom(context, fl).builder()
        return fl
    }

    fun scrollView(
        configure: (ScrollView.() -> Unit)? = null,
        builder: AndroidDom.() -> Unit
    ): ScrollView {
        val sv = ScrollView(context).apply {
            isFillViewport = true
        }
        addToParent(sv)
        configure?.invoke(sv)
        AndroidDom(context, sv).builder()
        return sv
    }

    fun flowLayout(
        minChildWidth: Int = 180,
        horizontalSpacing: Int = 0,
        verticalSpacing: Int = 0,
        configure: (FlowLayout.() -> Unit)? = null,
        builder: AndroidDom.() -> Unit
    ): FlowLayout {
        val fl = FlowLayout(context).apply {
            this.minChildWidth = minChildWidth
            this.horizontalSpacing = horizontalSpacing
            this.verticalSpacing = verticalSpacing
        }
        addToParent(fl)
        configure?.invoke(fl)
        AndroidDom(context, fl).builder()
        return fl
    }

    // MARK: - Leaf Components

    fun textView(configure: TextView.() -> Unit): TextView {
        val tv = TextView(context)
        addToParent(tv)
        tv.configure()
        return tv
    }

    fun editText(hint: String = "", configure: EditText.() -> Unit): EditText {
        val et = EditText(context).apply {
            this.hint = hint
        }
        addToParent(et)
        et.configure()
        return et
    }

    fun button(text: String = "", configure: Button.() -> Unit): Button {
        val btn = Button(context).apply {
            if (text.isNotEmpty()) this.text = text
            isAllCaps = false
            stateListAnimator = null
        }
        addToParent(btn)
        btn.configure()
        return btn
    }

    fun imageView(configure: ImageView.() -> Unit): ImageView {
        val iv = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addToParent(iv)
        iv.configure()
        return iv
    }

    fun progressBar(configure: ProgressBar.() -> Unit = {}): ProgressBar {
        val pb = ProgressBar(context)
        addToParent(pb, width = ViewGroup.LayoutParams.WRAP_CONTENT, height = ViewGroup.LayoutParams.WRAP_CONTENT)
        pb.configure()
        return pb
    }

    // MARK: - Spacer

    fun spacer(width: Int = 0, height: Int = 0): View {
        val sp = View(context)
        addToParent(sp, width = if (width > 0) width else 0, height = if (height > 0) height else 0)
        return sp
    }

    fun flexSpacer(): View {
        val sp = View(context)
        addToParent(sp)
        (sp.layoutParams as? LinearLayout.LayoutParams)?.apply {
            weight = 1f
            width = 0
        }
        sp.layoutParams = sp.layoutParams // force refresh
        return sp
    }

    // MARK: - Utility

    fun add(view: View) {
        addToParent(view)
    }

    fun parent(): ViewGroup = parent

    fun context(): Context = context

    // MARK: - Private

    private fun addToParent(
        view: View,
        width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    ) {
        val lp = when (parent) {
            is LinearLayout -> LinearLayout.LayoutParams(width, height)
            is FrameLayout -> FrameLayout.LayoutParams(width, height)
            else -> ViewGroup.MarginLayoutParams(width, height)
        }
        parent.addView(view, lp)
    }

    /**
     * Invisible drawable used as spacer between LinearLayout children.
     */
    private class SpacerDrawable(private val sizePx: Int) : android.graphics.drawable.Drawable() {
        override fun draw(canvas: android.graphics.Canvas) {}
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSPARENT
        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx
    }
}
