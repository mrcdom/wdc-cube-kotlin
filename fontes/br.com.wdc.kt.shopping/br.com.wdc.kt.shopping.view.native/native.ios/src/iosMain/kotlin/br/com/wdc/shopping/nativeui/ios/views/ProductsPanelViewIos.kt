package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.shopping.nativeui.ios.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.UIK
import br.com.wdc.shopping.nativeui.ios.UIKitDom
import br.com.wdc.shopping.nativeui.ios.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.ViewUtils
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.structs.ProductInfo
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
class ProductsPanelViewIos(presenter: ProductsPanelPresenter) : AbstractViewIos<ProductsPanelPresenter>("products-panel-view", presenter) {

    private lateinit var scrollView: UIScrollView
    private lateinit var stackView: UIStackView
    private lateinit var emptyLabel: UILabel
    private lateinit var productsSlot: ListSlot<ProductInfo, ProductCardView>

    private var lastProducts: List<ProductInfo>? = null

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = ShoppingColors.Background

        scrollView = scrollView(configure = {
            showsVerticalScrollIndicator = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        }) {
            stackView = vStack(spacing = 12.0) {}
            NSLayoutConstraint.activateConstraints(listOf(
                stackView.topAnchor.constraintEqualToAnchor(parent().topAnchor, 8.0),
                stackView.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                stackView.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0),
                stackView.bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -8.0),
                stackView.widthAnchor.constraintEqualToAnchor(parent().widthAnchor, constant = -32.0)
            ))
        }

        emptyLabel = label {
            text = "Nenhum produto disponível"
            font = UIFont.systemFontOfSize(16.0)
            textColor = ShoppingColors.OnSurfaceVariant
            textAlignment = UIK.TextAlignCenter
            hidden = true
        }
        center(emptyLabel)

        productsSlot = newListSlot(
            stackView,
            factory = { ProductCardView(presenter).also { it.initialize() } },
            updater = { view, item -> view.setState(item); view.forceUpdate() }
        )
    }

    override fun doUpdate() {
        val products = presenter.state.products
        if (products === lastProducts) return
        lastProducts = products

        if (products.isNullOrEmpty()) {
            emptyLabel.hidden = false
            scrollView.hidden = true
            productsSlot.sync(emptyList())
        } else {
            emptyLabel.hidden = true
            scrollView.hidden = false
            productsSlot.sync(products)
        }
    }
}

// --- Item sub-view: ProductCardView ---

@OptIn(ExperimentalForeignApi::class)
private class ProductCardView(presenter: ProductsPanelPresenter) : AbstractViewIos<ProductsPanelPresenter>("product-card", presenter) {

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

        hStack(spacing = 12.0, configure = {
            alignment = UIK.StackAlignCenter
            layoutMarginsRelativeArrangement = true
            layoutMargins = UIEdgeInsetsMake(12.0, 12.0, 12.0, 12.0)
        }) {
            imageView = imageView {
                contentMode = UIK.ContentModeScaleAspectFill
                backgroundColor = ShoppingColors.SurfaceVariant
                layer.cornerRadius = 6.0
            }
            vStack(spacing = 4.0) {
                nameLabel = label {
                    font = UIFont.boldSystemFontOfSize(15.0)
                    textColor = ShoppingColors.OnSurface
                    numberOfLines = 1
                }
                priceLabel = label {
                    font = UIFont.boldSystemFontOfSize(14.0)
                    textColor = ShoppingColors.PriceColor
                }
            }
        }.also { pin(it) }

        NSLayoutConstraint.activateConstraints(listOf(
            imageView.widthAnchor.constraintEqualToConstant(64.0),
            imageView.heightAnchor.constraintEqualToConstant(64.0)
        ))

        // Tap gesture
        tapAction = ProductCardTapAction(this@ProductCardView).also { retainForGC(it) }
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
            priceLabel.text = "R$ ${ViewUtils.formatPrice(p.price)}"
        }
    }

    fun onTap() {
        val id = product?.id ?: return
        presenter.onOpenProduct(id)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ProductCardTapAction(private val cardView: ProductCardView) : NSObject() {

    val gesture: UITapGestureRecognizer = UITapGestureRecognizer(target = this, action = sel_registerName("onTap"))

    @ObjCAction
    fun onTap() {
        cardView.onTap()
    }
}
