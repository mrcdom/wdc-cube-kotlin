package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.shopping.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.toolkit.UIKitDom
import br.com.wdc.shopping.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.shopping.nativeui.ios.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.theme.ShoppingIcons
import br.com.wdc.shopping.nativeui.ios.theme.UIK
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

        // Scroll content
        scrollView = scrollView(configure = {
            delaysContentTouches = false
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        }) {
            contentStack = vStack(spacing = 16.0, configure = { alignment = UIK.StackAlignFill }) {
                // Title (large, bold — h4 equivalent)
                nameLabel = label {
                    font = UIFont.boldSystemFontOfSize(26.0)
                    textColor = ShoppingColors.OnSurface
                    numberOfLines = 0
                }

                // Separator
                view(configure = {
                    backgroundColor = ShoppingColors.SurfaceVariant
                    heightAnchor.constraintEqualToConstant(1.0).active = true
                })

                // Description in card (SurfaceVariant50 bg, 8px radius, 16px padding)
                view(configure = {
                    backgroundColor = ShoppingColors.SurfaceVariant50
                    layer.cornerRadius = 8.0
                }) {
                    descriptionLabel = label {
                        font = UIFont.systemFontOfSize(15.0)
                        textColor = ShoppingColors.OnSurfaceVariant
                        numberOfLines = 0
                    }
                    pin(descriptionLabel, insets = 16.0)
                }

                // Price + Image row
                hStack(spacing = 16.0, configure = { alignment = UIK.StackAlignCenter }) {
                    // Price and quantity on the left
                    vStack(spacing = 12.0, configure = {
                        alignment = UIK.StackAlignLeading
                        setContentHuggingPriority(251.0f, UIK.AxisHorizontal)
                    }) {
                        // Price badge
                        view(configure = {
                            backgroundColor = ShoppingColors.PriceBackground
                            layer.cornerRadius = 12.0
                            heightAnchor.constraintEqualToConstant(40.0).active = true
                        }) {
                            priceLabel = label {
                                font = UIFont.boldSystemFontOfSize(20.0)
                                textColor = ShoppingColors.PriceColor
                            }
                            center(priceLabel)
                            NSLayoutConstraint.activateConstraints(listOf(
                                priceLabel.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                                priceLabel.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0)
                            ))
                        }
                        // Quantity selector
                        hStack(spacing = 4.0, configure = { alignment = UIK.StackAlignCenter }) {
                            label {
                                text = "Qtd:"
                                font = UIFont.systemFontOfSize(14.0)
                                textColor = ShoppingColors.OnSurfaceVariant
                            }
                            qtyContainer = view(configure = {
                                backgroundColor = ShoppingColors.SurfaceVariant
                                layer.cornerRadius = 12.0
                                NSLayoutConstraint.activateConstraints(listOf(
                                    heightAnchor.constraintEqualToConstant(40.0),
                                    widthAnchor.constraintEqualToConstant(120.0)
                                ))
                            }) {
                                minusButton = button("−") {
                                    titleLabel?.font = UIFont.boldSystemFontOfSize(20.0)
                                    addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onMinus")))
                                    NSLayoutConstraint.activateConstraints(listOf(
                                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 8.0),
                                        topAnchor.constraintEqualToAnchor(parent().topAnchor),
                                        bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                                        widthAnchor.constraintEqualToConstant(32.0)
                                    ))
                                }
                                quantityLabel = label {
                                    text = "1"
                                    font = UIFont.boldSystemFontOfSize(17.0)
                                    textColor = ShoppingColors.OnSurface
                                    textAlignment = UIK.TextAlignCenter
                                }
                                center(quantityLabel)
                                plusButton = button("+") {
                                    titleLabel?.font = UIFont.boldSystemFontOfSize(20.0)
                                    addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onPlus")))
                                    NSLayoutConstraint.activateConstraints(listOf(
                                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -8.0),
                                        topAnchor.constraintEqualToAnchor(parent().topAnchor),
                                        bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                                        widthAnchor.constraintEqualToConstant(32.0)
                                    ))
                                }
                            }
                        }
                    }
                    // Product image on the right
                    imageView = imageView {
                        backgroundColor = ShoppingColors.SurfaceVariant
                        layer.cornerRadius = 12.0
                        contentMode = UIK.ContentModeScaleAspectFill
                        NSLayoutConstraint.activateConstraints(listOf(
                            widthAnchor.constraintEqualToConstant(120.0),
                            heightAnchor.constraintEqualToConstant(120.0)
                        ))
                    }
                }

                // Action buttons row
                hStack(spacing = 12.0, configure = { alignment = UIK.StackAlignCenter }) {
                    flexSpacer()
                    backButton = button("Voltar") {
                        setTitleColor(ShoppingColors.Primary, forState = UIControlStateNormal)
                        titleLabel?.font = UIFont.systemFontOfSize(15.0)
                        setImage(ShoppingIcons.arrowBack(18.0, ShoppingColors.Primary), forState = UIControlStateNormal)
                        imageEdgeInsets = UIEdgeInsetsMake(0.0, -4.0, 0.0, 4.0)
                        layer.borderWidth = 1.0
                        layer.borderColor = ShoppingColors.Primary.CGColor
                        layer.cornerRadius = 12.0
                        addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onBack")))
                        NSLayoutConstraint.activateConstraints(listOf(
                            heightAnchor.constraintEqualToConstant(48.0),
                            widthAnchor.constraintEqualToConstant(110.0)
                        ))
                    }
                    addToCartButton = button("Adicionar") {
                        setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                        backgroundColor = ShoppingColors.Primary
                        layer.cornerRadius = 12.0
                        titleLabel?.font = UIFont.boldSystemFontOfSize(15.0)
                        setImage(ShoppingIcons.addShoppingCart(18.0, UIColor.whiteColor), forState = UIControlStateNormal)
                        imageEdgeInsets = UIEdgeInsetsMake(0.0, -4.0, 0.0, 4.0)
                        addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onAddToCart")))
                        NSLayoutConstraint.activateConstraints(listOf(
                            heightAnchor.constraintEqualToConstant(48.0),
                            widthAnchor.constraintGreaterThanOrEqualToConstant(140.0)
                        ))
                    }
                }

                // Error label
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
