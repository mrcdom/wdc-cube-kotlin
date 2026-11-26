package br.com.wdc.store.nativeui.android.views

import br.com.wdc.store.i18n.productAddToCart
import br.com.wdc.store.i18n.productBack
import br.com.wdc.store.i18n.productQuantity
import br.com.wdc.store.design.StoreTokens
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
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope

class ProductViewAndroid(app: AndroidNativeRootPresenter, scope: ProductScope) : AbstractViewAndroid<ProductScope>("product-view", app, scope) {

    private lateinit var imageView: ImageView
    private lateinit var nameLabel: TextView
    private lateinit var descriptionLabel: TextView
    private lateinit var priceLabel: TextView
    private lateinit var quantityLabel: TextView
    private lateinit var addButton: Button
    private lateinit var backButton: Button

    private var lastProductId: Long = -1
    private var quantity = 1

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val root = parent()
            root.setBackgroundColor(StoreColors.Background)

            scrollView {
                vStack(spacing = (Dimens.spacingLg * density).toInt(), configure = {
                    setPadding((Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt())
                }) {
                    // Title
                    nameLabel = textView {
                        textSize = 26f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(StoreColors.OnSurface)
                    }

                    // Separator
                    frame(configure = {
                        setBackgroundColor(StoreColors.SurfaceVariant)
                        (layoutParams as LinearLayout.LayoutParams).height = 1
                    })

                    // Description card
                    frame(configure = {
                        background = StoreStyles.roundedBackground(StoreColors.Surface, Dimens.radiusSm * density)
                        StoreStyles.applyCardStyle(this, Dimens.radiusSm.toFloat())
                        setPadding((Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingLg * density).toInt())
                    }) {
                        descriptionLabel = textView {
                            textSize = StoreTokens.FontSize.Body.toFloat()
                            setTextColor(StoreColors.OnSurfaceVariant)
                            (layoutParams as FrameLayout.LayoutParams).width = ViewGroup.LayoutParams.MATCH_PARENT
                        }
                    }

                    // Price + Image row
                    hStack(configure = {
                        gravity = Gravity.TOP
                    }) {
                        // Left side: price + quantity
                        vStack(spacing = (Dimens.spacingMd * density).toInt(), configure = {
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = 0
                                weight = 1f
                            }
                        }) {
                            // Price badge (wide)
                            frame(configure = {
                                background = StoreStyles.roundedBackground(StoreColors.PriceBackground, Dimens.radiusSm * density)
                                (layoutParams as LinearLayout.LayoutParams).apply {
                                    width = ViewGroup.LayoutParams.MATCH_PARENT
                                    height = (40 * density).toInt()
                                }
                            }) {
                                priceLabel = textView {
                                    textSize = 18f
                                    setTypeface(null, Typeface.BOLD)
                                    setTextColor(StoreColors.PriceColor)
                                    gravity = Gravity.CENTER
                                    (layoutParams as FrameLayout.LayoutParams).apply {
                                        width = ViewGroup.LayoutParams.MATCH_PARENT
                                        height = ViewGroup.LayoutParams.MATCH_PARENT
                                        gravity = Gravity.CENTER
                                    }
                                }
                            }

                            // Quantity selector
                            hStack(configure = {
                                gravity = Gravity.CENTER_VERTICAL
                            }) {
                                textView {
                                    text = messages.productQuantity()
                                    textSize = StoreTokens.FontSize.Body.toFloat()
                                    setTypeface(null, Typeface.BOLD)
                                    setTextColor(StoreColors.OnSurface)
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                                        marginEnd = (Dimens.spacingMd * density).toInt()
                                    }
                                }
                                // Minus button
                                button("−") {
                                    textSize = 18f
                                    setTextColor(StoreColors.OnSurfaceVariant)
                                    background = StoreStyles.roundedBackground(StoreColors.SurfaceVariant, 6 * density)
                                    gravity = Gravity.CENTER
                                    setPadding(0, 0, 0, 0)
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = (36 * density).toInt()
                                        height = (36 * density).toInt()
                                    }
                                    setOnClickListener {
                                        if (quantity > 1) { quantity--; quantityLabel.text = quantity.toString() }
                                    }
                                }
                                // Quantity number
                                quantityLabel = textView {
                                    text = "1"
                                    textSize = 18f
                                    setTypeface(null, Typeface.BOLD)
                                    setTextColor(StoreColors.OnSurface)
                                    gravity = Gravity.CENTER
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = (40 * density).toInt()
                                    }
                                }
                                // Plus button
                                button("+") {
                                    textSize = 18f
                                    setTextColor(StoreColors.OnSurfaceVariant)
                                    background = StoreStyles.roundedBackground(StoreColors.SurfaceVariant, 6 * density)
                                    gravity = Gravity.CENTER
                                    setPadding(0, 0, 0, 0)
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = (36 * density).toInt()
                                        height = (36 * density).toInt()
                                    }
                                    setOnClickListener {
                                        quantity++; quantityLabel.text = quantity.toString()
                                    }
                                }
                            }
                        }

                        // Right side: product image
                        imageView = imageView {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setBackgroundColor(StoreColors.SurfaceVariant)
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = (160 * density).toInt()
                                height = (160 * density).toInt()
                                marginStart = (Dimens.spacingLg * density).toInt()
                            }
                        }
                    }

                    // Buttons row
                    hStack(configure = {
                        gravity = Gravity.CENTER_VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).topMargin = (Dimens.spacingSm * density).toInt()
                    }) {
                        // Back button (outline)
                        backButton = button(messages.productBack()) {
                            textSize = 15f
                            setTextColor(StoreColors.Primary)
                            background = StoreStyles.borderPill(StoreColors.Primary, Dimens.radiusMd * density)
                            val padH = (20 * density).toInt()
                            val padV = (Dimens.spacingMd * density).toInt()
                            setPadding(padH, padV, padH, padV)
                            setCompoundDrawablesRelativeWithIntrinsicBounds(
                                StoreIcons.arrowBack(ctx, 18, StoreColors.Primary), null, null, null
                            )
                            compoundDrawablePadding = (6 * density).toInt()
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                height = (48 * density).toInt()
                                marginEnd = (Dimens.spacingMd * density).toInt()
                            }
                            setOnClickListener { safeAction("back") { scope.onOpenProducts() } }
                        }

                        // Add to Cart button (primary)
                        addButton = button(messages.productAddToCart()) {
                            textSize = 15f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(android.graphics.Color.WHITE)
                            background = StoreStyles.roundedBackground(StoreColors.Primary, Dimens.radiusMd * density)
                            val padH = (20 * density).toInt()
                            val padV = (Dimens.spacingMd * density).toInt()
                            setPadding(padH, padV, padH, padV)
                            setCompoundDrawablesRelativeWithIntrinsicBounds(
                                StoreIcons.addShoppingCart(ctx, 20, android.graphics.Color.WHITE), null, null, null
                            )
                            compoundDrawablePadding = (Dimens.spacingSm * density).toInt()
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = 0
                                weight = 1f
                                height = (48 * density).toInt()
                            }
                            setOnClickListener {
                                safeAction("addToCart") { scope.onAddToCart(quantity) }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun doUpdate() {
        val product = scope.product ?: return
        if (product.id == lastProductId) return
        lastProductId = product.id

        nameLabel.text = product.name ?: ""
        descriptionLabel.text = ViewUtils.stripHtml(product.description)
        priceLabel.text = "\$${ViewUtils.formatPrice(product.price)}"
        ViewUtils.loadImageAsync(imageView, ViewUtils.productImageUrl(product.id))

        // Reset quantity
        quantity = 1
        quantityLabel.text = "1"
    }
}
