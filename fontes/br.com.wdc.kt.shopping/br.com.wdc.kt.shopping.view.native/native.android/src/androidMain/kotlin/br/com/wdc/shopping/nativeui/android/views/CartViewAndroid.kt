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
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.structs.CartItem

class CartViewAndroid(presenter: CartPresenter) : AbstractViewAndroid<CartPresenter>("cart-view", presenter) {

    private lateinit var scrollView: ScrollView
    private lateinit var stackView: LinearLayout
    private lateinit var emptyContainer: LinearLayout
    private lateinit var totalLabel: TextView
    private lateinit var footerContainer: LinearLayout
    private lateinit var errorLabel: TextView
    private lateinit var backButtonEmpty: Button
    private lateinit var itemsSlot: ListSlot<CartItem, CartItemView>

    private var lastItems: List<CartItem>? = null
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
                    text = "Carrinho de Compras"
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
                                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                                (layoutParams as LinearLayout.LayoutParams).apply {
                                    topMargin = (16 * density).toInt()
                                    height = (48 * density).toInt()
                                }
                            }) {
                                // Continue shopping
                                button("Continuar Comprando") {
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
                                    setOnClickListener { safeAction("back") { presenter.onOpenProducts() } }
                                }
                                // Buy button
                                button("Comprar") {
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
                                    setOnClickListener { safeAction("buy") { presenter.onBuy() } }
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
                        text = "Seu carrinho está vazio"
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(ShoppingColors.OnSurface)
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).topMargin = (16 * density).toInt()
                    }
                    textView {
                        text = "Adicione produtos para começar suas compras"
                        textSize = 14f
                        setTextColor(ShoppingColors.OnSurfaceVariant)
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).topMargin = (8 * density).toInt()
                    }
                    backButtonEmpty = button("Continuar Comprando") {
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
                            gravity = Gravity.END
                        }
                        setOnClickListener { safeAction("back") { presenter.onOpenProducts() } }
                    }
                }
            }

            itemsSlot = newListSlot(
                stackView,
                factory = { CartItemView(presenter).also { it.initialize() } },
                updater = { view, item -> view.setState(item); view.forceUpdate() }
            )
        }
    }

    override fun doUpdate() {
        val state = presenter.state
        val items = state.items

        if (items !== lastItems) {
            lastItems = items

            if (items.isEmpty()) {
                emptyContainer.visibility = View.VISIBLE
                scrollView.visibility = View.GONE
                itemsSlot.sync(emptyList())
            } else {
                emptyContainer.visibility = View.GONE
                scrollView.visibility = View.VISIBLE
                itemsSlot.sync(items)

                var total = 0.0
                for (item in items) {
                    total += item.price * item.quantity
                }
                totalLabel.text = "R$ ${ViewUtils.formatPrice(total)}"
            }
        }

        val errorMessage = state.errorMessage
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
}

// --- Cart Item sub-view ---

private class CartItemView(presenter: CartPresenter) : AbstractViewAndroid<CartPresenter>("cart-item", presenter) {

    private lateinit var imageView: ImageView
    private lateinit var nameLabel: TextView
    private lateinit var priceLabel: TextView
    private lateinit var qtyLabel: TextView

    var item: CartItem? = null
        private set

    private var lastItemId: Long = -1
    private var lastName: String? = null
    private var lastPrice: Double = -1.0
    private var lastQuantity: Int = -1

    fun setState(cartItem: CartItem) {
        this.item = cartItem
    }

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val card = parent()
            card.background = ShoppingStyles.roundedBackground(ShoppingColors.SurfaceVariant40, 8 * density)

            hStack(spacing = (12 * density).toInt(), configure = {
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
                (layoutParams as FrameLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }) {
                // Product image
                imageView = imageView {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(ShoppingColors.SurfaceVariant)
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = (50 * density).toInt()
                        height = (50 * density).toInt()
                    }
                }

                // Name + price
                vStack(spacing = (2 * density).toInt(), configure = {
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        weight = 1f
                        width = 0
                    }
                }) {
                    nameLabel = textView {
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(ShoppingColors.OnSurface)
                        maxLines = 1
                    }
                    priceLabel = textView {
                        textSize = 13f
                        setTextColor(ShoppingColors.PriceColor)
                    }
                }

                // Quantity controls
                hStack(spacing = (4 * density).toInt(), configure = {
                    gravity = Gravity.CENTER_VERTICAL
                    (layoutParams as LinearLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                }) {
                    button("−") {
                        textSize = 14f
                        background = ShoppingStyles.roundedBackground(ShoppingColors.SecondaryContainer, 4 * density)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = (28 * density).toInt()
                            height = (28 * density).toInt()
                        }
                        setPadding(0, 0, 0, 0)
                        setOnClickListener {
                            val i = item ?: return@setOnClickListener
                            if (i.quantity > 1) safeAction("minus") { presenter.onModifyQuantity(i.id, i.quantity - 1) }
                        }
                    }
                    qtyLabel = textView {
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).width = (30 * density).toInt()
                    }
                    button("+") {
                        textSize = 14f
                        background = ShoppingStyles.roundedBackground(ShoppingColors.SecondaryContainer, 4 * density)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = (28 * density).toInt()
                            height = (28 * density).toInt()
                        }
                        setPadding(0, 0, 0, 0)
                        setOnClickListener {
                            val i = item ?: return@setOnClickListener
                            safeAction("plus") { presenter.onModifyQuantity(i.id, i.quantity + 1) }
                        }
                    }
                    // Remove button
                    button("✕") {
                        textSize = 14f
                        setTextColor(ShoppingColors.Error)
                        setBackgroundColor(Color.TRANSPARENT)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = (28 * density).toInt()
                            height = (28 * density).toInt()
                        }
                        setPadding(0, 0, 0, 0)
                        setOnClickListener {
                            val i = item ?: return@setOnClickListener
                            safeAction("remove") { presenter.onRemoveProduct(i.id) }
                        }
                    }
                }
            }
        }
    }

    override fun doUpdate() {
        val i = item ?: return

        if (i.id != lastItemId) {
            lastItemId = i.id
            ViewUtils.loadImageAsync(imageView, ViewUtils.productImageUrl(i.id))
            lastName = null
            lastPrice = -1.0
            lastQuantity = -1
        }
        if (i.name != lastName) {
            lastName = i.name
            nameLabel.text = i.name ?: ""
        }
        if (i.price != lastPrice || i.quantity != lastQuantity) {
            lastPrice = i.price
            lastQuantity = i.quantity
            priceLabel.text = "R$ ${ViewUtils.formatPrice(i.price)} × ${i.quantity}"
            qtyLabel.text = i.quantity.toString()
        }
    }
}
