package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.nativeui.ios.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.UIK
import br.com.wdc.shopping.nativeui.ios.UIKitDom
import br.com.wdc.shopping.nativeui.ios.ShoppingColors
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.UIKit.*
import platform.CoreGraphics.CGRectMake
import platform.objc.sel_registerName
import platform.darwin.NSObject

/**
 * Home view — header bar with tabs (Products/Purchases) + content area + detail slot.
 * Handles responsive layout (compact vs desktop) based on width.
 */
@OptIn(ExperimentalForeignApi::class)
class HomeViewIos(presenter: HomePresenter) : AbstractViewIos<HomePresenter>("home-view", presenter) {

    private lateinit var headerBar: UIView
    private lateinit var logoIcon: UILabel
    private lateinit var titleLabel: UILabel
    private lateinit var nicknameLabel: UILabel
    private lateinit var cartBadgeLabel: UILabel
    private lateinit var cartButton: UIButton
    private lateinit var exitButton: UIButton
    private lateinit var tabsContainer: UIView
    private lateinit var productsTab: UILabel
    private lateinit var purchasesTab: UILabel
    private lateinit var panelsContainer: UIView
    private lateinit var detailContainer: UIView
    private lateinit var errorLabel: UILabel

    private lateinit var detailSlot: ViewSlot
    private var mountedProductsPanel: CubeView? = null
    private var mountedPurchasesPanel: CubeView? = null
    private var selectedTab = 0
    private var lastTab = -1
    private var lastNick: String? = null
    private var lastCartCount = -1
    private val actions = HomeActions(this).also { retainForGC(it) }

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = ShoppingColors.Background

        // Header bar
        headerBar = view(configure = {
            backgroundColor = ShoppingColors.Primary
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                heightAnchor.constraintEqualToConstant(56.0)
            ))
        }) {
            logoIcon = label {
                text = "🛍️"; font = UIFont.systemFontOfSize(20.0)
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 12.0),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                ))
            }
            titleLabel = label {
                text = "Shopping"
                font = UIFont.boldSystemFontOfSize(18.0)
                textColor = UIColor.whiteColor
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(logoIcon.trailingAnchor, 8.0),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                ))
            }
            nicknameLabel = label {
                font = UIFont.systemFontOfSize(14.0)
                textColor = ShoppingColors.WhiteOverlay85
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(titleLabel.trailingAnchor, 16.0),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                ))
            }
            exitButton = button("Sair") {
                setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                titleLabel?.font = UIFont.systemFontOfSize(14.0)
                layer.borderWidth = 1.0
                layer.borderColor = ShoppingColors.WhiteOverlay50.CGColor
                layer.cornerRadius = 6.0
                addTarget(actions, action = sel_registerName("onExitTapped"), forControlEvents = UIControlEventTouchUpInside)
                NSLayoutConstraint.activateConstraints(listOf(
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -12.0),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                    widthAnchor.constraintEqualToConstant(48.0),
                    heightAnchor.constraintEqualToConstant(32.0)
                ))
            }
            cartButton = button("🛒") {
                titleLabel?.font = UIFont.systemFontOfSize(20.0)
                backgroundColor = ShoppingColors.WhiteOverlay20
                layer.cornerRadius = 8.0
                addTarget(actions, action = sel_registerName("onCartTapped"), forControlEvents = UIControlEventTouchUpInside)
                NSLayoutConstraint.activateConstraints(listOf(
                    trailingAnchor.constraintEqualToAnchor(exitButton.leadingAnchor, -8.0),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                    widthAnchor.constraintEqualToConstant(40.0),
                    heightAnchor.constraintEqualToConstant(36.0)
                ))
            }
            cartBadgeLabel = label {
                font = UIFont.boldSystemFontOfSize(11.0)
                textColor = ShoppingColors.OnPrimaryContainer
                backgroundColor = ShoppingColors.PrimaryContainer
                textAlignment = UIK.TextAlignCenter
                layer.cornerRadius = 9.0
                clipsToBounds = true
                hidden = true
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(cartButton.topAnchor, -4.0),
                    trailingAnchor.constraintEqualToAnchor(cartButton.trailingAnchor, 4.0),
                    widthAnchor.constraintEqualToConstant(18.0),
                    heightAnchor.constraintEqualToConstant(18.0)
                ))
            }
        }

        // Tabs row
        tabsContainer = view(configure = {
            backgroundColor = ShoppingColors.Primary
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(headerBar.bottomAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                heightAnchor.constraintEqualToConstant(44.0)
            ))
        }) {
            productsTab = label {
                text = "Produtos"
                textColor = UIColor.whiteColor
                font = UIFont.boldSystemFontOfSize(14.0)
                userInteractionEnabled = true
                addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onProductsTab")))
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                    topAnchor.constraintEqualToAnchor(parent().topAnchor),
                    bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor)
                ))
            }
            purchasesTab = label {
                text = "Compras"
                textColor = ShoppingColors.WhiteOverlay85
                font = UIFont.systemFontOfSize(14.0)
                userInteractionEnabled = true
                addGestureRecognizer(UITapGestureRecognizer(target = actions, action = sel_registerName("onPurchasesTab")))
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(productsTab.trailingAnchor, 24.0),
                    topAnchor.constraintEqualToAnchor(parent().topAnchor),
                    bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor)
                ))
            }
        }

        // Panels container (shows products or purchases panel)
        panelsContainer = view(configure = {
            backgroundColor = ShoppingColors.Background
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(tabsContainer.bottomAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        })

        // Detail container (overlays when product/cart/receipt is open)
        detailContainer = view(configure = {
            backgroundColor = ShoppingColors.Background
            hidden = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(headerBar.bottomAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        })

        // Error label
        errorLabel = label {
            textColor = ShoppingColors.Error
            font = UIFont.systemFontOfSize(14.0)
            numberOfLines = 0
            textAlignment = UIK.TextAlignCenter
            hidden = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(tabsContainer.bottomAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0)
            ))
        }

        // Register view slots
        detailSlot = newViewSlot(detailContainer)
    }

    /** Mount a panel view into panelsContainer once, pin to edges, return its rootView */
    private fun mountPanel(panel: CubeView): UIView {
        val childView = (panel as AbstractViewIos<*>).rootView
        childView.translatesAutoresizingMaskIntoConstraints = false
        panelsContainer.addSubview(childView)
        NSLayoutConstraint.activateConstraints(listOf(
            childView.topAnchor.constraintEqualToAnchor(panelsContainer.topAnchor),
            childView.leadingAnchor.constraintEqualToAnchor(panelsContainer.leadingAnchor),
            childView.trailingAnchor.constraintEqualToAnchor(panelsContainer.trailingAnchor),
            childView.bottomAnchor.constraintEqualToAnchor(panelsContainer.bottomAnchor)
        ))
        return childView
    }

    override fun doUpdate() {
        val state = presenter.state

        // Guard: Nickname
        val nick = state.nickName
        if (nick != lastNick) {
            lastNick = nick
            nicknameLabel.text = if (!nick.isNullOrBlank()) "Olá, $nick" else ""
        }

        // Guard: Cart badge
        val cartCount = state.cartItemCount
        if (cartCount != lastCartCount) {
            lastCartCount = cartCount
            if (cartCount > 0) {
                cartBadgeLabel.text = cartCount.toString()
                cartBadgeLabel.hidden = false
            } else {
                cartBadgeLabel.hidden = true
            }
        }

        // Panels (tab content) — mount once, then show/hide
        val productsPanel = state.productsPanelView
        val purchasesPanel = state.purchasesPanelView

        // Mount panels on first appearance (only once), set visibility immediately
        if (productsPanel != null && mountedProductsPanel !== productsPanel) {
            mountedProductsPanel = productsPanel
            val view = mountPanel(productsPanel)
            view.hidden = (selectedTab != 0)
        }
        if (purchasesPanel != null && mountedPurchasesPanel !== purchasesPanel) {
            mountedPurchasesPanel = purchasesPanel
            val view = mountPanel(purchasesPanel)
            view.hidden = (selectedTab != 1)
        }

        // Toggle visibility based on selected tab
        if (selectedTab != lastTab) {
            lastTab = selectedTab
            (mountedProductsPanel as? AbstractViewIos<*>)?.rootView?.hidden = (selectedTab != 0)
            (mountedPurchasesPanel as? AbstractViewIos<*>)?.rootView?.hidden = (selectedTab != 1)
            updateTabAppearance()
        }

        // Detail view (content slot)
        val newContentView = state.contentView
        detailSlot.sync(newContentView)
        detailContainer.hidden = (newContentView == null)

        // Error message
        val errorMessage = state.errorMessage
        if (!errorMessage.isNullOrBlank()) {
            errorLabel.text = errorMessage
            errorLabel.hidden = false
        } else {
            errorLabel.hidden = true
        }
    }

    private fun updateTabAppearance() {
        productsTab.textColor = if (selectedTab == 0) UIColor.whiteColor else ShoppingColors.WhiteOverlay85
        productsTab.font = if (selectedTab == 0) UIFont.boldSystemFontOfSize(14.0) else UIFont.systemFontOfSize(14.0)
        purchasesTab.textColor = if (selectedTab == 1) UIColor.whiteColor else ShoppingColors.WhiteOverlay85
        purchasesTab.font = if (selectedTab == 1) UIFont.boldSystemFontOfSize(14.0) else UIFont.systemFontOfSize(14.0)
    }

    internal fun switchToProducts() {
        if (selectedTab != 0) {
            selectedTab = 0
            lastTab = 0
            (mountedProductsPanel as? AbstractViewIos<*>)?.rootView?.hidden = false
            (mountedPurchasesPanel as? AbstractViewIos<*>)?.rootView?.hidden = true
            updateTabAppearance()
        }
    }

    internal fun switchToPurchases() {
        if (selectedTab != 1) {
            selectedTab = 1
            lastTab = 1
            (mountedProductsPanel as? AbstractViewIos<*>)?.rootView?.hidden = true
            (mountedPurchasesPanel as? AbstractViewIos<*>)?.rootView?.hidden = false
            updateTabAppearance()
        }
    }

    internal fun openCart() {
        safeAction("openCart") { presenter.onOpenCart() }
    }

    internal fun exit() {
        safeAction("exit") { presenter.onExit() }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class HomeActions(private val view: HomeViewIos) : NSObject() {

    @ObjCAction
    fun onProductsTab() {
        view.switchToProducts()
    }

    @ObjCAction
    fun onPurchasesTab() {
        view.switchToPurchases()
    }

    @ObjCAction
    fun onCartTapped() {
        view.openCart()
    }

    @ObjCAction
    fun onExitTapped() {
        view.exit()
    }
}
