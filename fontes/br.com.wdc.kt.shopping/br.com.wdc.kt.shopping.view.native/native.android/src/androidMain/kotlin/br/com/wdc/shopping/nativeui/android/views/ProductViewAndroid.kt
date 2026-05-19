package br.com.wdc.shopping.nativeui.android.views

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import br.com.wdc.shopping.nativeui.android.theme.*
import br.com.wdc.shopping.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.shopping.nativeui.android.toolkit.AndroidDom
import br.com.wdc.shopping.nativeui.android.toolkit.ViewUtils
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter

class ProductViewAndroid(presenter: ProductPresenter) : AbstractViewAndroid<ProductPresenter>("product-view", presenter) {

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
            root.setBackgroundColor(ShoppingColors.Background)

            scrollView {
                vStack(spacing = (16 * density).toInt(), configure = {
                    setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                }) {
                    // Title
                    nameLabel = textView {
                        textSize = 26f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(ShoppingColors.OnSurface)
                    }

                    // Separator
                    frame(configure = {
                        setBackgroundColor(ShoppingColors.SurfaceVariant)
                        (layoutParams as LinearLayout.LayoutParams).height = 1
                    })

                    // Description card
                    frame(configure = {
                        background = ShoppingStyles.roundedBackground(ShoppingColors.Surface, 8 * density)
                        ShoppingStyles.applyCardStyle(this, 8f)
                        setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                    }) {
                        descriptionLabel = textView {
                            textSize = 14f
                            setTextColor(ShoppingColors.OnSurfaceVariant)
                            (layoutParams as FrameLayout.LayoutParams).width = ViewGroup.LayoutParams.MATCH_PARENT
                        }
                    }

                    // Price + Image row
                    hStack(configure = {
                        gravity = Gravity.TOP
                    }) {
                        // Left side: price + quantity
                        vStack(spacing = (12 * density).toInt(), configure = {
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = 0
                                weight = 1f
                            }
                        }) {
                            // Price badge (wide)
                            frame(configure = {
                                background = ShoppingStyles.roundedBackground(ShoppingColors.PriceBackground, 8 * density)
                                (layoutParams as LinearLayout.LayoutParams).apply {
                                    width = ViewGroup.LayoutParams.MATCH_PARENT
                                    height = (40 * density).toInt()
                                }
                            }) {
                                priceLabel = textView {
                                    textSize = 18f
                                    setTypeface(null, Typeface.BOLD)
                                    setTextColor(ShoppingColors.PriceColor)
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
                                    text = "Qtd:"
                                    textSize = 14f
                                    setTypeface(null, Typeface.BOLD)
                                    setTextColor(ShoppingColors.OnSurface)
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                                        marginEnd = (12 * density).toInt()
                                    }
                                }
                                // Minus button
                                button("−") {
                                    textSize = 18f
                                    setTextColor(ShoppingColors.OnSurfaceVariant)
                                    background = ShoppingStyles.roundedBackground(ShoppingColors.SurfaceVariant, 6 * density)
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
                                    setTextColor(ShoppingColors.OnSurface)
                                    gravity = Gravity.CENTER
                                    (layoutParams as LinearLayout.LayoutParams).apply {
                                        width = (40 * density).toInt()
                                    }
                                }
                                // Plus button
                                button("+") {
                                    textSize = 18f
                                    setTextColor(ShoppingColors.OnSurfaceVariant)
                                    background = ShoppingStyles.roundedBackground(ShoppingColors.SurfaceVariant, 6 * density)
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
                            setBackgroundColor(ShoppingColors.SurfaceVariant)
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = (160 * density).toInt()
                                height = (160 * density).toInt()
                                marginStart = (16 * density).toInt()
                            }
                        }
                    }

                    // Buttons row
                    hStack(configure = {
                        gravity = Gravity.CENTER_VERTICAL
                        (layoutParams as LinearLayout.LayoutParams).topMargin = (8 * density).toInt()
                    }) {
                        // Voltar button (outline)
                        backButton = button("Voltar") {
                            textSize = 15f
                            setTextColor(ShoppingColors.Primary)
                            background = ShoppingStyles.borderPill(ShoppingColors.Primary, 12 * density)
                            val padH = (20 * density).toInt()
                            val padV = (12 * density).toInt()
                            setPadding(padH, padV, padH, padV)
                            setCompoundDrawablesRelativeWithIntrinsicBounds(
                                ShoppingIcons.arrowBack(ctx, 18, ShoppingColors.Primary), null, null, null
                            )
                            compoundDrawablePadding = (6 * density).toInt()
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = ViewGroup.LayoutParams.WRAP_CONTENT
                                height = (48 * density).toInt()
                                marginEnd = (12 * density).toInt()
                            }
                            setOnClickListener { safeAction("back") { presenter.onOpenProducts() } }
                        }

                        // Adicionar ao Carrinho button (primary)
                        addButton = button("Adicionar ao Carrinho") {
                            textSize = 15f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(android.graphics.Color.WHITE)
                            background = ShoppingStyles.roundedBackground(ShoppingColors.Primary, 12 * density)
                            val padH = (20 * density).toInt()
                            val padV = (12 * density).toInt()
                            setPadding(padH, padV, padH, padV)
                            setCompoundDrawablesRelativeWithIntrinsicBounds(
                                ShoppingIcons.addShoppingCart(ctx, 20, android.graphics.Color.WHITE), null, null, null
                            )
                            compoundDrawablePadding = (8 * density).toInt()
                            (layoutParams as LinearLayout.LayoutParams).apply {
                                width = 0
                                weight = 1f
                                height = (48 * density).toInt()
                            }
                            setOnClickListener {
                                safeAction("addToCart") { presenter.onAddToCart(quantity) }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun doUpdate() {
        val product = presenter.state.product ?: return
        if (product.id == lastProductId) return
        lastProductId = product.id

        nameLabel.text = product.name ?: ""
        descriptionLabel.text = ViewUtils.stripHtml(product.description)
        priceLabel.text = "R$ ${ViewUtils.formatPrice(product.price)}"
        ViewUtils.loadImageAsync(imageView, ViewUtils.productImageUrl(product.id))

        // Reset quantity
        quantity = 1
        quantityLabel.text = "1"
    }
}
