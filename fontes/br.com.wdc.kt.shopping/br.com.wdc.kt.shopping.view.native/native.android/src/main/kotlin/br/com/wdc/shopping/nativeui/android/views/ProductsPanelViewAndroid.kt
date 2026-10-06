package br.com.wdc.shopping.nativeui.android.views

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import br.com.wdc.shopping.nativeui.android.toolkit.FlowLayout
import coil.imageLoader
import coil.request.ImageRequest
import br.com.wdc.shopping.nativeui.android.theme.*
import br.com.wdc.shopping.nativeui.android.toolkit.AbstractViewAndroid
import br.com.wdc.shopping.nativeui.android.toolkit.AndroidDom
import br.com.wdc.shopping.nativeui.android.toolkit.ViewUtils
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo

class ProductsPanelViewAndroid(presenter: ProductsPanelPresenter) : AbstractViewAndroid<ProductsPanelPresenter>("products-panel-view", presenter) {

    private lateinit var scrollView: ScrollView
    private lateinit var stackView: FlowLayout
    private lateinit var emptyLabel: TextView
    private lateinit var headerBadge: TextView
    private lateinit var productsSlot: ListSlot<ProductInfo, ProductCardView>

    private var lastProducts: List<ProductInfo>? = null
    private var lastCount: Int = -1

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val root = parent()
            root.setBackgroundColor(ShoppingColors.Background)

            vStack {
                // Section header
                hStack(configure = {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (4 * density).toInt())
                }) {
                    textView {
                        text = "Produtos"
                        textSize = 17f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(ShoppingColors.OnSurface)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            weight = 1f
                            width = 0
                        }
                    }
                    headerBadge = textView {
                        textSize = 12f
                        setTextColor(ShoppingColors.OnPrimaryContainer)
                        gravity = Gravity.CENTER
                        background = ShoppingStyles.roundedBackground(ShoppingColors.SecondaryContainer, 8 * density)
                        val padH = (16 * density).toInt()
                        val padV = (6 * density).toInt()
                        setPadding(padH, padV, padH, padV)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    }
                }

                // Separator
                frame(configure = {
                    setBackgroundColor(ShoppingColors.SurfaceVariant)
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        height = (1 * density).toInt().coerceAtLeast(1)
                        topMargin = (8 * density).toInt()
                        bottomMargin = (12 * density).toInt()
                        marginStart = (16 * density).toInt()
                        marginEnd = (16 * density).toInt()
                    }
                })

                // Scroll + stack for items
                scrollView = scrollView(configure = {
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = 0
                        weight = 1f
                    }
                }) {
                    stackView = flowLayout(
                        minChildWidth = (160 * density).toInt(),
                        horizontalSpacing = (12 * density).toInt(),
                        verticalSpacing = (12 * density).toInt(),
                        configure = {
                            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                        }
                    ) {}
                }
            }

            // Empty label (centered)
            emptyLabel = textView {
                text = "Nenhum produto disponível"
                textSize = 16f
                setTextColor(ShoppingColors.OnSurfaceVariant)
                gravity = Gravity.CENTER
                visibility = View.GONE
                (layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER
            }

            productsSlot = newListSlot(
                stackView,
                factory = { ProductCardView(presenter).also { it.initialize() } },
                updater = { view, item -> view.setState(item); view.forceUpdate() }
            )
        }
    }

    override fun doUpdate() {
        val products = presenter.state.products
        if (products === lastProducts) return
        lastProducts = products

        if (products.isNullOrEmpty()) {
            emptyLabel.visibility = View.VISIBLE
            scrollView.visibility = View.GONE
            productsSlot.sync(emptyList())
            if (lastCount != 0) {
                lastCount = 0
                headerBadge.text = "0 itens"
            }
        } else {
            emptyLabel.visibility = View.GONE
            scrollView.visibility = View.VISIBLE

            // Prefetch all product images in parallel
            val ctx = RootViewAndroid.appContext
            val loader = ctx.imageLoader
            for (p in products) {
                val url = ViewUtils.productImageUrl(p.id)
                val req = ImageRequest.Builder(ctx).data(url).build()
                loader.enqueue(req)
            }

            productsSlot.sync(products)
            val count = products.size
            if (count != lastCount) {
                lastCount = count
                headerBadge.text = "$count itens"
            }
        }
    }
}

// --- Product Card sub-view ---

private class ProductCardView(presenter: ProductsPanelPresenter) : AbstractViewAndroid<ProductsPanelPresenter>("product-card", presenter) {

    private lateinit var imageView: ImageView
    private lateinit var nameLabel: TextView
    private lateinit var priceLabel: TextView

    var product: ProductInfo? = null
        private set

    private var lastProductId: Long = -1
    private var lastName: String? = null
    private var lastPrice: Double = -1.0

    fun setState(item: ProductInfo) {
        this.product = item
    }

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val card = parent()
            val cornerPx = 8f * density
            val normalBg = ShoppingStyles.roundedBackground(ShoppingColors.Surface, cornerPx)
            val pressedBg = ShoppingStyles.roundedBackground(ShoppingColors.SurfaceVariant, cornerPx)
            val statesBg = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
                addState(intArrayOf(), normalBg)
            }
            card.background = statesBg
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener {
                val id = product?.id ?: return@setOnClickListener
                safeAction("openProduct") { presenter.onOpenProduct(id) }
            }
            (card as FrameLayout).clipChildren = true

            vStack {
                // Image
                imageView = imageView {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(ShoppingColors.SurfaceVariant)
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = (Dimens.cardImageHeight * density).toInt()
                    }
                }

                // Content
                vStack(spacing = (6 * density).toInt(), configure = {
                    setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                }) {
                    nameLabel = textView {
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(ShoppingColors.OnSurface)
                        maxLines = 2
                    }

                    // Price badge
                    frame(configure = {
                        background = ShoppingStyles.roundedBackground(ShoppingColors.PriceBackground, 8 * density)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = (28 * density).toInt()
                        }
                        setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
                    }) {
                        priceLabel = textView {
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(ShoppingColors.PriceColor)
                            (layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER
                        }
                    }
                }
            }
        }
    }

    override fun doUpdate() {
        val p = product ?: return

        if (p.id != lastProductId) {
            lastProductId = p.id
            ViewUtils.loadImageAsync(imageView, ViewUtils.productImageUrl(p.id))
        }
        if (p.name != lastName) {
            lastName = p.name
            nameLabel.text = p.name ?: ""
        }
        if (p.price != lastPrice) {
            lastPrice = p.price
            priceLabel.text = "R$ ${ViewUtils.formatPrice(p.price)}"
        }
    }
}
