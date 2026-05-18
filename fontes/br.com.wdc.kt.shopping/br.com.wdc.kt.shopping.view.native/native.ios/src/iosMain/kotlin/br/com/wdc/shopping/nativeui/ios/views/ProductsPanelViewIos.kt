package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.shopping.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.toolkit.UIKitDom
import br.com.wdc.shopping.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.shopping.nativeui.ios.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.theme.UIK
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
    private lateinit var headerTitle: UILabel
    private lateinit var headerBadge: UILabel
    private lateinit var productsSlot: ListSlot<ProductInfo, ProductCardView>

    private var lastProducts: List<ProductInfo>? = null
    private var lastCount: Int = -1

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
            headerTitle = label {
                text = "Produtos"
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
                backgroundColor = ShoppingColors.PrimaryContainer
                textAlignment = UIK.TextAlignCenter
                layer.cornerRadius = 10.0
                clipsToBounds = true
                NSLayoutConstraint.activateConstraints(listOf(
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                    heightAnchor.constraintEqualToConstant(20.0),
                    widthAnchor.constraintGreaterThanOrEqualToConstant(50.0)
                ))
            }
        }

        // Separator
        view(configure = {
            backgroundColor = ShoppingColors.SurfaceVariant
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
            stackView = vStack(spacing = 12.0) {}
            NSLayoutConstraint.activateConstraints(listOf(
                stackView.topAnchor.constraintEqualToAnchor(parent().topAnchor, 8.0),
                stackView.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 12.0),
                stackView.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -12.0),
                stackView.bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -8.0),
                stackView.widthAnchor.constraintEqualToAnchor(parent().widthAnchor, constant = -24.0)
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
            if (lastCount != 0) {
                lastCount = 0
                headerBadge.text = "  0 itens  "
            }
        } else {
            emptyLabel.hidden = true
            scrollView.hidden = false
            productsSlot.sync(products)
            val count = products.size
            if (count != lastCount) {
                lastCount = count
                headerBadge.text = "  $count itens  "
            }
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

        vStack(spacing = 0.0, configure = { alignment = UIK.StackAlignFill }) {
            // Product image — full width, fixed height
            imageView = imageView {
                contentMode = UIK.ContentModeScaleAspectFill
                backgroundColor = ShoppingColors.SurfaceVariant
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
                    textColor = ShoppingColors.OnSurface
                    numberOfLines = 2
                }
                // Price badge
                view(configure = {
                    backgroundColor = ShoppingColors.PriceBackground
                    layer.cornerRadius = 8.0
                    NSLayoutConstraint.activateConstraints(listOf(
                        heightAnchor.constraintEqualToConstant(28.0)
                    ))
                }) {
                    priceLabel = label {
                        font = UIFont.boldSystemFontOfSize(14.0)
                        textColor = ShoppingColors.PriceColor
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
