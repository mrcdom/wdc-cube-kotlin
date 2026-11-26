package br.com.wdc.store.nativeui.ios.views

import br.com.wdc.store.design.StoreTokens.FontSize
import br.com.wdc.store.design.StoreTokens.Radius
import br.com.wdc.store.design.StoreTokens.Spacing
import br.com.wdc.store.nativeui.ios.theme.pt
import br.com.wdc.store.nativeui.ios.theme.StoreColors
import br.com.wdc.store.nativeui.ios.theme.UIK
import br.com.wdc.store.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.store.nativeui.ios.toolkit.UIKitDom
import br.com.wdc.store.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.store.nativeui.ios.IosNativeRootPresenter
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.UIKit.*
import platform.darwin.NSObject
import platform.objc.sel_registerName

/**
 * A row of the cart — image, name, price × quantity, quantity controls (minimum 1) and remove.
 * A view of its own in the catalog: the cart view only places it; it repaints when its own scope changes.
 */
@OptIn(ExperimentalForeignApi::class)
class CartItemViewIos(app: IosNativeRootPresenter, scope: CartItemScope) :
    AbstractViewIos<CartItemScope>("cart-item", app, scope) {

    private lateinit var imageView: UIImageView
    private lateinit var nameLabel: UILabel
    private lateinit var priceLabel: UILabel
    private lateinit var qtyLabel: UILabel

    // Retained action target (UIKit keeps gesture targets unretained)
    private val itemActions = CartItemActions(this).also { retainForGC(it) }

    // Guards
    private var lastProductId: Long = -1
    private var lastName: String? = null
    private var lastPrice: Double = -1.0
    private var lastQuantity: Int = -1

    override fun createView(): UIView = UIKitDom.build {
        val card = parent()
        card.backgroundColor = StoreColors.SurfaceVariant40
        card.layer.cornerRadius = Radius.Sm.pt

        hStack(spacing = Spacing.Md.pt, configure = {
            alignment = UIK.StackAlignCenter
            layoutMarginsRelativeArrangement = true
            layoutMargins = UIEdgeInsetsMake(10.0, 10.0, 10.0, 10.0)
        }) {
            imageView = imageView {
                contentMode = UIK.ContentModeScaleAspectFill
                backgroundColor = StoreColors.SurfaceVariant
                layer.cornerRadius = 6.0
            }
            vStack(spacing = 2.0) {
                nameLabel = label {
                    font = UIFont.boldSystemFontOfSize(FontSize.Body.pt)
                    textColor = StoreColors.OnSurface
                }
                priceLabel = label {
                    font = UIFont.systemFontOfSize(13.0)
                    textColor = StoreColors.PriceColor
                }
            }
            hStack(spacing = Spacing.Xs.pt, configure = { alignment = UIK.StackAlignCenter }) {
                button("−") {
                    backgroundColor = StoreColors.SecondaryContainer
                    layer.cornerRadius = Radius.Xs.pt
                    addGestureRecognizer(UITapGestureRecognizer(target = itemActions, action = sel_registerName("onMinus")))
                }.also { size(it, width = 28.0, height = 28.0) }
                qtyLabel = label {
                    font = UIFont.boldSystemFontOfSize(FontSize.Body.pt)
                    textAlignment = UIK.TextAlignCenter
                    widthAnchor.constraintEqualToConstant(30.0).active = true
                }
                button("+") {
                    backgroundColor = StoreColors.SecondaryContainer
                    layer.cornerRadius = Radius.Xs.pt
                    addGestureRecognizer(UITapGestureRecognizer(target = itemActions, action = sel_registerName("onPlus")))
                }.also { size(it, width = 28.0, height = 28.0) }
                button("✕") {
                    setTitleColor(StoreColors.Error, forState = UIControlStateNormal)
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
        val productId = scope.productId
        if (productId != lastProductId) {
            lastProductId = productId
            ViewUtils.loadImageAsync(imageView, ViewUtils.productImageUrl(productId))
        }
        val name = scope.name
        if (name != lastName) {
            lastName = name
            nameLabel.text = name ?: ""
        }
        val price = scope.price
        val quantity = scope.quantity
        if (price != lastPrice || quantity != lastQuantity) {
            lastPrice = price
            lastQuantity = quantity
            priceLabel.text = "\$${ViewUtils.formatPrice(price)} × $quantity"
            qtyLabel.text = quantity.toString()
        }
    }

    internal fun onMinus() {
        val quantity = scope.quantity
        if (quantity > 1) {
            safeAction("minus") { scope.onModifyQuantity(quantity - 1) }
        }
    }

    internal fun onPlus() {
        val quantity = scope.quantity
        safeAction("plus") { scope.onModifyQuantity(quantity + 1) }
    }

    internal fun onRemove() {
        safeAction("remove") { scope.onRemove() }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CartItemActions(private val view: CartItemViewIos) : NSObject() {

    @ObjCAction
    fun onMinus() {
        view.onMinus()
    }

    @ObjCAction
    fun onPlus() {
        view.onPlus()
    }

    @ObjCAction
    fun onRemove() {
        view.onRemove()
    }
}
