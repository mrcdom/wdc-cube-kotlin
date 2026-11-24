package br.com.wdc.store.nativeui.ios.views

import br.com.wdc.store.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.store.nativeui.ios.toolkit.FlowLayoutView
import br.com.wdc.store.nativeui.ios.toolkit.UIKitDom
import br.com.wdc.store.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.store.nativeui.ios.theme.StoreColors
import br.com.wdc.store.nativeui.ios.theme.UIK
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.nativeui.ios.IosNativeRootPresenter
import br.com.wdc.store.presentation.presenter.restricted.products.structs.ProductInfo
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.UIKit.*
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Products panel — scrollable grid/list of product cards.
 * Each card shows image, name, price; tapping opens ProductView.
 *
 * Uses ListSlot with ProductCardView sub-views for efficient recycling.
 */
@OptIn(ExperimentalForeignApi::class)
class ProductsPanelViewIos(app: IosNativeRootPresenter, scope: ProductsPanelScope) : AbstractViewIos<ProductsPanelScope>("products-panel-view", app, scope) {

    private lateinit var scrollView: UIScrollView
    private lateinit var stackView: FlowLayoutView
    private lateinit var emptyLabel: UILabel
    private lateinit var headerTitle: UILabel
    private lateinit var headerBadge: UILabel
    private lateinit var productsSlot: ListSlot<ProductInfo, ProductCardView>

    private var lastProducts: List<ProductInfo>? = null
    private var lastCount: Int = -1

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = StoreColors.Background

        // Section header
        val headerContainer = view(configure = {
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor, 12.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0),
                heightAnchor.constraintEqualToConstant(28.0)
            ))
        }) {
            headerTitle = label {
                text = "Products"
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
                layer.cornerRadius = 8.0
                clipsToBounds = true
                NSLayoutConstraint.activateConstraints(listOf(
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                    heightAnchor.constraintEqualToConstant(28.0)
                ))
            }) {
                headerBadge = label {
                    font = UIFont.systemFontOfSize(12.0)
                    textColor = StoreColors.OnPrimaryContainer
                    textAlignment = UIK.TextAlignCenter
                    NSLayoutConstraint.activateConstraints(listOf(
                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0),
                        centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                    ))
                }
            }
        }

        // Separator
        view(configure = {
            backgroundColor = StoreColors.SurfaceVariant
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(headerContainer.bottomAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0),
                heightAnchor.constraintEqualToConstant(1.0)
            ))
        })

        scrollView = scrollView(configure = {
            showsVerticalScrollIndicator = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(headerContainer.bottomAnchor, 20.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        }) {
            stackView = flowLayout(minChildWidth = 160.0, horizontalSpacing = 12.0, verticalSpacing = 12.0) {}
            NSLayoutConstraint.activateConstraints(listOf(
                stackView.topAnchor.constraintEqualToAnchor(parent().topAnchor, 8.0),
                stackView.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 12.0),
                stackView.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -12.0),
                stackView.bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -8.0),
                stackView.widthAnchor.constraintEqualToAnchor(parent().widthAnchor, constant = -24.0)
            ))
        }

        emptyLabel = label {
            text = "No products available"
            font = UIFont.systemFontOfSize(16.0)
            textColor = StoreColors.OnSurfaceVariant
            textAlignment = UIK.TextAlignCenter
            hidden = true
        }
        center(emptyLabel)

        productsSlot = newListSlot(
            stackView,
            factory = { ProductCardView(app, scope).also { it.initialize() } },
            updater = { view, item -> view.setState(item); view.redraw() }
        )
    }

    override fun doUpdate() {
        val products = scope.products
        if (products === lastProducts) return
        lastProducts = products

        if (products.isNullOrEmpty()) {
            emptyLabel.hidden = false
            scrollView.hidden = true
            productsSlot.sync(emptyList())
            if (lastCount != 0) {
                lastCount = 0
                headerBadge.text = "0 items"
            }
        } else {
            emptyLabel.hidden = true
            scrollView.hidden = false
            productsSlot.sync(products)
            val count = products.size
            if (count != lastCount) {
                lastCount = count
                headerBadge.text = "$count ${if (count == 1) "item" else "items"}"
            }
        }
    }
}

// --- Item sub-view: ProductCardView ---

@OptIn(ExperimentalForeignApi::class)
private class ProductCardView(app: IosNativeRootPresenter, scope: ProductsPanelScope) : AbstractViewIos<ProductsPanelScope>("product-card", app, scope) {

    private lateinit var imageView: UIImageView
    private lateinit var nameLabel: UILabel
    private lateinit var priceLabel: UILabel

    var product: ProductInfo? = null
        private set

    // Retained action target (UIKit uses weak ref to gesture target)
    private lateinit var tapAction: ProductCardTapAction

    // Guards
    private var lastProductId: Long = -1
    private var lastName: String? = null
    private var lastPrice: Double = -1.0

    fun setState(item: ProductInfo) {
        this.product = item
    }

    override fun createView(): UIView = UIKitDom.build {
        val card = parent()
        card.backgroundColor = UIColor.whiteColor
        card.layer.cornerRadius = 8.0
        card.clipsToBounds = true
        card.userInteractionEnabled = true

        vStack(spacing = 0.0, configure = { alignment = UIK.StackAlignFill }) {
            // Product image — full width, fixed height
            imageView = imageView {
                contentMode = UIK.ContentModeScaleAspectFit
                backgroundColor = StoreColors.SurfaceVariant
                clipsToBounds = true
                heightAnchor.constraintEqualToConstant(140.0).active = true
            }
            // Content area below image
            vStack(spacing = 6.0, configure = {
                layoutMarginsRelativeArrangement = true
                layoutMargins = UIEdgeInsetsMake(12.0, 12.0, 12.0, 12.0)
            }) {
                nameLabel = label {
                    font = UIFont.boldSystemFontOfSize(15.0)
                    textColor = StoreColors.OnSurface
                    numberOfLines = 2
                }
                // Price badge
                view(configure = {
                    backgroundColor = StoreColors.PriceBackground
                    layer.cornerRadius = 8.0
                    NSLayoutConstraint.activateConstraints(listOf(
                        heightAnchor.constraintEqualToConstant(28.0)
                    ))
                }) {
                    priceLabel = label {
                        font = UIFont.boldSystemFontOfSize(14.0)
                        textColor = StoreColors.PriceColor
                        NSLayoutConstraint.activateConstraints(listOf(
                            centerXAnchor.constraintEqualToAnchor(parent().centerXAnchor),
                            centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                            leadingAnchor.constraintGreaterThanOrEqualToAnchor(parent().leadingAnchor, 12.0),
                            trailingAnchor.constraintLessThanOrEqualToAnchor(parent().trailingAnchor, -12.0)
                        ))
                    }
                }
            }
        }.also { pin(it) }

        // Tap gesture with visual feedback
        tapAction = ProductCardTapAction(this@ProductCardView, card).also { retainForGC(it) }
        card.addGestureRecognizer(tapAction.gesture)
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
            priceLabel.text = "\$${ViewUtils.formatPrice(p.price)}"
        }
    }

    fun onTap() {
        val id = product?.id ?: return
        safeAction("openProduct") { scope.onOpenProduct(id) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ProductCardTapAction(private val cardView: ProductCardView, private val card: UIView) : NSObject() {

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
                    card.backgroundColor = UIColor.whiteColor
                }
                cardView.onTap()
            }
            UIGestureRecognizerStateCancelled,
            UIGestureRecognizerStateFailed -> {
                UIView.animateWithDuration(0.2) {
                    card.backgroundColor = UIColor.whiteColor
                }
            }
            else -> {}
        }
    }
}
