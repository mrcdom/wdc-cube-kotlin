package br.com.wdc.shopping.nativeui.android.views

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import br.com.wdc.shopping.nativeui.android.theme.*
import br.com.wdc.shopping.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.shopping.nativeui.android.toolkit.AndroidDom
import br.com.wdc.shopping.nativeui.android.toolkit.ViewUtils
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartItemScope

class CartViewAndroid(app: RootPresenter, scope: CartScope) : AbstractViewAndroid<CartScope>("cart-view", app, scope) {

    private lateinit var scrollView: ScrollView
    private lateinit var stackView: LinearLayout
    private lateinit var emptyContainer: LinearLayout
    private lateinit var totalLabel: TextView
    private lateinit var footerContainer: LinearLayout
    private lateinit var errorLabel: TextView
    private lateinit var backButtonEmpty: Button

    /** The row views on screen, in order; each one is the catalog's view of a [CartItemScope]. */
    private var rowViews: List<AbstractViewAndroid<*>> = emptyList()

    private var lastRows: List<CartItemScope>? = null
    private var lastTotal: Double = -1.0
    private var lastError: String? = null

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val root = parent()
            root.setBackgroundColor(ShoppingColors.Background)

            vStack {
                // Title
                textView {
                    text = "Shopping Cart"
                    textSize = 24f
                    setTextColor(ShoppingColors.OnSurface)
                    setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                }

                // Separator
                frame(configure = {
                    setBackgroundColor(ShoppingColors.SurfaceVariant)
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        height = 1
                        marginStart = (16 * density).toInt()
                        marginEnd = (16 * density).toInt()
                    }
                })

                // Error label
                errorLabel = textView {
                    setTextColor(ShoppingColors.Error)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    visibility = View.GONE
                    setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), 0)
                }

                // Scroll area (items + footer)
                scrollView = scrollView(configure = {
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = 0
                        weight = 1f
                    }
                }) {
                    vStack {
                        // Items stack
                        stackView = vStack(spacing = (10 * density).toInt(), configure = {
                            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
                        }) {}

                        // Footer: separator + total + buttons
                        footerContainer = vStack(configure = {
                            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
                        }) {
                            // Separator
                            frame(configure = {
                                setBackgroundColor(ShoppingColors.SurfaceVariant)
                                (layoutParams as LinearLayout.LayoutParams).height = 1
                            })

                            // Total row
                            hStack(configure = {
                                gravity = Gravity.CENTER_VERTICAL
                                (layoutParams as LinearLayout.LayoutParams).topMargin = (16 * density).toInt()
                            }) {
                                textView {
                                    text = "Total:"
                                    textSize = 16f
                                    setTextColor(ShoppingColors.OnSurfaceVariant)
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                                        marginEnd = (12 * density).toInt()
                                    }
                                }
                                flexSpacer()
                                frame(configure = {
                                    background = ShoppingStyles.roundedBackground(ShoppingColors.PriceBackground, 10 * density)
                                    setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                                    (layoutParams as LinearLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                                }) {
                                    totalLabel = textView {
                                        textSize = 18f
                                        setTypeface(null, Typeface.BOLD)
                                        setTextColor(ShoppingColors.PriceColor)
                                        (layoutParams as FrameLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                                    }
                                }
                            }

                            // Buttons row
                            hStack(configure = {
                                gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
                                (layoutParams as LinearLayout.LayoutParams).apply {
                                    topMargin = (16 * density).toInt()
                                    height = (48 * density).toInt()
                                }
                            }) {
                                // Continue shopping
                                button("Continue Shopping") {
                                    textSize = 14f
                                    setTextColor(ShoppingColors.Primary)
                                    background = ShoppingStyles.borderPill(ShoppingColors.Primary, 12 * density)
                                    val padH = (20 * density).toInt()
                                    val padV = (8 * density).toInt()
                                    setPadding(padH, padV, padH, padV)
                                    setCompoundDrawablesRelativeWithIntrinsicBounds(
                                        ShoppingIcons.arrowBack(ctx, 16, ShoppingColors.Primary), null, null, null
                                    )
                                    compoundDrawablePadding = (6 * density).toInt()
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                                        height = (48 * density).toInt()
                                        marginEnd = (16 * density).toInt()
                                    }
                                    setOnClickListener { safeAction("back") { scope.onOpenProducts() } }
                                }
                                // Buy button
                                button("Buy") {
                                    textSize = 16f
                                    setTypeface(null, Typeface.BOLD)
                                    setTextColor(Color.WHITE)
                                    background = ShoppingStyles.roundedBackground(ShoppingColors.PriceColor, 12 * density)
                                    val padH = (32 * density).toInt()
                                    val padV = (8 * density).toInt()
                                    setPadding(padH, padV, padH, padV)
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                                        height = (48 * density).toInt()
                                    }
                                    setOnClickListener { safeAction("buy") { scope.onBuy() } }
                                }
                            }
                        }
                    }
                }

                // Empty state (centered in the remaining space)
                emptyContainer = vStack(configure = {
                    gravity = Gravity.CENTER_HORIZONTAL
                    visibility = View.GONE
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = 0
                        weight = 1f
                    }
                    setPadding(0, (48 * density).toInt(), 0, (24 * density).toInt())
                }) {
                    imageView {
                        setImageDrawable(ShoppingIcons.shoppingCart(ctx, 48, ShoppingColors.OnSurfaceVariant))
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = (48 * density).toInt()
                            height = (48 * density).toInt()
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                    }
                    textView {
                        text = "Your cart is empty"
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(ShoppingColors.OnSurface)
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).topMargin = (16 * density).toInt()
                    }
                    textView {
                        text = "Add products to start shopping"
                        textSize = 14f
                        setTextColor(ShoppingColors.OnSurfaceVariant)
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).topMargin = (8 * density).toInt()
                    }
                    backButtonEmpty = button("Continue Shopping") {
                        textSize = 14f
                        setTextColor(ShoppingColors.Primary)
                        background = ShoppingStyles.borderPill(ShoppingColors.Primary, 12 * density)
                        val padH = (20 * density).toInt()
                        val padV = (8 * density).toInt()
                        setPadding(padH, padV, padH, padV)
                        setCompoundDrawablesRelativeWithIntrinsicBounds(
                            ShoppingIcons.arrowBack(ctx, 16, ShoppingColors.Primary), null, null, null
                        )
                        compoundDrawablePadding = (6 * density).toInt()
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = (48 * density).toInt()
                            topMargin = (32 * density).toInt()
                            gravity = Gravity.CENTER_HORIZONTAL
                        }
                        setOnClickListener { safeAction("back") { scope.onOpenProducts() } }
                    }
                }
            }
        }
    }

    override fun doUpdate() {
        val rows = scope.rows

        if (rows !== lastRows) {
            lastRows = rows

            if (rows.isEmpty()) {
                emptyContainer.visibility = View.VISIBLE
                scrollView.visibility = View.GONE
            } else {
                emptyContainer.visibility = View.GONE
                scrollView.visibility = View.VISIBLE
            }
            syncRows(rows)
        }

        val total = scope.total
        if (total != lastTotal) {
            lastTotal = total
            totalLabel.text = "\$${ViewUtils.formatPrice(total)}"
        }

        val errorMessage = scope.errorMessage
        if (errorMessage != lastError) {
            lastError = errorMessage
            if (!errorMessage.isNullOrBlank()) {
                errorLabel.text = errorMessage
                errorLabel.visibility = View.VISIBLE
            } else {
                errorLabel.visibility = View.GONE
            }
        }
    }

    /**
     * Hosts the view of each row, in order. A row that stays keeps its view (the application creates one per scope);
     * the views of rows that left are released by the presenter, so here they are only taken off the screen.
     */
    private fun syncRows(rows: List<CartItemScope>) {
        val next = rows.mapNotNull { app.viewOf(it) as? AbstractViewAndroid<*> }
        if (next.size == rowViews.size && next.indices.all { next[it] === rowViews[it] }) return
        rowViews = next
        stackView.removeAllViews()
        for (view in next) {
            (view.rootView.parent as? ViewGroup)?.removeView(view.rootView)
            stackView.addView(
                view.rootView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }
    }
}
