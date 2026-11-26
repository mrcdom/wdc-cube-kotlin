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
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.store.nativeui.ios.IosNativeRootPresenter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.UIKit.*
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Cart view — the rows, the total, and the buy button.
 * Each row is a view of its own ([CartItemViewIos], from the catalog): the cart only places them, in order.
 * A row that stays keeps its view; the view of a row that leaves is released when the cart is drawn without it.
 */
@OptIn(ExperimentalForeignApi::class)
class CartViewIos(app: IosNativeRootPresenter, scope: CartScope) : AbstractViewIos<CartScope>("cart-view", app, scope) {

    private lateinit var scrollView: UIScrollView
    private lateinit var stackView: UIStackView
    private lateinit var emptyContainer: UIView
    private lateinit var totalLabel: UILabel
    private lateinit var buyButton: UIButton
    private lateinit var backButton: UIButton
    private lateinit var errorLabel: UILabel
    private lateinit var footerContainer: UIView


    private val actions = CartActions(this).also { retainForGC(it) }

    // Guards
    private var lastRows: List<CartItemScope>? = null
    private var lastTotal: Double = -1.0
    private var lastError: String? = null

    private lateinit var titleLabel: UILabel

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = StoreColors.Background

        // Title header
        titleLabel = label {
            text = "Shopping Cart"
            font = UIFont.systemFontOfSize(FontSize.Headline.pt, UIFontWeightMedium)
            textColor = StoreColors.OnSurface
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor, Spacing.Lg.pt),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, Spacing.Lg.pt),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -Spacing.Lg.pt)
            ))
        }

        // Separator
        val separator = view(configure = {
            backgroundColor = StoreColors.SurfaceVariant
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(titleLabel.bottomAnchor, Spacing.Md.pt),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, Spacing.Lg.pt),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -Spacing.Lg.pt),
                heightAnchor.constraintEqualToConstant(1.0)
            ))
        })

        // Back button (visible in empty state, aligned right below empty message)
        backButton = button("Continue Shopping") {
            setTitleColor(StoreColors.Primary, forState = UIControlStateNormal)
            titleLabel?.font = UIFont.systemFontOfSize(FontSize.Body.pt)
            setImage(StoreIcons.arrowBack(18.0, StoreColors.Primary), forState = UIControlStateNormal)
            imageEdgeInsets = UIEdgeInsetsMake(0.0, -Spacing.Xs.pt, 0.0, Spacing.Xs.pt)
            contentEdgeInsets = UIEdgeInsetsMake(Spacing.Sm.pt, 20.0, Spacing.Sm.pt, 20.0)
            layer.borderWidth = 1.0
            layer.borderColor = StoreColors.Primary.CGColor
            layer.cornerRadius = Radius.Md.pt
            addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onBack")))
            NSLayoutConstraint.activateConstraints(listOf(
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -Spacing.Lg.pt),
                heightAnchor.constraintEqualToConstant(48.0)
            ))
        }

        // Scroll area (items + footer flow together)
        scrollView = scrollView(configure = {
            delaysContentTouches = false
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(separator.bottomAnchor, Spacing.Sm.pt),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        }) {
            val contentView = view(configure = {
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(parent().topAnchor),
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                    bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                    widthAnchor.constraintEqualToAnchor(parent().widthAnchor)
                ))
            }) {
                // Items stack
                stackView = vStack(spacing = 10.0, configure = {
                    NSLayoutConstraint.activateConstraints(listOf(
                        topAnchor.constraintEqualToAnchor(parent().topAnchor, Spacing.Sm.pt),
                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, Spacing.Lg.pt),
                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -Spacing.Lg.pt)
                    ))
                }) {}

                // Footer (separator + total + buy button)
                footerContainer = view(configure = {
                    NSLayoutConstraint.activateConstraints(listOf(
                        topAnchor.constraintEqualToAnchor(stackView.bottomAnchor, Spacing.Lg.pt),
                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, Spacing.Lg.pt),
                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -Spacing.Lg.pt),
                        bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -Spacing.Sm.pt)
                    ))
                }) {
                    // Separator line
                    val footerSep = view(configure = {
                        backgroundColor = StoreColors.SurfaceVariant
                        NSLayoutConstraint.activateConstraints(listOf(
                            topAnchor.constraintEqualToAnchor(parent().topAnchor),
                            leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                            trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                            heightAnchor.constraintEqualToConstant(1.0)
                        ))
                    })

                    // Total (right-aligned)
                    val totalRow = view(configure = {
                        NSLayoutConstraint.activateConstraints(listOf(
                            topAnchor.constraintEqualToAnchor(footerSep.bottomAnchor, Spacing.Lg.pt),
                            leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                            trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                            heightAnchor.constraintEqualToConstant(36.0)
                        ))
                    }) {
                        label {
                            text = "Total:"
                            font = UIFont.systemFontOfSize(FontSize.BodyLarge.pt)
                            textColor = StoreColors.OnSurfaceVariant
                            NSLayoutConstraint.activateConstraints(listOf(
                                centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                                trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -100.0)
                            ))
                        }
                        val totalBadge = view(configure = {
                            backgroundColor = StoreColors.PriceBackground
                            layer.cornerRadius = 10.0
                            NSLayoutConstraint.activateConstraints(listOf(
                                trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                                centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                                heightAnchor.constraintEqualToConstant(36.0)
                            ))
                        }) {
                            totalLabel = label {
                                font = UIFont.boldSystemFontOfSize(18.0)
                                textColor = StoreColors.PriceColor
                                textAlignment = UIK.TextAlignCenter
                                NSLayoutConstraint.activateConstraints(listOf(
                                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, Spacing.Md.pt),
                                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -Spacing.Md.pt)
                                ))
                            }
                        }
                    }

                    // Buttons row: both right-aligned (matching web)
                    val buttonsRow = view(configure = {
                        NSLayoutConstraint.activateConstraints(listOf(
                            topAnchor.constraintEqualToAnchor(totalRow.bottomAnchor, Spacing.Lg.pt),
                            leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                            trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                            bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                            heightAnchor.constraintEqualToConstant(48.0)
                        ))
                    }) {
                        buyButton = button("Buy") {
                            setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                            backgroundColor = StoreColors.PriceColor
                            layer.cornerRadius = Radius.Md.pt
                            titleLabel?.font = UIFont.boldSystemFontOfSize(FontSize.BodyLarge.pt)
                            contentEdgeInsets = UIEdgeInsetsMake(Spacing.Sm.pt, Spacing.Xxl.pt, Spacing.Sm.pt, Spacing.Xxl.pt)
                            addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onBuy")))
                            NSLayoutConstraint.activateConstraints(listOf(
                                trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                                centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                                heightAnchor.constraintEqualToConstant(48.0)
                            ))
                        }
                        button("Continue Shopping") {
                            setTitleColor(StoreColors.Primary, forState = UIControlStateNormal)
                            titleLabel?.font = UIFont.systemFontOfSize(FontSize.Body.pt)
                            setImage(StoreIcons.arrowBack(18.0, StoreColors.Primary), forState = UIControlStateNormal)
                            imageEdgeInsets = UIEdgeInsetsMake(0.0, -Spacing.Xs.pt, 0.0, Spacing.Xs.pt)
                            contentEdgeInsets = UIEdgeInsetsMake(Spacing.Sm.pt, 20.0, Spacing.Sm.pt, 20.0)
                            layer.borderWidth = 1.0
                            layer.borderColor = StoreColors.Primary.CGColor
                            layer.cornerRadius = Radius.Md.pt
                            addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onBack")))
                            NSLayoutConstraint.activateConstraints(listOf(
                                trailingAnchor.constraintEqualToAnchor(buyButton.leadingAnchor, -Spacing.Lg.pt),
                                centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                                heightAnchor.constraintEqualToConstant(48.0)
                            ))
                        }
                    }
                }
            }
        }

        // Empty state (icon + heading + subtitle, below separator)
        emptyContainer = view(configure = {
            hidden = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(separator.bottomAnchor, 48.0),
                centerXAnchor.constraintEqualToAnchor(root.centerXAnchor)
            ))
        }) {
            val iconIv = imageView {
                image = StoreIcons.shoppingCart(48.0, StoreColors.OnSurfaceVariant)
                contentMode = UIK.ContentModeScaleAspectFit
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(parent().topAnchor),
                    centerXAnchor.constraintEqualToAnchor(parent().centerXAnchor),
                    widthAnchor.constraintEqualToConstant(48.0),
                    heightAnchor.constraintEqualToConstant(48.0)
                ))
            }
            label {
                text = "Your cart is empty"
                font = UIFont.boldSystemFontOfSize(18.0)
                textColor = StoreColors.OnSurface
                textAlignment = UIK.TextAlignCenter
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(iconIv.bottomAnchor, Spacing.Lg.pt),
                    centerXAnchor.constraintEqualToAnchor(parent().centerXAnchor)
                ))
            }
            val subtitle = label {
                text = "Add products to start shopping"
                font = UIFont.systemFontOfSize(FontSize.Body.pt)
                textColor = StoreColors.OnSurfaceVariant
                textAlignment = UIK.TextAlignCenter
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(iconIv.bottomAnchor, 40.0),
                    centerXAnchor.constraintEqualToAnchor(parent().centerXAnchor),
                    bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor)
                ))
            }
        }

        // Link backButton below emptyContainer
        NSLayoutConstraint.activateConstraints(listOf(
            backButton.topAnchor.constraintEqualToAnchor(emptyContainer.bottomAnchor, Spacing.Xxl.pt)
        ))

        // Error
        errorLabel = label {
            font = UIFont.systemFontOfSize(FontSize.Body.pt)
            textColor = StoreColors.Error
            numberOfLines = 0
            textAlignment = UIK.TextAlignCenter
            hidden = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(separator.bottomAnchor, Spacing.Sm.pt),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, Spacing.Lg.pt),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -Spacing.Lg.pt)
            ))
        }
    }

    override fun doUpdate() {
        val rows = scope.rows
        // Asked for on every drawing: a row view not asked for is released
        val rowViews = rows.map(::viewOf)

        // Rows (guard by reference: the presenter assigns a new list when the rows change)
        if (rows !== lastRows) {
            lastRows = rows
            val empty = rows.isEmpty()
            emptyContainer.hidden = !empty
            scrollView.hidden = empty
            backButton.hidden = !empty
            syncRows(rowViews)
        }

        // Total (guard), computed by the presenter
        val total = scope.total
        if (total != lastTotal) {
            lastTotal = total
            totalLabel.text = "\$${ViewUtils.formatPrice(total)}"
        }

        // Error (guard)
        val errorMessage = scope.errorMessage
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

    /**
     * Places the row views in [stackView] in the given order. Views that stay are moved, not rebuilt; the views of
     * rows that left are released when this drawing ends (they were not asked for), which takes them off the screen.
     */
    private fun syncRows(next: List<AbstractViewIos<*>>) {
        next.forEachIndexed { index, view ->
            val arranged = stackView.arrangedSubviews
            if (index >= arranged.size || arranged[index] !== view.rootView) {
                stackView.insertArrangedSubview(view.rootView, atIndex = index.toULong())
            }
        }
        stackView.setNeedsLayout()
    }

    internal fun goBack() {
        safeAction("back") { scope.onOpenProducts() }
    }

    internal fun buy() {
        safeAction("buy") { scope.onBuy() }
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
