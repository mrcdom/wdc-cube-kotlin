package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.shopping.nativeui.ios.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.UIK
import br.com.wdc.shopping.nativeui.ios.UIKitDom
import br.com.wdc.shopping.nativeui.ios.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.ViewUtils
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.structs.CartItem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.UIKit.*
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Cart view — list of items with quantity controls, remove button, total, and buy button.
 * Uses ListSlot with CartItemView sub-views for efficient recycling.
 */
@OptIn(ExperimentalForeignApi::class)
class CartViewIos(presenter: CartPresenter) : AbstractViewIos<CartPresenter>("cart-view", presenter) {

    private lateinit var scrollView: UIScrollView
    private lateinit var stackView: UIStackView
    private lateinit var emptyLabel: UILabel
    private lateinit var totalLabel: UILabel
    private lateinit var buyButton: UIButton
    private lateinit var backButton: UIButton
    private lateinit var errorLabel: UILabel
    private lateinit var footerContainer: UIView
    private lateinit var itemsSlot: ListSlot<CartItem, CartItemView>

    private val actions = CartActions(this).also { retainForGC(it) }

    // Guards
    private var lastItems: List<CartItem>? = null
    private var lastError: String? = null

    private lateinit var titleLabel: UILabel

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = ShoppingColors.Background

        // Back button
        backButton = button("← Produtos") {
            setTitleColor(ShoppingColors.Primary, forState = UIControlStateNormal)
            titleLabel?.font = UIFont.systemFontOfSize(16.0)
            addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onBack")))
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 12.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -12.0),
                heightAnchor.constraintEqualToConstant(36.0)
            ))
        }

        // Title
        titleLabel = label {
            text = "🛒 Carrinho"
            font = UIFont.boldSystemFontOfSize(20.0)
            textColor = ShoppingColors.OnSurface
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(backButton.bottomAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0)
            ))
        }

        // Footer: total + buy (declared before scrollView so scrollView can reference it)
        footerContainer = view(configure = {
            NSLayoutConstraint.activateConstraints(listOf(
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        }) {
            totalLabel = label {
                font = UIFont.boldSystemFontOfSize(22.0)
                textColor = ShoppingColors.PriceColor
                textAlignment = UIK.TextAlignCenter
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(parent().topAnchor, 12.0),
                    centerXAnchor.constraintEqualToAnchor(parent().centerXAnchor)
                ))
            }

            buyButton = button("Comprar") {
                setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                backgroundColor = ShoppingColors.PriceColor
                layer.cornerRadius = 12.0
                titleLabel?.font = UIFont.boldSystemFontOfSize(16.0)
                addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onBuy")))
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(totalLabel.bottomAnchor, 12.0),
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0),
                    heightAnchor.constraintEqualToConstant(48.0),
                    bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -16.0)
                ))
            }
        }

        // Scroll area
        scrollView = scrollView(configure = {
            delaysContentTouches = false
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(titleLabel.bottomAnchor, 12.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(footerContainer.topAnchor)
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

        // Empty state
        emptyLabel = label {
            text = "🛒 Carrinho vazio"
            font = UIFont.systemFontOfSize(16.0)
            textColor = ShoppingColors.OnSurfaceVariant
            textAlignment = UIK.TextAlignCenter
            hidden = true
        }
        center(emptyLabel)

        // Error
        errorLabel = label {
            font = UIFont.systemFontOfSize(14.0)
            textColor = ShoppingColors.Error
            numberOfLines = 0
            textAlignment = UIK.TextAlignCenter
            hidden = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(titleLabel.bottomAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0)
            ))
        }

        itemsSlot = newListSlot(
            stackView,
            factory = { CartItemView(presenter).also { it.initialize() } },
            updater = { view, item -> view.setState(item); view.forceUpdate() }
        )
    }

    override fun doUpdate() {
        val state = presenter.state
        val items = state.items

        // Items list (guard by reference)
        if (items !== lastItems) {
            lastItems = items

            if (items.isEmpty()) {
                emptyLabel.hidden = false
                scrollView.hidden = true
                footerContainer.hidden = true
                itemsSlot.sync(emptyList())
            } else {
                emptyLabel.hidden = true
                scrollView.hidden = false
                footerContainer.hidden = false
                itemsSlot.sync(items)

                var total = 0.0
                for (item in items) {
                    total += item.price * item.quantity
                }
                totalLabel.text = "Total: R$ ${ViewUtils.formatPrice(total)}"
            }
        }

        // Error (guard)
        val errorMessage = state.errorMessage
        if (errorMessage != lastError) {
            lastError = errorMessage
            if (!errorMessage.isNullOrBlank()) {
                errorLabel.text = errorMessage
                errorLabel.hidden = false
            } else {
                errorLabel.hidden = true
            }
        }
    }

    internal fun goBack() {
        safeAction("back") { presenter.onOpenProducts() }
    }

    internal fun buy() {
        safeAction("buy") { presenter.onBuy() }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CartActions(private val view: CartViewIos) : NSObject() {

    @ObjCAction
    fun onBack() {
        view.goBack()
    }

    @ObjCAction
    fun onBuy() {
        view.buy()
    }
}

// --- Item sub-view: CartItemView ---

@OptIn(ExperimentalForeignApi::class)
private class CartItemView(presenter: CartPresenter) : AbstractViewIos<CartPresenter>("cart-item", presenter) {

    private lateinit var imageView: UIImageView
    private lateinit var nameLabel: UILabel
    private lateinit var priceLabel: UILabel
    private lateinit var qtyLabel: UILabel

    var item: CartItem? = null
        private set

    // Retained action target (UIKit uses weak ref to control target)
    private lateinit var itemActions: CartItemActions

    // Guards
    private var lastItemId: Long = -1
    private var lastName: String? = null
    private var lastPrice: Double = -1.0
    private var lastQuantity: Int = -1

    fun setState(cartItem: CartItem) {
        this.item = cartItem
    }

    override fun createView(): UIView = UIKitDom.build {
        val card = parent()
        card.backgroundColor = ShoppingColors.SurfaceVariant40
        card.layer.cornerRadius = 8.0

        hStack(spacing = 12.0, configure = {
            alignment = UIK.StackAlignCenter
            layoutMarginsRelativeArrangement = true
            layoutMargins = UIEdgeInsetsMake(10.0, 10.0, 10.0, 10.0)
        }) {
            imageView = imageView {
                contentMode = UIK.ContentModeScaleAspectFill
                backgroundColor = ShoppingColors.SurfaceVariant
                layer.cornerRadius = 6.0
            }
            vStack(spacing = 2.0) {
                nameLabel = label {
                    font = UIFont.boldSystemFontOfSize(14.0)
                    textColor = ShoppingColors.OnSurface
                }
                priceLabel = label {
                    font = UIFont.systemFontOfSize(13.0)
                    textColor = ShoppingColors.PriceColor
                }
            }
            hStack(spacing = 4.0, configure = { alignment = UIK.StackAlignCenter }) {
                itemActions = CartItemActions(this@CartItemView).also { retainForGC(it) }
                button("−") {
                    backgroundColor = ShoppingColors.SecondaryContainer
                    layer.cornerRadius = 4.0
                    addGestureRecognizer(UITapGestureRecognizer(target = itemActions, action = sel_registerName("onMinus")))
                }.also { size(it, width = 28.0, height = 28.0) }
                qtyLabel = label {
                    font = UIFont.boldSystemFontOfSize(14.0)
                    textAlignment = UIK.TextAlignCenter
                    widthAnchor.constraintEqualToConstant(30.0).active = true
                }
                button("+") {
                    backgroundColor = ShoppingColors.SecondaryContainer
                    layer.cornerRadius = 4.0
                    addGestureRecognizer(UITapGestureRecognizer(target = itemActions, action = sel_registerName("onPlus")))
                }.also { size(it, width = 28.0, height = 28.0) }
                button("✕") {
                    setTitleColor(ShoppingColors.Error, forState = UIControlStateNormal)
                    addGestureRecognizer(UITapGestureRecognizer(target = itemActions, action = sel_registerName("onRemove")))
                }.also { size(it, width = 28.0, height = 28.0) }
            }
        }.also { pin(it) }

        NSLayoutConstraint.activateConstraints(listOf(
            imageView.widthAnchor.constraintEqualToConstant(50.0),
            imageView.heightAnchor.constraintEqualToConstant(50.0)
        ))
    }

    override fun doUpdate() {
        val i = item ?: return

        if (i.id != lastItemId) {
            lastItemId = i.id
            ViewUtils.loadImageAsync(imageView, ViewUtils.productImageUrl(i.id))
            // Reset guards on identity change
            lastName = null
            lastPrice = -1.0
            lastQuantity = -1
        }
        if (i.name != lastName) {
            lastName = i.name
            nameLabel.text = i.name ?: ""
        }
        if (i.price != lastPrice || i.quantity != lastQuantity) {
            lastPrice = i.price
            lastQuantity = i.quantity
            priceLabel.text = "R$ ${ViewUtils.formatPrice(i.price)} × ${i.quantity}"
            qtyLabel.text = i.quantity.toString()
        }
    }

    fun onMinus() {
        val i = item ?: return
        if (i.quantity > 1) {
            presenter.onModifyQuantity(i.id, i.quantity - 1)
        }
    }

    fun onPlus() {
        val i = item ?: return
        presenter.onModifyQuantity(i.id, i.quantity + 1)
    }

    fun onRemove() {
        val i = item ?: return
        presenter.onRemoveProduct(i.id)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CartItemActions(private val cardView: CartItemView) : NSObject() {

    @ObjCAction
    fun onMinus() {
        cardView.onMinus()
    }

    @ObjCAction
    fun onPlus() {
        cardView.onPlus()
    }

    @ObjCAction
    fun onRemove() {
        cardView.onRemove()
    }
}
