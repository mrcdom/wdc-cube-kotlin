package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.shopping.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.toolkit.UIKitDom
import br.com.wdc.shopping.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.shopping.nativeui.ios.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.theme.UIK
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.structs.PurchaseInfo
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.UIKit.*
import platform.objc.sel_registerName
import platform.darwin.NSObject
import kotlin.math.ceil
import kotlin.math.max

/**
 * Purchases panel — paginated list of past purchases with prev/next navigation.
 * Uses ListSlot with PurchaseCardView sub-views for efficient recycling.
 */
@OptIn(ExperimentalForeignApi::class)
class PurchasesPanelViewIos(presenter: PurchasesPanelPresenter) : AbstractViewIos<PurchasesPanelPresenter>("purchases-panel-view", presenter) {

    private lateinit var scrollView: UIScrollView
    private lateinit var stackView: UIStackView
    private lateinit var emptyLabel: UILabel
    private lateinit var paginationContainer: UIView
    private lateinit var pageLabel: UILabel
    private lateinit var prevButton: UIButton
    private lateinit var nextButton: UIButton
    private lateinit var headerBadge: UILabel
    private lateinit var purchasesSlot: ListSlot<PurchaseInfo, PurchaseCardView>

    private val actions = PurchasesPanelActions(this).also { retainForGC(it) }

    // Guards
    private var lastPurchases: List<PurchaseInfo>? = null
    private var lastPage: Int = -1
    private var lastTotalCount: Int = -1
    private var lastCapacity: Int = 0

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = ShoppingColors.Background

        // Section header
        val headerContainer = view(configure = {
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor, 12.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0),
                heightAnchor.constraintEqualToConstant(28.0)
            ))
        }) {
            label {
                text = "Compras"
                font = UIFont.boldSystemFontOfSize(17.0)
                textColor = ShoppingColors.OnSurface
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                ))
            }
            headerBadge = label {
                font = UIFont.systemFontOfSize(12.0, UIFontWeightMedium)
                textColor = ShoppingColors.OnPrimaryContainer
                backgroundColor = ShoppingColors.SecondaryContainer
                textAlignment = UIK.TextAlignCenter
                layer.cornerRadius = 10.0
                clipsToBounds = true
                NSLayoutConstraint.activateConstraints(listOf(
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                    heightAnchor.constraintEqualToConstant(20.0),
                    widthAnchor.constraintGreaterThanOrEqualToConstant(60.0)
                ))
            }
        }

        // Separator
        val headerSeparator = view(configure = {
            backgroundColor = ShoppingColors.SurfaceVariant
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(headerContainer.bottomAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0),
                heightAnchor.constraintEqualToConstant(1.0)
            ))
        })

        // Pagination at bottom (declared before scrollView so scrollView can reference it)
        paginationContainer = view(configure = {
            backgroundColor = ShoppingColors.Background
            NSLayoutConstraint.activateConstraints(listOf(
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor),
                heightAnchor.constraintEqualToConstant(44.0)
            ))
        }) {
            prevButton = button("◀ Anterior") {
                setTitleColor(ShoppingColors.Primary, forState = UIControlStateNormal)
                setTitleColor(ShoppingColors.OnSurfaceVariant, forState = UIControlStateDisabled)
                titleLabel?.font = UIFont.systemFontOfSize(14.0)
                addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onPrev")))
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                    topAnchor.constraintEqualToAnchor(parent().topAnchor),
                    bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor)
                ))
            }

            pageLabel = label {
                font = UIFont.systemFontOfSize(14.0)
                textColor = ShoppingColors.OnSurface
                textAlignment = UIK.TextAlignCenter
            }
            center(pageLabel)

            nextButton = button("Próxima ▶") {
                setTitleColor(ShoppingColors.Primary, forState = UIControlStateNormal)
                setTitleColor(ShoppingColors.OnSurfaceVariant, forState = UIControlStateDisabled)
                titleLabel?.font = UIFont.systemFontOfSize(14.0)
                addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onNext")))
                NSLayoutConstraint.activateConstraints(listOf(
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0),
                    topAnchor.constraintEqualToAnchor(parent().topAnchor),
                    bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor)
                ))
            }
        }

        scrollView = scrollView(configure = {
            showsVerticalScrollIndicator = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(headerSeparator.bottomAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(paginationContainer.topAnchor)
            ))
        }) {
            stackView = vStack(spacing = 10.0) {}
            NSLayoutConstraint.activateConstraints(listOf(
                stackView.topAnchor.constraintEqualToAnchor(parent().topAnchor, 8.0),
                stackView.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                stackView.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0),
                stackView.bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -8.0),
                stackView.widthAnchor.constraintEqualToAnchor(parent().widthAnchor, constant = -32.0)
            ))
        }

        emptyLabel = label {
            text = "Nenhuma compra realizada"
            font = UIFont.systemFontOfSize(16.0)
            textColor = ShoppingColors.OnSurfaceVariant
            textAlignment = UIK.TextAlignCenter
            hidden = true
        }
        center(emptyLabel)

        purchasesSlot = newListSlot(
            stackView,
            factory = { PurchaseCardView(presenter).also { it.initialize() } },
            updater = { view, item -> view.setState(item); view.forceUpdate() }
        )
    }

    override fun doUpdate() {
        val state = presenter.state

        // Calculate capacity based on available scroll height
        val availableHeight = scrollView.frame.useContents { size.height }
        if (availableHeight > 0.0) {
            val cardHeight = 90.0 // ~80pt card + 10pt spacing
            val capacity = max(1, (availableHeight / cardHeight).toInt())
            if (capacity != lastCapacity) {
                lastCapacity = capacity
                presenter.onItemSizeCapacityChanged(capacity)
                return // presenter will trigger a new update with data
            }
        } else {
            // Layout not ready yet — schedule a re-check
            platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                update()
            }
            return
        }
        val purchases = state.purchases
        val page = state.page
        val totalCount = state.totalCount
        val pageSize = if (state.pageSize > 0) state.pageSize else 5
        val totalPages = max(1, ceil(totalCount.toDouble() / pageSize).toInt())

        // Sync list with recycling
        if (purchases !== lastPurchases) {
            lastPurchases = purchases

            if (purchases.isEmpty() && totalCount == 0) {
                emptyLabel.hidden = false
                scrollView.hidden = true
                purchasesSlot.sync(emptyList())
            } else {
                emptyLabel.hidden = true
                scrollView.hidden = false
                purchasesSlot.sync(purchases)
            }
        }

        // Pagination (guard)
        if (page != lastPage || totalCount != lastTotalCount) {
            lastPage = page
            lastTotalCount = totalCount
            paginationContainer.hidden = totalPages <= 1
            pageLabel.text = "${page + 1} / $totalPages"
            prevButton.enabled = page > 0
            nextButton.enabled = page < totalPages - 1
            headerBadge.text = "  $totalCount compras  "
        }
    }

    internal fun onPrevPage() {
        safeAction("prevPage") {
            val state = presenter.state
            if (state.page > 0) {
                presenter.onPageChange(state.page - 1)
            }
        }
    }

    internal fun onNextPage() {
        safeAction("nextPage") {
            presenter.onPageChange(presenter.state.page + 1)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PurchasesPanelActions(private val view: PurchasesPanelViewIos) : NSObject() {

    @ObjCAction
    fun onPrev() {
        view.onPrevPage()
    }

    @ObjCAction
    fun onNext() {
        view.onNextPage()
    }
}

// --- Item sub-view: PurchaseCardView ---

@OptIn(ExperimentalForeignApi::class)
private class PurchaseCardView(presenter: PurchasesPanelPresenter) : AbstractViewIos<PurchasesPanelPresenter>("purchase-card", presenter) {

    private lateinit var dateLabel: UILabel
    private lateinit var totalLabel: UILabel
    private lateinit var itemsLabel: UILabel

    var purchase: PurchaseInfo? = null
        private set

    // Retained action target (UIKit uses weak ref to gesture target)
    private lateinit var tapAction: PurchaseCardTapAction

    // Guards
    private var lastPurchaseId: Long = -1
    private var lastDate: String? = null
    private var lastTotal: Double = -1.0
    private var lastItems: String? = null

    fun setState(item: PurchaseInfo) {
        this.purchase = item
    }

    override fun createView(): UIView = UIKitDom.build {
        val card = parent()
        card.backgroundColor = ShoppingColors.SurfaceVariant50
        card.layer.cornerRadius = 8.0
        card.userInteractionEnabled = true

        vStack(spacing = 4.0, configure = {
            layoutMarginsRelativeArrangement = true
            layoutMargins = UIEdgeInsetsMake(12.0, 12.0, 12.0, 12.0)
        }) {
            dateLabel = label {
                font = UIFont.systemFontOfSize(12.0)
                textColor = ShoppingColors.OnSurfaceVariant
            }
            totalLabel = label {
                font = UIFont.boldSystemFontOfSize(15.0)
                textColor = ShoppingColors.PriceColor
            }
            itemsLabel = label {
                font = UIFont.systemFontOfSize(13.0)
                textColor = ShoppingColors.OnSurfaceVariant
                numberOfLines = 1
            }
        }.also { pin(it) }

        // Tap gesture
        tapAction = PurchaseCardTapAction(this@PurchaseCardView).also { retainForGC(it) }
        card.addGestureRecognizer(tapAction.gesture)
    }

    override fun doUpdate() {
        val p = purchase ?: return

        if (p.id != lastPurchaseId) {
            lastPurchaseId = p.id
            // Reset all guards when item identity changes
            lastDate = null
            lastTotal = -1.0
            lastItems = null
        }

        val dateStr = ViewUtils.formatDate(p.date)
        if (dateStr != lastDate) {
            lastDate = dateStr
            dateLabel.text = dateStr
        }

        if (p.total != lastTotal) {
            lastTotal = p.total
            totalLabel.text = "R$ ${ViewUtils.formatPrice(p.total)}"
        }

        val itemsText = p.items.take(3).joinToString(", ")
        if (itemsText != lastItems) {
            lastItems = itemsText
            itemsLabel.text = itemsText
            itemsLabel.hidden = itemsText.isBlank()
        }
    }

    fun onTap() {
        val id = purchase?.id ?: return
        presenter.onOpenReceipt(id)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PurchaseCardTapAction(private val cardView: PurchaseCardView) : NSObject() {

    val gesture: UITapGestureRecognizer = UITapGestureRecognizer(target = this, action = sel_registerName("onTap"))

    @ObjCAction
    fun onTap() {
        cardView.onTap()
    }
}
