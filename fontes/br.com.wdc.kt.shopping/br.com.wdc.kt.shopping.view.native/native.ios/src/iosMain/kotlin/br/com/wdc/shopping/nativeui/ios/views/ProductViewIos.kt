package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.shopping.nativeui.ios.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.UIK
import br.com.wdc.shopping.nativeui.ios.UIKitDom
import br.com.wdc.shopping.nativeui.ios.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.ViewUtils
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.UIKit.*
import platform.CoreGraphics.CGRectMake
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Product detail view — image, name, description, price, quantity selector, add-to-cart button.
 */
@OptIn(ExperimentalForeignApi::class)
class ProductViewIos(presenter: ProductPresenter) : AbstractViewIos<ProductPresenter>("product-view", presenter) {

    private lateinit var scrollView: UIScrollView
    private lateinit var contentStack: UIStackView
    private lateinit var imageView: UIImageView
    private lateinit var nameLabel: UILabel
    private lateinit var descriptionLabel: UILabel
    private lateinit var priceLabel: UILabel
    private lateinit var qtyContainer: UIView
    private lateinit var minusButton: UIButton
    private lateinit var quantityLabel: UILabel
    private lateinit var plusButton: UIButton
    private lateinit var addToCartButton: UIButton
    private lateinit var backButton: UIButton
    private lateinit var errorLabel: UILabel

    private var quantity = 1
    private val actions = ProductActions(this).also { retainForGC(it) }

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
                heightAnchor.constraintEqualToConstant(36.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -12.0)
            ))
        }

        // Scroll content
        scrollView = scrollView(configure = {
            delaysContentTouches = false
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(backButton.bottomAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        }) {
            contentStack = vStack(spacing = 16.0, configure = { alignment = UIK.StackAlignCenter }) {
                imageView = imageView {
                    backgroundColor = ShoppingColors.SurfaceVariant
                    layer.cornerRadius = 8.0
                    NSLayoutConstraint.activateConstraints(listOf(
                        widthAnchor.constraintEqualToConstant(200.0),
                        heightAnchor.constraintEqualToConstant(200.0)
                    ))
                }
                nameLabel = label {
                    font = UIFont.boldSystemFontOfSize(20.0)
                    textColor = ShoppingColors.OnSurface
                    numberOfLines = 0
                    textAlignment = UIK.TextAlignCenter
                }
                descriptionLabel = label {
                    font = UIFont.systemFontOfSize(14.0)
                    textColor = ShoppingColors.OnSurfaceVariant
                    numberOfLines = 0
                }
                priceLabel = label {
                    font = UIFont.boldSystemFontOfSize(22.0)
                    textColor = ShoppingColors.PriceColor
                    textAlignment = UIK.TextAlignCenter
                }
                // Quantity selector
                qtyContainer = view(configure = {
                    backgroundColor = ShoppingColors.SurfaceVariant
                    layer.cornerRadius = 12.0
                    NSLayoutConstraint.activateConstraints(listOf(
                        heightAnchor.constraintEqualToConstant(44.0),
                        widthAnchor.constraintEqualToConstant(140.0)
                    ))
                }) {
                    minusButton = button("−") {
                        titleLabel?.font = UIFont.boldSystemFontOfSize(20.0)
                        addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onMinus")))
                        NSLayoutConstraint.activateConstraints(listOf(
                            leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 8.0),
                            topAnchor.constraintEqualToAnchor(parent().topAnchor),
                            bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                            widthAnchor.constraintEqualToConstant(36.0)
                        ))
                    }
                    quantityLabel = label {
                        text = "1"
                        font = UIFont.boldSystemFontOfSize(18.0)
                        textColor = ShoppingColors.OnSurface
                        textAlignment = UIK.TextAlignCenter
                        NSLayoutConstraint.activateConstraints(listOf(
                            centerXAnchor.constraintEqualToAnchor(parent().centerXAnchor),
                            centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                        ))
                    }
                    plusButton = button("+") {
                        titleLabel?.font = UIFont.boldSystemFontOfSize(20.0)
                        addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onPlus")))
                        NSLayoutConstraint.activateConstraints(listOf(
                            trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -8.0),
                            topAnchor.constraintEqualToAnchor(parent().topAnchor),
                            bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                            widthAnchor.constraintEqualToConstant(36.0)
                        ))
                    }
                }
                addToCartButton = button("🛒 Adicionar ao Carrinho") {
                    setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                    backgroundColor = ShoppingColors.PriceColor
                    layer.cornerRadius = 12.0
                    titleLabel?.font = UIFont.boldSystemFontOfSize(16.0)
                    addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onAddToCart")))
                    NSLayoutConstraint.activateConstraints(listOf(
                        heightAnchor.constraintEqualToConstant(48.0),
                        widthAnchor.constraintEqualToAnchor(parent().widthAnchor)
                    ))
                }
                errorLabel = label {
                    font = UIFont.systemFontOfSize(14.0)
                    textColor = ShoppingColors.Error
                    numberOfLines = 0
                    textAlignment = UIK.TextAlignCenter
                    hidden = true
                }
            }
            NSLayoutConstraint.activateConstraints(listOf(
                contentStack.topAnchor.constraintEqualToAnchor(parent().topAnchor, 16.0),
                contentStack.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                contentStack.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0),
                contentStack.bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -16.0),
                contentStack.widthAnchor.constraintEqualToAnchor(parent().widthAnchor, constant = -32.0)
            ))
        }
    }

    override fun doUpdate() {
        val state = presenter.state
        val product = state.product

        if (product != null) {
            nameLabel.text = product.name ?: ""
            descriptionLabel.text = ViewUtils.stripHtml(product.description)
            priceLabel.text = "R$ ${ViewUtils.formatPrice(product.price)}"

            val imageUrl = ViewUtils.productImageUrl(product.id)
            ViewUtils.loadImageAsync(imageView, imageUrl)
        }

        // Error
        val errorMessage = state.errorMessage
        if (!errorMessage.isNullOrBlank()) {
            errorLabel.text = errorMessage
            errorLabel.hidden = false
        } else {
            errorLabel.hidden = true
        }

        quantityLabel.text = quantity.toString()
    }

    internal fun decreaseQuantity() {
        if (quantity > 1) {
            quantity--
            quantityLabel.text = quantity.toString()
        }
    }

    internal fun increaseQuantity() {
        quantity++
        quantityLabel.text = quantity.toString()
    }

    internal fun addToCart() {
        presenter.onAddToCart(quantity)
    }

    internal fun goBack() {
        presenter.onOpenProducts()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class ProductActions(private val view: ProductViewIos) : NSObject() {

    @ObjCAction
    fun onMinus() {
        view.decreaseQuantity()
    }

    @ObjCAction
    fun onPlus() {
        view.increaseQuantity()
    }

    @ObjCAction
    fun onAddToCart() {
        view.addToCart()
    }

    @ObjCAction
    fun onBack() {
        view.goBack()
    }
}
