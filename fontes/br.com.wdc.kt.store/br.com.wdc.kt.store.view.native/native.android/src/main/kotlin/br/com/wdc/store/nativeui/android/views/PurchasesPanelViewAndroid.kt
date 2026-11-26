package br.com.wdc.store.nativeui.android.views

import br.com.wdc.store.design.StoreTokens
import android.graphics.Paint
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
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.presentation.presenter.restricted.home.structs.PurchaseInfo

class PurchasesPanelViewAndroid(app: AndroidNativeRootPresenter, scope: PurchasesPanelScope) : AbstractViewAndroid<PurchasesPanelScope>("purchases-panel-view", app, scope) {

    private lateinit var stackView: LinearLayout
    private lateinit var emptyLabel: TextView
    private lateinit var headerBadge: TextView
    private lateinit var paginationContainer: LinearLayout
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var pageLabel: TextView
    private lateinit var purchasesSlot: ListSlot<PurchaseInfo, PurchaseCardView>

    private var lastPurchases: List<PurchaseInfo>? = null
    private var lastPage = -1
    private var lastTotalCount = -1
    private var lastCapacity = -1
    private var lastStackHeight = -1

    companion object {
        private const val CARD_PADDING_DP = Dimens.spacingMd
        private const val CARD_VSTACK_SPACING_DP = Dimens.spacingXs
        private val CARD_DATE_SP = StoreTokens.FontSize.Caption.toFloat()
        private const val CARD_ITEMS_SP = 13f
        private val CARD_PRICE_SP = StoreTokens.FontSize.Body.toFloat()
        private const val CARD_PRICE_PADDING_V_DP = 6
        private const val LIST_SPACING_DP = Dimens.spacingMd

        /**
         * Computes the card height in pixels deterministically based on device font metrics.
         * All text labels are single-line with ellipsis, so height is always predictable.
         */
        fun computeCardHeightPx(density: Float, scaledDensity: Float): Int {
            // Card vertical padding
            val cardPaddingPx = (CARD_PADDING_DP * 2 * density).toInt()

            // Left side: dateLabel (1 line) + spacing + itemsLabel (1 line)
            val datePaint = Paint().apply { textSize = CARD_DATE_SP * scaledDensity }
            val dateFm = datePaint.fontMetricsInt
            val dateHeight = dateFm.bottom - dateFm.top

            val itemsPaint = Paint().apply { textSize = CARD_ITEMS_SP * scaledDensity }
            val itemsFm = itemsPaint.fontMetricsInt
            val itemsHeight = itemsFm.bottom - itemsFm.top

            val leftHeight = dateHeight + (CARD_VSTACK_SPACING_DP * density).toInt() + itemsHeight

            // Right side: price badge padding + totalLabel (bold, 1 line)
            val pricePaint = Paint().apply {
                textSize = CARD_PRICE_SP * scaledDensity
                typeface = Typeface.DEFAULT_BOLD
            }
            val priceFm = pricePaint.fontMetricsInt
            val priceHeight = priceFm.bottom - priceFm.top
            val rightHeight = (CARD_PRICE_PADDING_V_DP * 2 * density).toInt() + priceHeight

            // hStack with CENTER_VERTICAL: height = max of sides
            val contentHeight = maxOf(leftHeight, rightHeight)

            return cardPaddingPx + contentHeight
        }
    }

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val root = parent()
            root.setBackgroundColor(StoreColors.Surface)

            vStack(configure = {
                (layoutParams as FrameLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }) {
                // Section header
                hStack(configure = {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((Dimens.spacingLg * density).toInt(), (Dimens.spacingMd * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingXs * density).toInt())
                }) {
                    textView {
                        text = "Purchases"
                        textSize = StoreTokens.FontSize.Title.toFloat()
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(StoreColors.OnSurface)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            weight = 1f
                            width = 0
                        }
                    }
                    headerBadge = textView {
                        textSize = StoreTokens.FontSize.Caption.toFloat()
                        setTextColor(StoreColors.OnPrimaryContainer)
                        gravity = Gravity.CENTER
                        background = StoreStyles.roundedBackground(StoreColors.SecondaryContainer, Dimens.radiusSm * density)
                        val padH = (Dimens.spacingLg * density).toInt()
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
                    setBackgroundColor(StoreColors.SurfaceVariant)
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        height = (1 * density).toInt().coerceAtLeast(1)
                        topMargin = (Dimens.spacingSm * density).toInt()
                        bottomMargin = (Dimens.spacingMd * density).toInt()
                        marginStart = (Dimens.spacingLg * density).toInt()
                        marginEnd = (Dimens.spacingLg * density).toInt()
                    }
                })

                // Items stack (no scroll - pagination handles overflow)
                stackView = vStack(spacing = (Dimens.spacingMd * density).toInt(), configure = {
                    setPadding((Dimens.spacingMd * density).toInt(), (0 * density).toInt(), (Dimens.spacingMd * density).toInt(), (Dimens.spacingSm * density).toInt())
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = 0
                        weight = 1f
                    }
                }) {}

                // Pagination bar
                paginationContainer = hStack(configure = {
                    gravity = Gravity.CENTER
                    setPadding((Dimens.spacingLg * density).toInt(), (Dimens.spacingSm * density).toInt(), (Dimens.spacingLg * density).toInt(), (Dimens.spacingSm * density).toInt())
                    visibility = View.INVISIBLE
                }) {
                    prevButton = button("◀") {
                        textSize = StoreTokens.FontSize.BodyLarge.toFloat()
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTextColor(StoreColors.Primary)
                        minWidth = 0
                        minimumWidth = 0
                        val padH = (Dimens.spacingMd * density).toInt()
                        val padV = (Dimens.spacingXs * density).toInt()
                        setPadding(padH, padV, padH, padV)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                        setOnClickListener { safeAction("prevPage") { scope.onPageChange(scope.page - 1) } }
                    }
                    pageLabel = textView {
                        textSize = StoreTokens.FontSize.Body.toFloat()
                        setTextColor(StoreColors.OnSurfaceVariant)
                        gravity = Gravity.CENTER
                        val padH = (Dimens.spacingSm * density).toInt()
                        setPadding(padH, 0, padH, 0)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }
                    nextButton = button("▶") {
                        textSize = StoreTokens.FontSize.BodyLarge.toFloat()
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTextColor(StoreColors.Primary)
                        minWidth = 0
                        minimumWidth = 0
                        val padH = (Dimens.spacingMd * density).toInt()
                        val padV = (Dimens.spacingXs * density).toInt()
                        setPadding(padH, padV, padH, padV)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                        setOnClickListener { safeAction("nextPage") { scope.onPageChange(scope.page + 1) } }
                    }
                }
            }

            // Empty label
            emptyLabel = textView {
                text = "No purchases yet"
                textSize = StoreTokens.FontSize.BodyLarge.toFloat()
                setTextColor(StoreColors.OnSurfaceVariant)
                gravity = Gravity.CENTER
                visibility = View.GONE
                (layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER
            }

            purchasesSlot = newListSlot(
                stackView,
                factory = { PurchaseCardView(app, scope).also { it.initialize() } },
                updater = { view, item -> view.setState(item); view.redraw() }
            )

            // Recalculate capacity when stackView height changes (e.g. orientation change)
            stackView.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                val newHeight = bottom - top
                if (lastStackHeight > 0 && newHeight != lastStackHeight && newHeight > 0) {
                    lastStackHeight = newHeight
                    stackView.post {
                        val ctx = RootViewAndroid.appContext
                        val metrics = ctx.resources.displayMetrics
                        val availablePx = newHeight - stackView.paddingTop - stackView.paddingBottom
                        val cardHeightPx = computeCardHeightPx(metrics.density, metrics.scaledDensity)
                        val spacing = (LIST_SPACING_DP * metrics.density).toInt()
                        val newCapacity = ((availablePx + spacing) / (cardHeightPx + spacing)).coerceAtLeast(1)
                        if (newCapacity != lastCapacity) {
                            lastCapacity = newCapacity
                            lastPurchases = null
                            lastPage = -1
                            lastTotalCount = -1
                            safeAction("capacityChanged") { scope.onItemSizeCapacityChanged(newCapacity) }
                        }
                    }
                }
            }
        }
    }

    override fun doUpdate() {
        if (lastCapacity < 0 && stackView.height > 0) {
            val ctx = RootViewAndroid.appContext
            val metrics = ctx.resources.displayMetrics

            val availablePx = stackView.height - stackView.paddingTop - stackView.paddingBottom
            val cardHeightPx = computeCardHeightPx(metrics.density, metrics.scaledDensity)
            val spacing = (LIST_SPACING_DP * metrics.density).toInt()
            val capacity = ((availablePx + spacing) / (cardHeightPx + spacing)).coerceAtLeast(1)

            lastStackHeight = stackView.height
            lastCapacity = capacity
            safeAction("capacityInit") { scope.onItemSizeCapacityChanged(capacity) }
        } else if (lastCapacity < 0) {
            // stackView not yet laid out, schedule recalculation
            stackView.post { redraw() }
            return
        }

        val purchases = scope.purchases
        val page = scope.page
        val totalCount = scope.totalCount
        val pageSize = scope.pageSize

        if (purchases !== lastPurchases) {
            lastPurchases = purchases

            if (purchases.isEmpty()) {
                emptyLabel.visibility = View.VISIBLE
                stackView.visibility = View.GONE
                purchasesSlot.sync(emptyList())
            } else {
                emptyLabel.visibility = View.GONE
                stackView.visibility = View.VISIBLE
                purchasesSlot.sync(purchases)
            }
        }

        if (totalCount != lastTotalCount) {
            lastTotalCount = totalCount
            headerBadge.text = "$totalCount ${if (totalCount == 1) "item" else "items"}"
        }

        if (page != lastPage || totalCount != lastTotalCount) {
            lastPage = page
            val totalPages = if (pageSize > 0) ((totalCount + pageSize - 1) / pageSize) else 1
            if (totalPages > 1) {
                paginationContainer.visibility = View.VISIBLE
                pageLabel.text = "${page + 1} / $totalPages"
                prevButton.isEnabled = page > 0
                prevButton.alpha = if (page > 0) 1f else 0.4f
                nextButton.isEnabled = page < totalPages - 1
                nextButton.alpha = if (page < totalPages - 1) 1f else 0.4f
            } else {
                paginationContainer.visibility = View.GONE
            }
        }
    }
}

// --- Purchase Card sub-view ---

private class PurchaseCardView(app: AndroidNativeRootPresenter, scope: PurchasesPanelScope) : AbstractViewAndroid<PurchasesPanelScope>("purchase-card", app, scope) {

    private lateinit var dateLabel: TextView
    private lateinit var totalLabel: TextView
    private lateinit var itemsLabel: TextView

    var purchase: PurchaseInfo? = null
        private set

    private var lastPurchaseId: Long = -1

    fun setState(item: PurchaseInfo) {
        this.purchase = item
    }

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val card = parent()
            val cornerPx = Dimens.radiusSm * density
            val normalBg = StoreStyles.roundedBackground(StoreColors.Background, cornerPx)
            val pressedBg = StoreStyles.roundedBackground(StoreColors.SurfaceVariant, cornerPx)
            val statesBg = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
                addState(intArrayOf(), normalBg)
            }
            card.background = statesBg
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener { safeAction("receipt") { scope.onOpenReceipt(purchase?.id) } }

            hStack(configure = {
                gravity = Gravity.CENTER_VERTICAL
                setPadding((Dimens.spacingMd * density).toInt(), (Dimens.spacingMd * density).toInt(), (Dimens.spacingMd * density).toInt(), (Dimens.spacingMd * density).toInt())
                (layoutParams as FrameLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }) {
                // Left side: date + items
                vStack(spacing = (Dimens.spacingXs * density).toInt(), configure = {
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = 0
                        weight = 1f
                    }
                }) {
                    dateLabel = textView {
                        textSize = StoreTokens.FontSize.Caption.toFloat()
                        setTextColor(StoreColors.OnSurfaceVariant)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    itemsLabel = textView {
                        textSize = 13f
                        setTextColor(StoreColors.OnSurfaceVariant)
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                }
                // Right side: price badge
                frame(configure = {
                    background = StoreStyles.roundedBackground(StoreColors.PriceBackground, 10 * density)
                    setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                        marginStart = (Dimens.spacingMd * density).toInt()
                    }
                }) {
                    totalLabel = textView {
                        textSize = StoreTokens.FontSize.Body.toFloat()
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(StoreColors.PriceColor)
                        (layoutParams as FrameLayout.LayoutParams).width = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                }
            }
        }
    }

    override fun doUpdate() {
        val p = purchase
        if (p == null) return
        if (p.id == lastPurchaseId) return
        lastPurchaseId = p.id

        dateLabel.text = ViewUtils.formatDate(p.date)
        totalLabel.text = "\$${ViewUtils.formatPrice(p.total)}"
        itemsLabel.text = p.items.joinToString(", ")
    }
}
