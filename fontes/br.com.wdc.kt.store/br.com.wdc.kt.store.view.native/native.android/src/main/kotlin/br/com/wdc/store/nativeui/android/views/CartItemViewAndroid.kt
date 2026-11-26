package br.com.wdc.store.nativeui.android.views

import br.com.wdc.store.design.StoreTokens
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import br.com.wdc.store.nativeui.android.theme.*
import br.com.wdc.store.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.store.nativeui.android.toolkit.AndroidDom
import br.com.wdc.store.nativeui.android.toolkit.ViewUtils
import br.com.wdc.store.nativeui.android.AndroidNativeRootPresenter
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope

/** A row of the cart: one product, its quantity controls and its remove button. Hosted by [CartViewAndroid]. */
class CartItemViewAndroid(app: AndroidNativeRootPresenter, scope: CartItemScope) :
    AbstractViewAndroid<CartItemScope>("cart-item", app, scope) {

    private lateinit var imageView: ImageView
    private lateinit var nameLabel: TextView
    private lateinit var priceLabel: TextView
    private lateinit var qtyLabel: TextView

    private var lastProductId: Long = -1
    private var lastName: String? = null
    private var lastPrice: Double = -1.0
    private var lastQuantity: Int = -1

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val card = parent()
            card.background = StoreStyles.roundedBackground(StoreColors.SurfaceVariant40, Dimens.radiusSm * density)

            hStack(spacing = (Dimens.spacingMd * density).toInt(), configure = {
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
                    setBackgroundColor(StoreColors.SurfaceVariant)
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
                        textSize = StoreTokens.FontSize.Body.toFloat()
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(StoreColors.OnSurface)
                        maxLines = 1
                    }
                    priceLabel = textView {
                        textSize = 13f
                        setTextColor(StoreColors.PriceColor)
                    }
                }

                // Quantity controls
                hStack(spacing = (Dimens.spacingXs * density).toInt(), configure = {
                    gravity = Gravity.CENTER_VERTICAL
                    (layoutParams as LinearLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                }) {
                    button("−") {
                        textSize = StoreTokens.FontSize.Body.toFloat()
                        background = StoreStyles.roundedBackground(StoreColors.SecondaryContainer, Dimens.radiusXs * density)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = (28 * density).toInt()
                            height = (28 * density).toInt()
                        }
                        setPadding(0, 0, 0, 0)
                        setOnClickListener {
                            val quantity = scope.quantity
                            if (quantity > 1) safeAction("minus") { scope.onModifyQuantity(quantity - 1) }
                        }
                    }
                    qtyLabel = textView {
                        textSize = StoreTokens.FontSize.Body.toFloat()
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        (layoutParams as LinearLayout.LayoutParams).width = (30 * density).toInt()
                    }
                    button("+") {
                        textSize = StoreTokens.FontSize.Body.toFloat()
                        background = StoreStyles.roundedBackground(StoreColors.SecondaryContainer, Dimens.radiusXs * density)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = (28 * density).toInt()
                            height = (28 * density).toInt()
                        }
                        setPadding(0, 0, 0, 0)
                        setOnClickListener {
                            val quantity = scope.quantity
                            safeAction("plus") { scope.onModifyQuantity(quantity + 1) }
                        }
                    }
                    // Remove button
                    button("✕") {
                        textSize = StoreTokens.FontSize.Body.toFloat()
                        setTextColor(StoreColors.Error)
                        setBackgroundColor(Color.TRANSPARENT)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = (28 * density).toInt()
                            height = (28 * density).toInt()
                        }
                        setPadding(0, 0, 0, 0)
                        setOnClickListener { safeAction("remove") { scope.onRemove() } }
                    }
                }
            }
        }
    }

    override fun doUpdate() {
        if (scope.productId != lastProductId) {
            lastProductId = scope.productId
            ViewUtils.loadImageAsync(imageView, ViewUtils.productImageUrl(scope.productId))
        }
        if (scope.name != lastName) {
            lastName = scope.name
            nameLabel.text = scope.name ?: ""
        }
        if (scope.price != lastPrice || scope.quantity != lastQuantity) {
            lastPrice = scope.price
            lastQuantity = scope.quantity
            priceLabel.text = "\$${ViewUtils.formatPrice(scope.price)} × ${scope.quantity}"
            qtyLabel.text = scope.quantity.toString()
        }
    }
}
