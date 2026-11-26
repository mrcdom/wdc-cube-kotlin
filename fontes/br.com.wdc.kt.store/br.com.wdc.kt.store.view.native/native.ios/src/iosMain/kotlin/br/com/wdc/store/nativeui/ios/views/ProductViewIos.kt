package br.com.wdc.store.nativeui.ios.views

import br.com.wdc.store.design.StoreTokens.FontSize
import br.com.wdc.store.design.StoreTokens.Radius
import br.com.wdc.store.design.StoreTokens.Spacing
import br.com.wdc.store.nativeui.ios.theme.pt
import br.com.wdc.store.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.store.nativeui.ios.toolkit.UIKitDom
import br.com.wdc.store.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.store.nativeui.ios.theme.StoreColors
import br.com.wdc.store.nativeui.ios.theme.StoreIcons
import br.com.wdc.store.nativeui.ios.theme.UIK
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.store.nativeui.ios.IosNativeRootPresenter
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
class ProductViewIos(app: IosNativeRootPresenter, scope: ProductScope) : AbstractViewIos<ProductScope>("product-view", app, scope) {

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
        root.backgroundColor = StoreColors.Background

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
            contentStack = vStack(spacing = Spacing.Lg.pt, configure = { alignment = UIK.StackAlignFill }) {
                // Title (large, bold — h4 equivalent)
                nameLabel = label {
                    font = UIFont.boldSystemFontOfSize(26.0)
                    textColor = StoreColors.OnSurface
                    numberOfLines = 0
                }

                // Separator
                view(configure = {
                    backgroundColor = StoreColors.SurfaceVariant
                    heightAnchor.constraintEqualToConstant(1.0).active = true
                })

                // Description in card (SurfaceVariant50 bg, 8px radius, 16px padding)
                view(configure = {
                    backgroundColor = StoreColors.SurfaceVariant50
                    layer.cornerRadius = Radius.Sm.pt
                }) {
                    descriptionLabel = label {
                        font = UIFont.systemFontOfSize(15.0)
                        textColor = StoreColors.OnSurfaceVariant
                        numberOfLines = 0
                    }
                    pin(descriptionLabel, insets = Spacing.Lg.pt)
                }

                // Price + Image row
                hStack(spacing = Spacing.Lg.pt, configure = { alignment = UIK.StackAlignCenter }) {
                    // Price and quantity on the left
                    vStack(spacing = Spacing.Md.pt, configure = {
                        alignment = UIK.StackAlignLeading
                        setContentHuggingPriority(251.0f, UIK.AxisHorizontal)
                    }) {
                        // Price badge
                        view(configure = {
                            backgroundColor = StoreColors.PriceBackground
                            layer.cornerRadius = Radius.Md.pt
                            heightAnchor.constraintEqualToConstant(40.0).active = true
                        }) {
                            priceLabel = label {
                                font = UIFont.boldSystemFontOfSize(FontSize.Title.pt)
                                textColor = StoreColors.PriceColor
                            }
                            center(priceLabel)
                            NSLayoutConstraint.activateConstraints(listOf(
                                priceLabel.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, Spacing.Lg.pt),
                                priceLabel.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -Spacing.Lg.pt)
                            ))
                        }
                        // Quantity selector
                        hStack(spacing = Spacing.Xs.pt, configure = { alignment = UIK.StackAlignCenter }) {
                            label {
                                text = "Qty:"
                                font = UIFont.systemFontOfSize(FontSize.Body.pt)
                                textColor = StoreColors.OnSurfaceVariant
                            }
                            qtyContainer = view(configure = {
                                backgroundColor = StoreColors.SurfaceVariant
                                layer.cornerRadius = Radius.Md.pt
                                NSLayoutConstraint.activateConstraints(listOf(
                                    heightAnchor.constraintEqualToConstant(40.0),
                                    widthAnchor.constraintEqualToConstant(120.0)
                                ))
                            }) {
                                minusButton = button("−") {
                                    titleLabel?.font = UIFont.boldSystemFontOfSize(FontSize.Title.pt)
                                    addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onMinus")))
                                    NSLayoutConstraint.activateConstraints(listOf(
                                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, Spacing.Sm.pt),
                                        topAnchor.constraintEqualToAnchor(parent().topAnchor),
                                        bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                                        widthAnchor.constraintEqualToConstant(32.0)
                                    ))
                                }
                                quantityLabel = label {
                                    text = "1"
                                    font = UIFont.boldSystemFontOfSize(17.0)
                                    textColor = StoreColors.OnSurface
                                    textAlignment = UIK.TextAlignCenter
                                }
                                center(quantityLabel)
                                plusButton = button("+") {
                                    titleLabel?.font = UIFont.boldSystemFontOfSize(FontSize.Title.pt)
                                    addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onPlus")))
                                    NSLayoutConstraint.activateConstraints(listOf(
                                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -Spacing.Sm.pt),
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
                        backgroundColor = StoreColors.SurfaceVariant
                        layer.cornerRadius = Radius.Md.pt
                        contentMode = UIK.ContentModeScaleAspectFill
                        NSLayoutConstraint.activateConstraints(listOf(
                            widthAnchor.constraintEqualToConstant(120.0),
                            heightAnchor.constraintEqualToConstant(120.0)
                        ))
                    }
                }

                // Action buttons row
                hStack(spacing = Spacing.Md.pt, configure = { alignment = UIK.StackAlignCenter }) {
                    flexSpacer()
                    backButton = button("Back") {
                        setTitleColor(StoreColors.Primary, forState = UIControlStateNormal)
                        titleLabel?.font = UIFont.systemFontOfSize(15.0)
                        setImage(StoreIcons.arrowBack(18.0, StoreColors.Primary), forState = UIControlStateNormal)
                        imageEdgeInsets = UIEdgeInsetsMake(0.0, -Spacing.Xs.pt, 0.0, Spacing.Xs.pt)
                        layer.borderWidth = 1.0
                        layer.borderColor = StoreColors.Primary.CGColor
                        layer.cornerRadius = Radius.Md.pt
                        addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onBack")))
                        NSLayoutConstraint.activateConstraints(listOf(
                            heightAnchor.constraintEqualToConstant(48.0),
                            widthAnchor.constraintEqualToConstant(110.0)
                        ))
                    }
                    addToCartButton = button("Add") {
                        setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                        backgroundColor = StoreColors.Primary
                        layer.cornerRadius = Radius.Md.pt
                        titleLabel?.font = UIFont.boldSystemFontOfSize(15.0)
                        setImage(StoreIcons.addShoppingCart(18.0, UIColor.whiteColor), forState = UIControlStateNormal)
                        imageEdgeInsets = UIEdgeInsetsMake(0.0, -Spacing.Xs.pt, 0.0, Spacing.Xs.pt)
                        addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onAddToCart")))
                        NSLayoutConstraint.activateConstraints(listOf(
                            heightAnchor.constraintEqualToConstant(48.0),
                            widthAnchor.constraintGreaterThanOrEqualToConstant(140.0)
                        ))
                    }
                }

                // Error label
                errorLabel = label {
                    font = UIFont.systemFontOfSize(FontSize.Body.pt)
                    textColor = StoreColors.Error
                    numberOfLines = 0
                    textAlignment = UIK.TextAlignCenter
                    hidden = true
                }
            }
            NSLayoutConstraint.activateConstraints(listOf(
                contentStack.topAnchor.constraintEqualToAnchor(parent().topAnchor, Spacing.Lg.pt),
                contentStack.leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, Spacing.Lg.pt),
                contentStack.trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -Spacing.Lg.pt),
                contentStack.bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -Spacing.Lg.pt),
                contentStack.widthAnchor.constraintEqualToAnchor(parent().widthAnchor, constant = -32.0)
            ))
        }
    }

    override fun doUpdate() {
        val product = scope.product

        if (product != null) {
            nameLabel.text = product.name ?: ""
            descriptionLabel.text = ViewUtils.stripHtml(product.description)
            priceLabel.text = "\$${ViewUtils.formatPrice(product.price)}"

            val imageUrl = ViewUtils.productImageUrl(product.id)
            ViewUtils.loadImageAsync(imageView, imageUrl)
        }

        // Error
        val errorMessage = scope.errorMessage
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
        safeAction("addToCart") { scope.onAddToCart(quantity) }
    }

    internal fun goBack() {
        safeAction("back") { scope.onOpenProducts() }
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
