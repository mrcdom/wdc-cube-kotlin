package br.com.wdc.shopping.nativeui.web.views

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.shopping.nativeui.web.bridge.RenderSlot
import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.nativeui.web.bridge.WorkerProxy
import br.com.wdc.shopping.nativeui.web.theme.ShoppingColors
import mui.icons.material.Inventory2
import mui.icons.material.LocalMall
import mui.icons.material.Logout
import mui.icons.material.ShoppingBag
import mui.icons.material.ShoppingCart
import mui.material.Alert
import mui.material.AppBar
import mui.material.AppBarPosition
import mui.material.Avatar
import mui.material.Badge
import mui.material.BadgeColor
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Card
import mui.material.IconButton
import mui.material.Tab
import mui.material.Tabs
import mui.material.Toolbar
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.FC
import react.Props
import react.ReactNode
import react.create
import react.useEffect
import react.useState
import web.cssom.*

private const val COMPACT_BREAKPOINT = 480

class HomeView(viewId: String, proxy: WorkerProxy) : ReactCubeView(viewId, proxy) {

    // Local state
    private var nickName: String? = null
    private var cartItemCount: Int = 0
    private var contentViewId: String? = null
    private var productsPanelViewId: String? = null
    private var purchasesPanelViewId: String? = null
    private var errorMessage: String? = null

    override fun readState(json: String) {
        // Reset nullable fields before parsing — fields absent from JSON mean null
        contentViewId = null
        errorMessage = null

        val inp = JsonInputFactory.createStringInput(json).input
        inp.beginObject()
        while (inp.hasNext()) {
            when (inp.nextName()) {
                "id" -> inp.skipValue()
                "nickName" -> nickName = inp.nextString()
                "cartItemCount" -> cartItemCount = inp.nextInt()
                "contentViewId" -> contentViewId = inp.nextString()
                "productsPanelViewId" -> productsPanelViewId = inp.nextString()
                "purchasesPanelViewId" -> purchasesPanelViewId = inp.nextString()
                "errorMessage" -> errorMessage = inp.nextString()
                else -> inp.skipValue()
            }
        }
        inp.endObject()
    }

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@HomeView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        var selectedTab by useState(0)
        var isCompact by useState(kotlinx.browser.window.innerWidth < COMPACT_BREAKPOINT)

        // Track window width for responsiveness
        useEffect(Unit) {
            val handler: (dynamic) -> Unit = {
                isCompact = kotlinx.browser.window.innerWidth < COMPACT_BREAKPOINT
            }
            kotlinx.browser.window.addEventListener("resize", handler)
        }

        Box {
            sx {
                display = Display.flex
                flexDirection = FlexDirection.column
                height = 100.vh
                overflow = Overflow.hidden
            }

            // Header bar (responsive)
            AppBar {
                position = AppBarPosition.static
                sx { backgroundColor = ShoppingColors.Primary.unsafeCast<BackgroundColor>() }

                if (isCompact) {
                    // Compact header: two rows
                    Box {
                        sx { padding = Padding(10.px, 12.px) }

                        // Top row: logo + "Shopping" + logout icon (all left)
                        Box {
                            sx {
                                display = Display.flex
                                alignItems = AlignItems.center
                                width = 100.pct
                            }

                            // Logo
                            Avatar {
                                sx {
                                    width = 32.px
                                    height = 32.px
                                    backgroundColor = ShoppingColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                                    borderRadius = 8.px
                                    marginRight = 8.px
                                }
                                LocalMall { sx { fontSize = 20.px; color = NamedColor.white.unsafeCast<Color>() } }
                            }
                            Typography {
                                variant = TypographyVariant.h6
                                sx { fontWeight = FontWeight.bold; color = NamedColor.white.unsafeCast<Color>() }
                                +"Shopping"
                            }

                            // Logout icon (subtle, near brand)
                            IconButton {
                                onClick = { action("onExit") }
                                sx {
                                    width = 28.px
                                    height = 28.px
                                    marginLeft = 12.px
                                    backgroundColor = ShoppingColors.WhiteOverlay10.unsafeCast<BackgroundColor>()
                                }
                                Logout { sx { fontSize = 16.px; color = NamedColor.white.unsafeCast<Color>(); opacity = number(0.7) } }
                            }
                        }

                        // Spacer
                        Box { sx { height = 8.px } }

                        // Bottom row: greeting + cart
                        Box {
                            sx {
                                display = Display.flex
                                alignItems = AlignItems.center
                                justifyContent = JustifyContent.spaceBetween
                                width = 100.pct
                            }

                            Typography {
                                variant = TypographyVariant.body2
                                sx { color = ShoppingColors.WhiteOverlay85.unsafeCast<Color>() }
                                +"Olá, ${nickName ?: ""}"
                            }

                            Badge {
                                badgeContent = ReactNode("$cartItemCount")
                                color = BadgeColor.error
                                invisible = cartItemCount <= 0

                                Button {
                                    variant = ButtonVariant.contained
                                    onClick = { action("onOpenCart") }
                                    sx {
                                        backgroundColor = ShoppingColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                                        borderRadius = 10.px
                                        textTransform = None.none
                                        padding = Padding(4.px, 12.px)
                                        fontSize = 13.px
                                    }
                                    ShoppingCart { sx { marginRight = 6.px; fontSize = 16.px } }
                                    +"Carrinho"
                                }
                            }
                        }
                    }
                } else {
                    // Desktop header: single row
                    Toolbar {
                        sx {
                            padding = Padding(12.px, 24.px)
                            minHeight = important(Auto.auto)
                        }

                        // Logo
                        Avatar {
                            sx {
                                width = 36.px
                                height = 36.px
                                backgroundColor = ShoppingColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                                borderRadius = 10.px
                                marginRight = 12.px
                            }
                            LocalMall { sx { fontSize = 22.px; color = NamedColor.white.unsafeCast<Color>() } }
                        }

                        Typography {
                            variant = TypographyVariant.h6
                            sx { fontWeight = FontWeight.bold }
                            +"Shopping"
                        }

                        // Logout icon (subtle, near brand)
                        IconButton {
                            onClick = { action("onExit") }
                            sx {
                                width = 32.px
                                height = 32.px
                                marginLeft = 16.px
                                backgroundColor = ShoppingColors.WhiteOverlay10.unsafeCast<BackgroundColor>()
                            }
                            Logout { sx { fontSize = 18.px; color = NamedColor.white.unsafeCast<Color>(); opacity = number(0.7) } }
                        }

                        // Spacer
                        Box { sx { flexGrow = number(1.0) } }

                        // Greeting (filled pill)
                        Box {
                            sx {
                                backgroundColor = ShoppingColors.WhiteOverlay15.unsafeCast<BackgroundColor>()
                                borderRadius = 20.px
                                padding = Padding(8.px, 16.px)
                                marginRight = 16.px
                            }
                            Typography {
                                variant = TypographyVariant.body2
                                sx { color = NamedColor.white.unsafeCast<Color>() }
                                +"Olá, ${nickName ?: ""}"
                            }
                        }

                        // Cart button
                        Badge {
                            badgeContent = ReactNode("$cartItemCount")
                            color = BadgeColor.error
                            invisible = cartItemCount <= 0

                            Button {
                                variant = ButtonVariant.contained
                                asDynamic().disableElevation = true
                                onClick = { action("onOpenCart") }
                                sx {
                                    backgroundColor = ShoppingColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                                    borderRadius = 12.px
                                    textTransform = None.none
                                }
                                ShoppingCart { sx { marginRight = 6.px; fontSize = 18.px } }
                                +"Carrinho"
                            }
                        }
                    }
                }
            }

            // Error message
            val err = errorMessage
            if (!err.isNullOrBlank()) {
                Alert {
                    severity = "error"
                    sx { margin = Margin(8.px, 16.px) }
                    +err
                }
            }

            // Content area
            val contentView = contentViewId?.let { proxy.getView(it) }
            if (contentView != null) {
                Box {
                    sx {
                        flex = Flex(number(1.0), number(1.0), 0.px)
                        overflowY = Overflow.scroll
                        overflowX = Overflow.hidden
                    }
                    Box {
                        sx {
                            maxWidth = 560.px
                            marginLeft = Auto.auto
                            marginRight = Auto.auto
                        }
                        RenderSlot {
                            view = contentView
                        }
                    }
                }
            } else if (isCompact) {
                // COMPACT: Tabs for Products / Purchases
                Box {
                    sx {
                        flex = Flex(number(1.0), number(1.0), 0.px)
                        padding = 8.px
                        overflow = Overflow.hidden
                        display = Display.flex
                        flexDirection = FlexDirection.column
                    }

                    Tabs {
                        value = selectedTab
                        onChange = { _, newValue -> selectedTab = newValue as Int }
                        sx {
                            marginBottom = 4.px
                            minHeight = 36.px
                        }

                        Tab {
                            label = ReactNode("Produtos")
                            asDynamic().icon = ShoppingBag.create()
                            iconPosition = mui.material.IconPosition.start
                            sx {
                                minHeight = 36.px
                                paddingTop = 4.px
                                paddingBottom = 4.px
                                fontSize = 13.px
                            }
                        }
                        Tab {
                            label = ReactNode("Compras")
                            asDynamic().icon = Inventory2.create()
                            iconPosition = mui.material.IconPosition.start
                            sx {
                                minHeight = 36.px
                                paddingTop = 4.px
                                paddingBottom = 4.px
                                fontSize = 13.px
                            }
                        }
                    }

                    Card {
                        sx {
                            borderRadius = 8.px
                            flex = Flex(number(1.0), number(1.0), 0.px)
                            if (selectedTab == 0) {
                                overflowY = Overflow.scroll
                                overflowX = Overflow.hidden
                            } else {
                                overflow = Overflow.hidden
                            }
                        }
                        elevation = 0

                        when (selectedTab) {
                            0 -> {
                                val productsView = productsPanelViewId?.let { proxy.getView(it) }
                                if (productsView != null) {
                                    RenderSlot { view = productsView }
                                }
                            }
                            1 -> {
                                val purchasesView = purchasesPanelViewId?.let { proxy.getView(it) }
                                if (purchasesView != null) {
                                    RenderSlot { view = purchasesView }
                                }
                            }
                        }
                    }
                }
            } else {
                // DESKTOP: Side-by-side layout
                Box {
                    sx {
                        display = Display.flex
                        flexDirection = FlexDirection.row
                        flex = Flex(number(1.0), number(1.0), 0.px)
                        padding = 16.px
                        gap = 16.px
                        overflow = Overflow.hidden
                        maxWidth = 1200.px
                        marginLeft = Auto.auto
                        marginRight = Auto.auto
                        width = 100.pct
                    }

                    // Products panel (flex: 3)
                    Card {
                        sx {
                            flex = Flex(number(3.0), number(1.0), 0.px)
                            borderRadius = 8.px
                            overflowY = Overflow.scroll
                            overflowX = Overflow.hidden
                        }
                        elevation = 0

                        val productsView = productsPanelViewId?.let { proxy.getView(it) }
                        if (productsView != null) {
                            RenderSlot { view = productsView }
                        }
                    }

                    // Purchases panel (flex: 2)
                    Card {
                        sx {
                            flex = Flex(number(2.0), number(1.0), 0.px)
                            borderRadius = 8.px
                            overflow = Overflow.hidden
                        }
                        elevation = 0

                        val purchasesView = purchasesPanelViewId?.let { proxy.getView(it) }
                        if (purchasesView != null) {
                            RenderSlot { view = purchasesView }
                        }
                    }
                }
            }
        }
    }
}
