package br.com.wdc.shopping.nativeui.ios.views

import br.com.wdc.framework.cube.CubeView
import br.com.wdc.shopping.nativeui.ios.toolkit.AbstractViewIos
import br.com.wdc.shopping.nativeui.ios.toolkit.UIKitDom
import br.com.wdc.shopping.nativeui.ios.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.ios.theme.ShoppingIcons
import br.com.wdc.shopping.nativeui.ios.theme.UIK
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.UIKit.*
import platform.CoreGraphics.CGRectMake
import platform.objc.sel_registerName
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNotification
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.UIKit.UIDevice

private const val WIDE_BREAKPOINT = 700.0

/**
 * Home view — responsive layout:
 * - Compact (<700pt): 2-row header + tabs + single panel
 * - Wide (>=700pt): 1-row header + side-by-side panels
 */
@OptIn(ExperimentalForeignApi::class)
class HomeViewIos(presenter: HomePresenter) : AbstractViewIos<HomePresenter>("home-view", presenter) {

    // Compact header UI
    private lateinit var compactHeaderContent: UIView
    private lateinit var nicknameLabel: UILabel
    private lateinit var cartBadgeLabel: UILabel
    private lateinit var cartButton: UIButton
    private lateinit var exitButton: UIButton

    // Wide header UI
    private lateinit var wideHeaderContent: UIView
    private lateinit var wideNicknameLabel: UILabel
    private lateinit var wideCartButton: UIButton
    private lateinit var wideCartBadgeLabel: UILabel
    private lateinit var wideExitButton: UIButton

    // Tabs (compact only)
    private lateinit var tabsContainer: UIView
    private lateinit var productsTab: UIButton
    private lateinit var purchasesTab: UIButton

    // Content areas
    private lateinit var headerBar: UIView
    private lateinit var compactPanelsContainer: UIView
    private lateinit var widePanelsContainer: UIView
    private lateinit var wideProductsPanel: UIView
    private lateinit var widePurchasesPanel: UIView
    private lateinit var detailContainer: UIView
    private lateinit var errorLabel: UILabel

    private lateinit var detailSlot: ViewSlot
    private var mountedProductsPanel: CubeView? = null
    private var mountedPurchasesPanel: CubeView? = null
    private var selectedTab = 0
    private var lastTab = -1
    private var lastNick: String? = null
    private var lastCartCount = -1
    private var isWide = false
    private var orientationObserver: Any? = null
    private lateinit var headerHeightConstraint: NSLayoutConstraint
    private val actions = HomeActions(this).also { retainForGC(it) }

    override fun createView(): UIView = UIKitDom.build {
        val root = parent()
        root.backgroundColor = ShoppingColors.Background

        // === HEADER BAR ===
        headerBar = view(configure = {
            backgroundColor = ShoppingColors.Primary
            headerHeightConstraint = heightAnchor.constraintEqualToConstant(88.0)
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(root.topAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                headerHeightConstraint
            ))
        }) {
            // --- COMPACT header (2 rows) ---
            compactHeaderContent = view(configure = {
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(parent().topAnchor),
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor)
                ))
            }) {
                val topRow = view(configure = {
                    NSLayoutConstraint.activateConstraints(listOf(
                        topAnchor.constraintEqualToAnchor(parent().topAnchor, 8.0),
                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 12.0),
                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -12.0),
                        heightAnchor.constraintEqualToConstant(32.0)
                    ))
                }) {
                    val logoAvatar = view(configure = {
                        backgroundColor = ShoppingColors.WhiteOverlay20
                        layer.cornerRadius = 8.0
                        NSLayoutConstraint.activateConstraints(listOf(
                            leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                            centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                            widthAnchor.constraintEqualToConstant(32.0),
                            heightAnchor.constraintEqualToConstant(32.0)
                        ))
                    }) {
                        val logoIv = imageView {
                            image = ShoppingIcons.localMall(20.0, UIColor.whiteColor)
                            contentMode = UIK.ContentModeScaleAspectFit
                        }
                        center(logoIv)
                        size(logoIv, width = 20.0, height = 20.0)
                    }
                    label {
                        text = "Shopping"
                        font = UIFont.boldSystemFontOfSize(20.0)
                        textColor = UIColor.whiteColor
                        NSLayoutConstraint.activateConstraints(listOf(
                            leadingAnchor.constraintEqualToAnchor(logoAvatar.trailingAnchor, 8.0),
                            centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                        ))
                    }
                    exitButton = button("Sair") {
                        setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                        titleLabel?.font = UIFont.systemFontOfSize(13.0)
                        layer.borderWidth = 1.0
                        layer.borderColor = ShoppingColors.WhiteOverlay50.CGColor
                        layer.cornerRadius = 10.0
                        contentEdgeInsets = UIEdgeInsetsMake(4.0, 12.0, 4.0, 12.0)
                        addTarget(actions, action = sel_registerName("onExitTapped"), forControlEvents = UIControlEventTouchUpInside)
                        NSLayoutConstraint.activateConstraints(listOf(
                            trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                            centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                            heightAnchor.constraintEqualToConstant(30.0)
                        ))
                    }
                }
                val bottomRow = view(configure = {
                    NSLayoutConstraint.activateConstraints(listOf(
                        topAnchor.constraintEqualToAnchor(topRow.bottomAnchor, 8.0),
                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 12.0),
                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -12.0),
                        heightAnchor.constraintEqualToConstant(32.0),
                        bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -8.0)
                    ))
                }) {
                    nicknameLabel = label {
                        font = UIFont.systemFontOfSize(14.0)
                        textColor = ShoppingColors.WhiteOverlay85
                        NSLayoutConstraint.activateConstraints(listOf(
                            leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                            centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                        ))
                    }
                    cartButton = button("Carrinho") {
                        setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                        titleLabel?.font = UIFont.systemFontOfSize(13.0)
                        backgroundColor = ShoppingColors.WhiteOverlay20
                        layer.cornerRadius = 10.0
                        contentEdgeInsets = UIEdgeInsetsMake(4.0, 12.0, 4.0, 12.0)
                        setImage(ShoppingIcons.shoppingCart(16.0, UIColor.whiteColor), forState = UIControlStateNormal)
                        imageEdgeInsets = UIEdgeInsetsMake(0.0, -4.0, 0.0, 4.0)
                        addTarget(actions, action = sel_registerName("onCartTapped"), forControlEvents = UIControlEventTouchUpInside)
                        NSLayoutConstraint.activateConstraints(listOf(
                            trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                            centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                            heightAnchor.constraintEqualToConstant(30.0)
                        ))
                    }
                    cartBadgeLabel = label {
                        font = UIFont.boldSystemFontOfSize(11.0)
                        textColor = UIColor.whiteColor
                        backgroundColor = ShoppingColors.Error
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
            }

            // --- WIDE header (single row) ---
            wideHeaderContent = view(configure = {
                hidden = true
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(parent().topAnchor),
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                    heightAnchor.constraintEqualToConstant(56.0)
                ))
            }) {
                val wideLogo = view(configure = {
                    backgroundColor = ShoppingColors.WhiteOverlay20
                    layer.cornerRadius = 10.0
                    NSLayoutConstraint.activateConstraints(listOf(
                        leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 24.0),
                        centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor),
                        widthAnchor.constraintEqualToConstant(36.0),
                        heightAnchor.constraintEqualToConstant(36.0)
                    ))
                }) {
                    val logoIv = imageView {
                        image = ShoppingIcons.localMall(22.0, UIColor.whiteColor)
                        contentMode = UIK.ContentModeScaleAspectFit
                    }
                    center(logoIv)
                    size(logoIv, width = 22.0, height = 22.0)
                }
                label {
                    text = "Shopping"
                    font = UIFont.boldSystemFontOfSize(18.0)
                    textColor = UIColor.whiteColor
                    NSLayoutConstraint.activateConstraints(listOf(
                        leadingAnchor.constraintEqualToAnchor(wideLogo.trailingAnchor, 12.0),
                        centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                    ))
                }

                wideExitButton = button("Sair") {
                    setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                    titleLabel?.font = UIFont.systemFontOfSize(14.0)
                    layer.borderWidth = 1.0
                    layer.borderColor = ShoppingColors.WhiteOverlay50.CGColor
                    layer.cornerRadius = 12.0
                    contentEdgeInsets = UIEdgeInsetsMake(6.0, 16.0, 6.0, 16.0)
                    addTarget(actions, action = sel_registerName("onExitTapped"), forControlEvents = UIControlEventTouchUpInside)
                    NSLayoutConstraint.activateConstraints(listOf(
                        trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -24.0),
                        centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                    ))
                }

                wideCartButton = button("Carrinho") {
                    setTitleColor(UIColor.whiteColor, forState = UIControlStateNormal)
                    titleLabel?.font = UIFont.systemFontOfSize(14.0)
                    backgroundColor = ShoppingColors.WhiteOverlay20
                    layer.cornerRadius = 12.0
                    contentEdgeInsets = UIEdgeInsetsMake(6.0, 14.0, 6.0, 14.0)
                    setImage(ShoppingIcons.shoppingCart(18.0, UIColor.whiteColor), forState = UIControlStateNormal)
                    imageEdgeInsets = UIEdgeInsetsMake(0.0, -4.0, 0.0, 4.0)
                    addTarget(actions, action = sel_registerName("onCartTapped"), forControlEvents = UIControlEventTouchUpInside)
                    NSLayoutConstraint.activateConstraints(listOf(
                        trailingAnchor.constraintEqualToAnchor(wideExitButton.leadingAnchor, -8.0),
                        centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                    ))
                }
                wideCartBadgeLabel = label {
                    font = UIFont.boldSystemFontOfSize(11.0)
                    textColor = UIColor.whiteColor
                    backgroundColor = ShoppingColors.Error
                    textAlignment = UIK.TextAlignCenter
                    layer.cornerRadius = 9.0
                    clipsToBounds = true
                    hidden = true
                    NSLayoutConstraint.activateConstraints(listOf(
                        topAnchor.constraintEqualToAnchor(wideCartButton.topAnchor, -4.0),
                        trailingAnchor.constraintEqualToAnchor(wideCartButton.trailingAnchor, 4.0),
                        widthAnchor.constraintEqualToConstant(18.0),
                        heightAnchor.constraintEqualToConstant(18.0)
                    ))
                }

                val greetingPill = view(configure = {
                    backgroundColor = ShoppingColors.WhiteOverlay15
                    layer.cornerRadius = 20.0
                    NSLayoutConstraint.activateConstraints(listOf(
                        trailingAnchor.constraintEqualToAnchor(wideCartButton.leadingAnchor, -16.0),
                        centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                    ))
                }) {
                    wideNicknameLabel = label {
                        font = UIFont.systemFontOfSize(14.0)
                        textColor = UIColor.whiteColor
                        NSLayoutConstraint.activateConstraints(listOf(
                            topAnchor.constraintEqualToAnchor(parent().topAnchor, 8.0),
                            bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor, -8.0),
                            leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                            trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor, -16.0)
                        ))
                    }
                }
            }
        }

        // === COMPACT: Tabs row ===
        tabsContainer = view(configure = {
            backgroundColor = ShoppingColors.Background
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(headerBar.bottomAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                heightAnchor.constraintEqualToConstant(48.0)
            ))
        }) {
            productsTab = button("PRODUTOS") {
                setTitleColor(ShoppingColors.Primary, forState = UIControlStateNormal)
                titleLabel?.font = UIFont.boldSystemFontOfSize(14.0)
                setImage(ShoppingIcons.shoppingBag(22.0, ShoppingColors.Primary), forState = UIControlStateNormal)
                imageEdgeInsets = UIEdgeInsetsMake(0.0, -6.0, 0.0, 6.0)
                addTarget(actions, action = sel_registerName("onProductsTab"), forControlEvents = UIControlEventTouchUpInside)
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor, 16.0),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                ))
            }
            purchasesTab = button("COMPRAS") {
                setTitleColor(ShoppingColors.OnSurfaceVariant, forState = UIControlStateNormal)
                titleLabel?.font = UIFont.systemFontOfSize(14.0)
                setImage(ShoppingIcons.inventory(22.0, ShoppingColors.OnSurfaceVariant), forState = UIControlStateNormal)
                imageEdgeInsets = UIEdgeInsetsMake(0.0, -6.0, 0.0, 6.0)
                addTarget(actions, action = sel_registerName("onPurchasesTab"), forControlEvents = UIControlEventTouchUpInside)
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(productsTab.trailingAnchor, 24.0),
                    centerYAnchor.constraintEqualToAnchor(parent().centerYAnchor)
                ))
            }
            view(configure = {
                backgroundColor = ShoppingColors.SurfaceVariant
                NSLayoutConstraint.activateConstraints(listOf(
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                    bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                    heightAnchor.constraintEqualToConstant(1.0)
                ))
            })
        }

        // === COMPACT: Single panel container (tabbed) ===
        compactPanelsContainer = view(configure = {
            backgroundColor = ShoppingColors.Background
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(tabsContainer.bottomAnchor),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor)
            ))
        })

        // === WIDE: Side-by-side panels ===
        widePanelsContainer = view(configure = {
            backgroundColor = ShoppingColors.Background
            hidden = true
            NSLayoutConstraint.activateConstraints(listOf(
                topAnchor.constraintEqualToAnchor(headerBar.bottomAnchor, 16.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0),
                bottomAnchor.constraintEqualToAnchor(root.bottomAnchor, -16.0)
            ))
        }) {
            wideProductsPanel = view(configure = {
                backgroundColor = UIColor.whiteColor
                layer.cornerRadius = 8.0
                clipsToBounds = true
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(parent().topAnchor),
                    leadingAnchor.constraintEqualToAnchor(parent().leadingAnchor),
                    bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                    widthAnchor.constraintEqualToAnchor(parent().widthAnchor, 0.6, -8.0)
                ))
            })
            widePurchasesPanel = view(configure = {
                backgroundColor = UIColor.whiteColor
                layer.cornerRadius = 8.0
                clipsToBounds = true
                NSLayoutConstraint.activateConstraints(listOf(
                    topAnchor.constraintEqualToAnchor(parent().topAnchor),
                    trailingAnchor.constraintEqualToAnchor(parent().trailingAnchor),
                    bottomAnchor.constraintEqualToAnchor(parent().bottomAnchor),
                    leadingAnchor.constraintEqualToAnchor(wideProductsPanel.trailingAnchor, 16.0)
                ))
            })
        }

        // === Detail container (overlay) ===
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
                topAnchor.constraintEqualToAnchor(headerBar.bottomAnchor, 8.0),
                leadingAnchor.constraintEqualToAnchor(root.leadingAnchor, 16.0),
                trailingAnchor.constraintEqualToAnchor(root.trailingAnchor, -16.0)
            ))
        }

        detailSlot = newViewSlot(detailContainer)

        // Observe orientation/size changes to re-detect layout
        UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
        orientationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIDeviceOrientationDidChangeNotification,
            `object` = null,
            queue = null
        ) { _ ->
            dispatch_async(dispatch_get_main_queue()) {
                this@HomeViewIos.detectLayout()
            }
        }
    }

    override fun release() {
        orientationObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
        }
        orientationObserver = null
        super.release()
    }

    // --- Responsive layout ---

    private var layoutCheckScheduled = false

    private fun detectLayout() {
        val width = rootView.bounds.useContents { size.width }
        if (width == 0.0) {
            // View not yet in hierarchy; re-check after layout pass
            if (!layoutCheckScheduled) {
                layoutCheckScheduled = true
                dispatch_async(dispatch_get_main_queue()) {
                    layoutCheckScheduled = false
                    detectLayout()
                }
            }
            return
        }
        val shouldBeWide = width >= WIDE_BREAKPOINT
        if (shouldBeWide != isWide) {
            isWide = shouldBeWide
            applyLayout()
        }
    }

    private fun applyLayout() {
        if (isWide) {
            headerHeightConstraint.constant = 56.0
            compactHeaderContent.hidden = true
            wideHeaderContent.hidden = false
            tabsContainer.hidden = true
            compactPanelsContainer.hidden = true
            widePanelsContainer.hidden = false
            remountPanelsForWide()
        } else {
            headerHeightConstraint.constant = 88.0
            compactHeaderContent.hidden = false
            wideHeaderContent.hidden = true
            tabsContainer.hidden = false
            compactPanelsContainer.hidden = false
            widePanelsContainer.hidden = true
            remountPanelsForCompact()
        }
    }

    private fun remountPanelsForWide() {
        val prodView = (mountedProductsPanel as? AbstractViewIos<*>)?.rootView ?: return
        val purchView = (mountedPurchasesPanel as? AbstractViewIos<*>)?.rootView ?: return

        prodView.removeFromSuperview()
        purchView.removeFromSuperview()
        prodView.hidden = false
        purchView.hidden = false

        wideProductsPanel.addSubview(prodView)
        NSLayoutConstraint.activateConstraints(listOf(
            prodView.topAnchor.constraintEqualToAnchor(wideProductsPanel.topAnchor),
            prodView.leadingAnchor.constraintEqualToAnchor(wideProductsPanel.leadingAnchor),
            prodView.trailingAnchor.constraintEqualToAnchor(wideProductsPanel.trailingAnchor),
            prodView.bottomAnchor.constraintEqualToAnchor(wideProductsPanel.bottomAnchor)
        ))

        widePurchasesPanel.addSubview(purchView)
        NSLayoutConstraint.activateConstraints(listOf(
            purchView.topAnchor.constraintEqualToAnchor(widePurchasesPanel.topAnchor),
            purchView.leadingAnchor.constraintEqualToAnchor(widePurchasesPanel.leadingAnchor),
            purchView.trailingAnchor.constraintEqualToAnchor(widePurchasesPanel.trailingAnchor),
            purchView.bottomAnchor.constraintEqualToAnchor(widePurchasesPanel.bottomAnchor)
        ))
    }

    private fun remountPanelsForCompact() {
        val prodView = (mountedProductsPanel as? AbstractViewIos<*>)?.rootView ?: return
        val purchView = (mountedPurchasesPanel as? AbstractViewIos<*>)?.rootView ?: return

        prodView.removeFromSuperview()
        purchView.removeFromSuperview()

        compactPanelsContainer.addSubview(prodView)
        NSLayoutConstraint.activateConstraints(listOf(
            prodView.topAnchor.constraintEqualToAnchor(compactPanelsContainer.topAnchor),
            prodView.leadingAnchor.constraintEqualToAnchor(compactPanelsContainer.leadingAnchor),
            prodView.trailingAnchor.constraintEqualToAnchor(compactPanelsContainer.trailingAnchor),
            prodView.bottomAnchor.constraintEqualToAnchor(compactPanelsContainer.bottomAnchor)
        ))

        compactPanelsContainer.addSubview(purchView)
        NSLayoutConstraint.activateConstraints(listOf(
            purchView.topAnchor.constraintEqualToAnchor(compactPanelsContainer.topAnchor),
            purchView.leadingAnchor.constraintEqualToAnchor(compactPanelsContainer.leadingAnchor),
            purchView.trailingAnchor.constraintEqualToAnchor(compactPanelsContainer.trailingAnchor),
            purchView.bottomAnchor.constraintEqualToAnchor(compactPanelsContainer.bottomAnchor)
        ))

        prodView.hidden = (selectedTab != 0)
        purchView.hidden = (selectedTab != 1)
    }

    private fun mountPanel(panel: CubeView, isProducts: Boolean): UIView {
        val childView = (panel as AbstractViewIos<*>).rootView
        childView.translatesAutoresizingMaskIntoConstraints = false

        val container = if (isWide) {
            if (isProducts) wideProductsPanel else widePurchasesPanel
        } else {
            compactPanelsContainer
        }

        container.addSubview(childView)
        NSLayoutConstraint.activateConstraints(listOf(
            childView.topAnchor.constraintEqualToAnchor(container.topAnchor),
            childView.leadingAnchor.constraintEqualToAnchor(container.leadingAnchor),
            childView.trailingAnchor.constraintEqualToAnchor(container.trailingAnchor),
            childView.bottomAnchor.constraintEqualToAnchor(container.bottomAnchor)
        ))
        return childView
    }

    override fun doUpdate() {
        detectLayout()

        val state = presenter.state

        // Nickname
        val nick = state.nickName
        if (nick != lastNick) {
            lastNick = nick
            val greeting = if (!nick.isNullOrBlank()) "Olá, $nick" else ""
            nicknameLabel.text = greeting
            wideNicknameLabel.text = greeting
        }

        // Cart badge
        val cartCount = state.cartItemCount
        if (cartCount != lastCartCount) {
            lastCartCount = cartCount
            val show = cartCount > 0
            val text = cartCount.toString()
            cartBadgeLabel.text = text
            cartBadgeLabel.hidden = !show
            wideCartBadgeLabel.text = text
            wideCartBadgeLabel.hidden = !show
        }

        // Mount panels
        val productsPanel = state.productsPanelView
        val purchasesPanel = state.purchasesPanelView

        if (productsPanel != null && mountedProductsPanel !== productsPanel) {
            mountedProductsPanel = productsPanel
            val view = mountPanel(productsPanel, isProducts = true)
            if (!isWide) view.hidden = (selectedTab != 0)
        }
        if (purchasesPanel != null && mountedPurchasesPanel !== purchasesPanel) {
            mountedPurchasesPanel = purchasesPanel
            val view = mountPanel(purchasesPanel, isProducts = false)
            if (!isWide) view.hidden = (selectedTab != 1)
        }

        // Tab visibility (compact only)
        if (!isWide && selectedTab != lastTab) {
            lastTab = selectedTab
            (mountedProductsPanel as? AbstractViewIos<*>)?.rootView?.hidden = (selectedTab != 0)
            (mountedPurchasesPanel as? AbstractViewIos<*>)?.rootView?.hidden = (selectedTab != 1)
            updateTabAppearance()
        }

        // Detail overlay
        val newContentView = state.contentView
        detailSlot.sync(newContentView)
        detailContainer.hidden = (newContentView == null)

        // Error
        val errorMessage = state.errorMessage
        if (!errorMessage.isNullOrBlank()) {
            errorLabel.text = errorMessage
            errorLabel.hidden = false
        } else {
            errorLabel.hidden = true
        }
    }

    private fun updateTabAppearance() {
        val prodColor = if (selectedTab == 0) ShoppingColors.Primary else ShoppingColors.OnSurfaceVariant
        productsTab.setTitleColor(prodColor, forState = UIControlStateNormal)
        productsTab.titleLabel?.font = if (selectedTab == 0) UIFont.boldSystemFontOfSize(14.0) else UIFont.systemFontOfSize(14.0)
        productsTab.setImage(ShoppingIcons.shoppingBag(22.0, prodColor), forState = UIControlStateNormal)

        val purchColor = if (selectedTab == 1) ShoppingColors.Primary else ShoppingColors.OnSurfaceVariant
        purchasesTab.setTitleColor(purchColor, forState = UIControlStateNormal)
        purchasesTab.titleLabel?.font = if (selectedTab == 1) UIFont.boldSystemFontOfSize(14.0) else UIFont.systemFontOfSize(14.0)
        purchasesTab.setImage(ShoppingIcons.inventory(22.0, purchColor), forState = UIControlStateNormal)
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
