package br.com.wdc.shopping.nativeui.ios.toolkit

import br.com.wdc.shopping.nativeui.ios.theme.UIK
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.*

/**
 * UIKit DSL builder for constructing view hierarchies programmatically.
 *
 * All container methods (vStack, hStack, scrollView, view) accept a lambda with
 * receiver UIKitDom — children created inside the lambda are automatically added
 * to the container (via addArrangedSubview for stacks, addSubview for others).
 *
 * Usage:
 *   UIKitDom.build {
 *       vStack(spacing = 16.0) {
 *           label { text = "Hello" }
 *           textField("Email") {}
 *           button("Submit") { ... }
 *       }
 *   }
 */
@OptIn(ExperimentalForeignApi::class)
class UIKitDom private constructor(private val parent: UIView) {

    companion object {
        /** Build a view hierarchy rooted at a new UIView. */
        fun build(builder: UIKitDom.() -> Unit): UIView {
            val root = UIView().apply { translatesAutoresizingMaskIntoConstraints = false }
            val dom = UIKitDom(root)
            dom.builder()
            return root
        }

        /** Build children inside an existing root view. */
        fun render(root: UIView, builder: UIKitDom.() -> Unit) {
            val dom = UIKitDom(root)
            dom.builder()
        }
    }

    // MARK: - Containers

    fun vStack(spacing: Double = 0.0, configure: (UIStackView.() -> Unit)? = null, builder: UIKitDom.() -> Unit): UIStackView {
        val stack = UIStackView().apply {
            axis = UIK.AxisVertical
            this.spacing = spacing
            translatesAutoresizingMaskIntoConstraints = false
            alignment = UIK.StackAlignFill
        }
        addToParent(stack)
        configure?.invoke(stack)
        UIKitDom(stack).builder()
        return stack
    }

    fun hStack(spacing: Double = 0.0, configure: (UIStackView.() -> Unit)? = null, builder: UIKitDom.() -> Unit): UIStackView {
        val stack = UIStackView().apply {
            axis = UIK.AxisHorizontal
            this.spacing = spacing
            translatesAutoresizingMaskIntoConstraints = false
            alignment = UIK.StackAlignCenter
        }
        addToParent(stack)
        configure?.invoke(stack)
        UIKitDom(stack).builder()
        return stack
    }

    fun view(configure: (UIView.() -> Unit)? = null, builder: (UIKitDom.() -> Unit)? = null): UIView {
        val v = UIView().apply { translatesAutoresizingMaskIntoConstraints = false }
        addToParent(v)
        configure?.invoke(v)
        if (builder != null) UIKitDom(v).builder()
        return v
    }

    fun scrollView(configure: (UIScrollView.() -> Unit)? = null, builder: UIKitDom.() -> Unit): UIScrollView {
        val sv = UIScrollView().apply { translatesAutoresizingMaskIntoConstraints = false }
        addToParent(sv)
        configure?.invoke(sv)
        UIKitDom(sv).builder()
        return sv
    }

    // MARK: - Leaf Components

    fun label(configure: UILabel.() -> Unit): UILabel {
        val lbl = UILabel().apply { translatesAutoresizingMaskIntoConstraints = false }
        addToParent(lbl)
        lbl.configure()
        return lbl
    }

    fun button(title: String = "", configure: UIButton.() -> Unit): UIButton {
        val btn = UIButton(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)).apply {
            translatesAutoresizingMaskIntoConstraints = false
            if (title.isNotEmpty()) setTitle(title, forState = UIControlStateNormal)
        }
        addToParent(btn)
        btn.configure()
        return btn
    }

    fun textField(placeholder: String = "", configure: UITextField.() -> Unit): UITextField {
        val tf = UITextField().apply {
            translatesAutoresizingMaskIntoConstraints = false
            this.placeholder = placeholder
            borderStyle = UIK.BorderStyleRoundedRect
            font = UIFont.systemFontOfSize(16.0)
        }
        addToParent(tf)
        tf.configure()
        return tf
    }

    fun imageView(configure: UIImageView.() -> Unit): UIImageView {
        val iv = UIImageView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            contentMode = UIK.ContentModeScaleAspectFit
            clipsToBounds = true
        }
        addToParent(iv)
        iv.configure()
        return iv
    }

    fun activityIndicator(configure: UIActivityIndicatorView.() -> Unit = {}): UIActivityIndicatorView {
        val ai = UIActivityIndicatorView(frame = CGRectMake(0.0, 0.0, 20.0, 20.0)).apply {
            translatesAutoresizingMaskIntoConstraints = false
            activityIndicatorViewStyle = UIK.ActivityStyleMedium
            hidesWhenStopped = true
        }
        addToParent(ai)
        ai.configure()
        return ai
    }

    fun segmentedControl(items: List<String>, configure: UISegmentedControl.() -> Unit = {}): UISegmentedControl {
        val sc = UISegmentedControl(items = items).apply {
            translatesAutoresizingMaskIntoConstraints = false
        }
        addToParent(sc)
        sc.configure()
        return sc
    }

    // MARK: - Spacers

    fun spacer(height: Double = 0.0, width: Double = 0.0): UIView {
        val sp = UIView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            backgroundColor = UIColor.clearColor
        }
        if (height > 0.0) sp.heightAnchor.constraintEqualToConstant(height).active = true
        if (width > 0.0) sp.widthAnchor.constraintEqualToConstant(width).active = true
        addToParent(sp)
        return sp
    }

    fun flexSpacer(): UIView {
        val sp = UIView().apply {
            translatesAutoresizingMaskIntoConstraints = false
            setContentHuggingPriority(1.0f, UIK.AxisVertical)
            setContentHuggingPriority(1.0f, UIK.AxisHorizontal)
            setContentCompressionResistancePriority(1.0f, UIK.AxisVertical)
            setContentCompressionResistancePriority(1.0f, UIK.AxisHorizontal)
        }
        addToParent(sp)
        return sp
    }

    // MARK: - Utility

    /** Add an externally-created view to the current parent. */
    fun add(subview: UIView) {
        subview.translatesAutoresizingMaskIntoConstraints = false
        addToParent(subview)
    }

    /** Returns the current parent view. */
    fun parent(): UIView = parent

    // MARK: - Constraint Helpers

    /** Pin a view to fill its superview with optional insets. */
    fun pin(view: UIView, to: UIView = parent, insets: Double = 0.0) {
        NSLayoutConstraint.activateConstraints(listOf(
            view.topAnchor.constraintEqualToAnchor(to.topAnchor, insets),
            view.leadingAnchor.constraintEqualToAnchor(to.leadingAnchor, insets),
            view.trailingAnchor.constraintEqualToAnchor(to.trailingAnchor, -insets),
            view.bottomAnchor.constraintEqualToAnchor(to.bottomAnchor, -insets)
        ))
    }

    /** Center a view in another view. */
    fun center(view: UIView, inside: UIView = parent) {
        NSLayoutConstraint.activateConstraints(listOf(
            view.centerXAnchor.constraintEqualToAnchor(inside.centerXAnchor),
            view.centerYAnchor.constraintEqualToAnchor(inside.centerYAnchor)
        ))
    }

    /** Set fixed size constraints on a view. */
    fun size(view: UIView, width: Double? = null, height: Double? = null) {
        if (width != null) view.widthAnchor.constraintEqualToConstant(width).active = true
        if (height != null) view.heightAnchor.constraintEqualToConstant(height).active = true
    }

    // MARK: - Private

    private fun addToParent(view: UIView) {
        if (parent is UIStackView) {
            (parent as UIStackView).addArrangedSubview(view)
        } else {
            parent.addSubview(view)
        }
    }
}
