package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.toolkit.UIKitDom
import br.com.wdc.shopping.nativeui.ios.toolkit.ViewUtils
import br.com.wdc.shopping.nativeui.ios.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.theme.ShoppingIcons
import br.com.wdc.shopping.nativeui.ios.theme.UIK
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.UIKit.*
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Cart view — the rows, the total, and the buy button.
 * Each row is a view of its own ([CartItemViewIos], from the catalog): the cart only places them, in order.
 * A row that stays keeps its view; the presenter releases the views of the rows that leave.
 */
@OptIn(ExperimentalForeignApi::class)
class CartViewIos(app: RootPresenter, scope: CartScope) : AbstractViewIos<CartScope>("cart-view", app, scope) {

    private lateinit var scrollView: UIScrollView
    private lateinit var stackView: UIStackView
    private lateinit var emptyContainer: UIView
    private lateinit var totalLabel: UILabel
    private lateinit var buyButton: UIButton
    private lateinit var backButton: UIButton
    private lateinit var errorLabel: UILabel
    private lateinit var footerContainer: UIView

    /** The row views currently in [stackView], in order. */
    private var rowViews: List<CubeView> = emptyList()

    private val actions = CartActions(this).also { retainForGC(it) }

    // Guards
    private var lastRows: List<CartItemScope>? = null
    private var lastTotal: Double = -1.0
    private var lastError: String? = null

    private lateinit var titleLabel: UILabel

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = ShoppingColors.Background

        // Title header
        titleLabel = label {
            text = "Shopping Cart"
            font = UIFont.systemFontOfSize(24.0, UIFontWeightMedium)
            textColor = ShoppingColors.OnSurface
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor, 16.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0)
            ))
        }

        // Separator
        val separator = view(configure = {
            backgroundColor = ShoppingColors.SurfaceVariant
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(titleLabel.bottomAnchor, 12.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0),
                heightAnchor.constraintEqualToConstant(1.0)
            ))
        })

        // Back button (visible in empty state, aligned right below empty message)
        backButton = button("Continue Shopping") {
            setTitleColor(ShoppingColors.Primary, forState = UIControlStateNormal)
            titleLabel?.font = UIFont.systemFontOfSize(14.0)
            setImage(ShoppingIcons.arrowBack(18.0, ShoppingColors.Primary), forState = UIControlStateNormal)
            imageEdgeInsets = UIEdgeInsetsMake(0.0, -4.0, 0.0, 4.0)
            contentEdgeInsets = UIEdgeInsetsMake(8.0, 20.0, 8.0, 20.0)
            layer.borderWidth = 1.0
            layer.borderColor = ShoppingColors.Primary.CGColor
            layer.cornerRadius = 12.0
            addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onBack")))
            NSLayoutConstraint.activateConstraints(listOf(
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0),
                heightAnchor.constraintEqualToConstant(48.0)
            ))
        }

        // Scroll area (items + footer flow together)
        scrollView = scrollView(configure = {
            delaysContentTouches = false
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(separator.bottomAnchor, 8.0),
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
                        topAnchor.constraintEqualToAnchor(parent().topAnchor, 8.0),
                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0)
                    ))
                }) {}

                // Footer (separator + total + buy button)
                footerContainer = view(configure = {
                    NSLayoutConstraint.activateConstraints(listOf(
                        topAnchor.constraintEqualToAnchor(stackView.bottomAnchor, 16.0),
                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0),
                        bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -8.0)
                    ))
                }) {
                    // Separator line
                    val footerSep = view(configure = {
                        backgroundColor = ShoppingColors.SurfaceVariant
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
                            topAnchor.constraintEqualToAnchor(footerSep.bottomAnchor, 16.0),
                            leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                            trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                            heightAnchor.constraintEqualToConstant(36.0)
                        ))
                    }) {
                        label {
                            text = "Total:"
                            font = UIFont.systemFontOfSize(16.0)
                            textColor = ShoppingColors.OnSurfaceVariant
                            NSLayoutConstraint.activateConstraints(listOf(
                                centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                                trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -100.0)
                            ))
                        }
                        val totalBadge = view(configure = {
                            backgroundColor = ShoppingColors.PriceBackground
                            layer.cornerRadius = 10.0
                            NSLayoutConstraint.activateConstraints(listOf(
                                trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                                centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                                heightAnchor.constraintEqualToConstant(36.0)
                            ))
                        }) {
                            totalLabel = label {
                                font = UIFont.boldSystemFontOfSize(18.0)
                                textColor = ShoppingColors.PriceColor
                                textAlignment = UIK.TextAlignCenter
                                NSLayoutConstraint.activateConstraints(listOf(
                                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 12.0),
                                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -12.0)
                                ))
                            }
                        }
                    }

                    // Buttons row: both right-aligned (matching web)
                    val buttonsRow = view(configure = {
                        NSLayoutConstraint.activateConstraints(listOf(
                            topAnchor.constraintEqualToAnchor(totalRow.bottomAnchor, 16.0),
                            leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                            trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                            bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                            heightAnchor.constraintEqualToConstant(48.0)
                        ))
                    }) {
                        buyButton = button("Buy") {
                            setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                            backgroundColor = ShoppingColors.PriceColor
                            layer.cornerRadius = 12.0
                            titleLabel?.font = UIFont.boldSystemFontOfSize(16.0)
                            contentEdgeInsets = UIEdgeInsetsMake(8.0, 32.0, 8.0, 32.0)
                            addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onBuy")))
                            NSLayoutConstraint.activateConstraints(listOf(
                                trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                                centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                                heightAnchor.constraintEqualToConstant(48.0)
                            ))
                        }
                        button("Continue Shopping") {
                            setTitleColor(ShoppingColors.Primary, forState = UIControlStateNormal)
                            titleLabel?.font = UIFont.systemFontOfSize(14.0)
                            setImage(ShoppingIcons.arrowBack(18.0, ShoppingColors.Primary), forState = UIControlStateNormal)
                            imageEdgeInsets = UIEdgeInsetsMake(0.0, -4.0, 0.0, 4.0)
                            contentEdgeInsets = UIEdgeInsetsMake(8.0, 20.0, 8.0, 20.0)
                            layer.borderWidth = 1.0
                            layer.borderColor = ShoppingColors.Primary.CGColor
                            layer.cornerRadius = 12.0
                            addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onBack")))
                            NSLayoutConstraint.activateConstraints(listOf(
                                trailingAnchor.constraintEqualToAnchor(buyButton.leadingAnchor, -16.0),
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
                image = ShoppingIcons.shoppingCart(48.0, ShoppingColors.OnSurfaceVariant)
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
                textColor = ShoppingColors.OnSurface
                textAlignment = UIK.TextAlignCenter
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(iconIv.bottomAnchor, 16.0),
                    centerXAnchor.constraintEqualToAnchor(parent().centerXAnchor)
                ))
            }
            val subtitle = label {
                text = "Add products to start shopping"
                font = UIFont.systemFontOfSize(14.0)
                textColor = ShoppingColors.OnSurfaceVariant
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
            backButton.topAnchor.constraintEqualToAnchor(emptyContainer.bottomAnchor, 32.0)
        ))

        // Error
        errorLabel = label {
            font = UIFont.systemFontOfSize(14.0)
            textColor = ShoppingColors.Error
            numberOfLines = 0
            textAlignment = UIK.TextAlignCenter
            hidden = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(separator.bottomAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0)
            ))
        }
    }

    override fun doUpdate() {
        val rows = scope.rows

        // Rows (guard by reference: the presenter assigns a new list when the rows change)
        if (rows !== lastRows) {
            lastRows = rows
            val empty = rows.isEmpty()
            emptyContainer.hidden = !empty
            scrollView.hidden = empty
            backButton.hidden = !empty
            syncRows(rows.map(app::viewOf))
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
     * Places the row views in [stackView] in the given order. Views that stay are moved, not rebuilt;
     * views that left are only detached (releasing them is the presenter's job, through `app.releaseView`).
     */
    private fun syncRows(views: List<CubeView>) {
        val next = views.filterIsInstance<AbstractViewIos<*>>()
        for (old in rowViews) {
            if (next.none { it === old }) (old as? AbstractViewIos<*>)?.rootView?.removeFromSuperview()
        }
        next.forEachIndexed { index, view ->
            val arranged = stackView.arrangedSubviews
            if (index >= arranged.size || arranged[index] !== view.rootView) {
                stackView.insertArrangedSubview(view.rootView, atIndex = index.toULong())
            }
        }
        rowViews = next
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
