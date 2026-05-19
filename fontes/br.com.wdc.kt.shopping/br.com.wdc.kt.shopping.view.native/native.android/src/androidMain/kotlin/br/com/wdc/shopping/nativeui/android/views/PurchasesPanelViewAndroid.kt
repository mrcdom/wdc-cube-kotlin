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
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.structs.PurchaseInfo

class PurchasesPanelViewAndroid(presenter: PurchasesPanelPresenter) : AbstractViewAndroid<PurchasesPanelPresenter>("purchases-panel-view", presenter) {

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

    companion object {
        private const val ITEM_HEIGHT_DP = 76
    }

    override fun createView(): View {
        val ctx = RootViewAndroid.appContext
        val density = ctx.resources.displayMetrics.density

        return AndroidDom.build(ctx) {
            val root = parent()
            root.setBackgroundColor(ShoppingColors.Surface)

            vStack(configure = {
                (layoutParams as FrameLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }) {
                // Section header
                hStack(configure = {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (4 * density).toInt())
                }) {
                    textView {
                        text = "Compras"
                        textSize = 20f
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

                // Items stack (no scroll - pagination handles overflow)
                stackView = vStack(spacing = (12 * density).toInt(), configure = {
                    setPadding((12 * density).toInt(), (0 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = 0
                        weight = 1f
                    }
                }) {}

                // Pagination bar
                paginationContainer = hStack(configure = {
                    gravity = Gravity.CENTER
                    setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
                    visibility = View.GONE
                }) {
                    prevButton = button("◀") {
                        textSize = 16f
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTextColor(ShoppingColors.Primary)
                        minWidth = 0
                        minimumWidth = 0
                        val padH = (12 * density).toInt()
                        val padV = (4 * density).toInt()
                        setPadding(padH, padV, padH, padV)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                        setOnClickListener { safeAction("prevPage") { presenter.onPageChange(presenter.state.page - 1) } }
                    }
                    pageLabel = textView {
                        textSize = 14f
                        setTextColor(ShoppingColors.OnSurfaceVariant)
                        gravity = Gravity.CENTER
                        val padH = (8 * density).toInt()
                        setPadding(padH, 0, padH, 0)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                    }
                    nextButton = button("▶") {
                        textSize = 16f
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setTextColor(ShoppingColors.Primary)
                        minWidth = 0
                        minimumWidth = 0
                        val padH = (12 * density).toInt()
                        val padV = (4 * density).toInt()
                        setPadding(padH, padV, padH, padV)
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            width = ViewGroup.LayoutParams.WRAP_CONTENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                        }
                        setOnClickListener { safeAction("nextPage") { presenter.onPageChange(presenter.state.page + 1) } }
                    }
                }
            }

            // Empty label
            emptyLabel = textView {
                text = "Nenhuma compra realizada"
                textSize = 16f
                setTextColor(ShoppingColors.OnSurfaceVariant)
                gravity = Gravity.CENTER
                visibility = View.GONE
                (layoutParams as FrameLayout.LayoutParams).gravity = Gravity.CENTER
            }

            purchasesSlot = newListSlot(
                stackView,
                factory = { PurchaseCardView(presenter).also { it.initialize() } },
                updater = { view, item -> view.setState(item); view.forceUpdate() }
            )
        }
    }

    override fun doUpdate() {
        if (lastCapacity < 0) {
            val ctx = RootViewAndroid.appContext
            val density = ctx.resources.displayMetrics.density
            val screenHeightPx = ctx.resources.displayMetrics.heightPixels

            // Determine actual system bars height
            val statusBarId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarPx = if (statusBarId > 0) ctx.resources.getDimensionPixelSize(statusBarId) else (24 * density).toInt()
            val navBarId = ctx.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            val navBarPx = if (navBarId > 0) ctx.resources.getDimensionPixelSize(navBarId) else (48 * density).toInt()

            // Fixed layout elements (in dp): compact header + tabs + section header + separator + pagination
            val fixedDp = Dimens.headerHeightCompact + Dimens.tabsHeight + 40 + 9 + 48
            val fixedPx = (fixedDp * density).toInt()

            // Available pixel height for items
            val availablePx = screenHeightPx - statusBarPx - navBarPx - fixedPx
            val itemHeightPx = (ITEM_HEIGHT_DP * density).toInt()
            val capacity = (availablePx / itemHeightPx).coerceAtLeast(1)

            lastCapacity = capacity
            presenter.onItemSizeCapacityChanged(capacity)
        }

        val state = presenter.state
        val purchases = state.purchases
        val page = state.page
        val totalCount = state.totalCount
        val pageSize = state.pageSize

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
            headerBadge.text = "$totalCount itens"
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

private class PurchaseCardView(presenter: PurchasesPanelPresenter) : AbstractViewAndroid<PurchasesPanelPresenter>("purchase-card", presenter) {

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
            val cornerPx = 8f * density
            val normalBg = ShoppingStyles.roundedBackground(ShoppingColors.Background, cornerPx)
            val pressedBg = ShoppingStyles.roundedBackground(ShoppingColors.SurfaceVariant, cornerPx)
            val statesBg = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressedBg)
                addState(intArrayOf(), normalBg)
            }
            card.background = statesBg
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener { safeAction("receipt") { presenter.onOpenReceipt(purchase?.id) } }

            hStack(configure = {
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                (layoutParams as FrameLayout.LayoutParams).apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }) {
                // Left side: date + items
                vStack(spacing = (4 * density).toInt(), configure = {
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = 0
                        weight = 1f
                    }
                }) {
                    dateLabel = textView {
                        textSize = 12f
                        setTextColor(ShoppingColors.OnSurfaceVariant)
                    }
                    itemsLabel = textView {
                        textSize = 13f
                        setTextColor(ShoppingColors.OnSurfaceVariant)
                        maxLines = 2
                    }
                }
                // Right side: price badge
                frame(configure = {
                    background = ShoppingStyles.roundedBackground(ShoppingColors.PriceBackground, 10 * density)
                    setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
                    (layoutParams as LinearLayout.LayoutParams).apply {
                        width = ViewGroup.LayoutParams.WRAP_CONTENT
                        marginStart = (12 * density).toInt()
                    }
                }) {
                    totalLabel = textView {
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(ShoppingColors.PriceColor)
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
        totalLabel.text = "R$ ${ViewUtils.formatPrice(p.total)}"
        itemsLabel.text = p.items.joinToString(", ")
    }
}
