package br.com.wdc.store.nativeui.ios.views

import br.com.wdc.store.i18n.*
import br.com.wdc.store.design.StoreTokens.FontSize
import br.com.wdc.store.design.StoreTokens.Radius
import br.com.wdc.store.design.StoreTokens.Spacing
import br.com.wdc.store.nativeui.ios.theme.pt
import br.com.wdc.store.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.store.nativeui.ios.toolkit.UIKitDom
import br.com.wdc.store.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.store.nativeui.ios.theme.StoreColors
import br.com.wdc.store.nativeui.ios.theme.UIK
import br.com.wdc.store.presentation.presenter.restricted.home.PurchasesPanelScope
import br.com.wdc.store.nativeui.ios.IosNativeRootPresenter
import br.com.wdc.store.presentation.presenter.restricted.home.structs.PurchaseInfo
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
class PurchasesPanelViewIos(app: IosNativeRootPresenter, scope: PurchasesPanelScope) : AbstractViewIos<PurchasesPanelScope>("purchases-panel-view", app, scope) {

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
        root.backgroundColor = StoreColors.Background

        // Section header
        val headerContainer = view(configure = {
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor, Spacing.Md.pt),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, Spacing.Lg.pt),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -Spacing.Lg.pt),
                heightAnchor.constraintEqualToConstant(28.0)
            ))
        }) {
            label {
                text = messages.purchasesTitle()
                font = UIFont.boldSystemFontOfSize(17.0)
                textColor = StoreColors.OnSurface
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                ))
            }
            // Chip background
            view(configure = {
                backgroundColor = StoreColors.SecondaryContainer
                layer.cornerRadius = Radius.Sm.pt
                clipsToBounds = true
                NSLayoutConstraint.activateConstraints(listOf(
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                    heightAnchor.constraintEqualToConstant(28.0)
                ))
            }) {
                headerBadge = label {
                    font = UIFont.systemFontOfSize(FontSize.Caption.pt)
                    textColor = StoreColors.OnPrimaryContainer
                    textAlignment = UIK.TextAlignCenter
                    NSLayoutConstraint.activateConstraints(listOf(
                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, Spacing.Lg.pt),
                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -Spacing.Lg.pt),
                        centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                    ))
                }
            }
        }

        // Separator
        val headerSeparator = view(configure = {
            backgroundColor = StoreColors.SurfaceVariant
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(headerContainer.bottomAnchor, Spacing.Sm.pt),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, Spacing.Lg.pt),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -Spacing.Lg.pt),
                heightAnchor.constraintEqualToConstant(1.0)
            ))
        })

        // Pagination at bottom (declared before scrollView so scrollView can reference it)
        paginationContainer = view(configure = {
            backgroundColor = StoreColors.Background
            NSLayoutConstraint.activateConstraints(listOf(
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor),
                heightAnchor.constraintEqualToConstant(44.0)
            ))
        }) {
            prevButton = button("◀") {
                setTitleColor(StoreColors.Primary, forState = UIControlStateNormal)
                setTitleColor(StoreColors.OnSurfaceVariant, forState = UIControlStateDisabled)
                titleLabel?.font = UIFont.systemFontOfSize(FontSize.BodyLarge.pt)
                addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onPrev")))
            }

            pageLabel = label {
                font = UIFont.systemFontOfSize(FontSize.Body.pt)
                textColor = StoreColors.OnSurfaceVariant
                textAlignment = UIK.TextAlignCenter
            }
            center(pageLabel)

            nextButton = button("▶") {
                setTitleColor(StoreColors.Primary, forState = UIControlStateNormal)
                setTitleColor(StoreColors.OnSurfaceVariant, forState = UIControlStateDisabled)
                titleLabel?.font = UIFont.systemFontOfSize(FontSize.BodyLarge.pt)
                addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onNext")))
            }

            // Layout: prev - 12pt - pageLabel - 12pt - next, centered
            NSLayoutConstraint.activateConstraints(listOf(
                prevButton.trailingAnchor.constraintEqualToAnchor(pageLabel.leadingAnchor, -Spacing.Md.pt),
                prevButton.centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                nextButton.leadingAnchor.constraintEqualToAnchor(pageLabel.trailingAnchor, Spacing.Md.pt),
                nextButton.centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
            ))
        }

        scrollView = scrollView(configure = {
            showsVerticalScrollIndicator = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(headerSeparator.bottomAnchor, Spacing.Sm.pt),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(paginationContainer.topAnchor)
            ))
        }) {
            stackView = vStack(spacing = 10.0) {}
            NSLayoutConstraint.activateConstraints(listOf(
                stackView.topAnchor.constraintEqualToAnchor(parent().topAnchor, Spacing.Sm.pt),
                stackView.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, Spacing.Lg.pt),
                stackView.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -Spacing.Lg.pt),
                stackView.bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -Spacing.Sm.pt),
                stackView.widthAnchor.constraintEqualToAnchor(parent().widthAnchor, constant = -32.0)
            ))
        }

        emptyLabel = label {
            text = messages.purchasesEmpty()
            font = UIFont.systemFontOfSize(FontSize.BodyLarge.pt)
            textColor = StoreColors.OnSurfaceVariant
            textAlignment = UIK.TextAlignCenter
            hidden = true
        }
        center(emptyLabel)

        purchasesSlot = newListSlot(
            stackView,
            factory = { PurchaseCardView(app, scope).also { it.initialize() } },
            updater = { view, item -> view.setState(item); view.redraw() }
        )
    }

    override fun doUpdate() {

        // Calculate capacity based on available scroll height
        val availableHeight = scrollView.frame.useContents { size.height }
        if (availableHeight > 0.0) {
            val cardHeight = 90.0 // ~80pt card + 10pt spacing
            val capacity = max(1, (availableHeight / cardHeight).toInt())
            if (capacity != lastCapacity) {
                lastCapacity = capacity
                safeAction("capacityChanged") { scope.onItemSizeCapacityChanged(capacity) }
                return // presenter will trigger a new update with data
            }
        } else {
            // Layout not ready yet — schedule a re-check
            platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                update()
            }
            return
        }
        val purchases = scope.purchases
        val page = scope.page
        val totalCount = scope.totalCount
        val pageSize = if (scope.pageSize > 0) scope.pageSize else 5
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
            pageLabel.text = messages.purchasesPageOf(
                page = page + 1,
                pages = totalPages,
            )
            prevButton.enabled = page > 0
            nextButton.enabled = page < totalPages - 1
            headerBadge.text = if (totalCount == 1) messages.commonItemCountOne(count = totalCount) else messages.commonItemCountOther(count = totalCount)
        }
    }

    internal fun onPrevPage() {
        safeAction("prevPage") {
            if (scope.page > 0) {
                scope.onPageChange(scope.page - 1)
            }
        }
    }

    internal fun onNextPage() {
        safeAction("nextPage") {
            scope.onPageChange(scope.page + 1)
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
private class PurchaseCardView(app: IosNativeRootPresenter, scope: PurchasesPanelScope) : AbstractViewIos<PurchasesPanelScope>("purchase-card", app, scope) {

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
        card.backgroundColor = StoreColors.SurfaceVariant50
        card.layer.cornerRadius = Radius.Sm.pt
        card.userInteractionEnabled = true

        vStack(spacing = Spacing.Xs.pt, configure = {
            layoutMarginsRelativeArrangement = true
            layoutMargins = UIEdgeInsetsMake(Spacing.Md.pt, Spacing.Md.pt, Spacing.Md.pt, Spacing.Md.pt)
        }) {
            dateLabel = label {
                font = UIFont.systemFontOfSize(FontSize.Caption.pt)
                textColor = StoreColors.OnSurfaceVariant
            }
            totalLabel = label {
                font = UIFont.boldSystemFontOfSize(15.0)
                textColor = StoreColors.PriceColor
            }
            itemsLabel = label {
                font = UIFont.systemFontOfSize(13.0)
                textColor = StoreColors.OnSurfaceVariant
                numberOfLines = 1
            }
        }.also { pin(it) }

        // Tap gesture with visual feedback
        tapAction = PurchaseCardTapAction(this@PurchaseCardView, card).also { retainForGC(it) }
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

        val dateStr = ViewUtils.formatDate(
            epochMs = p.date,
            messages = messages,
        )
        if (dateStr != lastDate) {
            lastDate = dateStr
            dateLabel.text = dateStr
        }

        if (p.total != lastTotal) {
            lastTotal = p.total
            totalLabel.text = "\$${ViewUtils.formatPrice(p.total)}"
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
        safeAction("openReceipt") { scope.onOpenReceipt(id) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PurchaseCardTapAction(private val cardView: PurchaseCardView, private val card: UIView) : NSObject() {

    val gesture: UILongPressGestureRecognizer = UILongPressGestureRecognizer(target = this, action = sel_registerName("onGesture")).apply {
        minimumPressDuration = 0.0
    }

    @ObjCAction
    fun onGesture() {
        when (gesture.state) {
            UIGestureRecognizerStateBegan -> {
                card.backgroundColor = StoreColors.SurfaceVariant
            }
            UIGestureRecognizerStateEnded -> {
                UIView.animateWithDuration(0.2) {
                    card.backgroundColor = StoreColors.SurfaceVariant50
                }
                cardView.onTap()
            }
            UIGestureRecognizerStateCancelled,
            UIGestureRecognizerStateFailed -> {
                UIView.animateWithDuration(0.2) {
                    card.backgroundColor = StoreColors.SurfaceVariant50
                }
            }
            else -> {}
        }
    }
}
