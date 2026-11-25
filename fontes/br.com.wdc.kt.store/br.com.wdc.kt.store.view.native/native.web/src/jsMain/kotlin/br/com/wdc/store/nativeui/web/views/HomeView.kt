package br.com.wdc.store.nativeui.web.views

import br.com.wdc.store.nativeui.web.bridge.RenderSlot
import br.com.wdc.store.nativeui.web.bridge.ScopeProps
import br.com.wdc.store.nativeui.web.bridge.safeCall
import br.com.wdc.store.nativeui.web.bridge.useScope
import br.com.wdc.store.nativeui.web.theme.StoreColors
import br.com.wdc.store.presentation.presenter.restricted.home.HomeScope
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
import react.ReactNode
import react.create
import react.dom.events.MouseEventHandler
import react.dom.events.SyntheticEvent
import react.useEffectWithCleanup
import react.useState
import web.cssom.*
import web.html.HTMLButtonElement

private const val COMPACT_BREAKPOINT = 518

val HomeView = FC<ScopeProps<HomeScope>> { props ->
    val scope = useScope(props.scope)

    /** The tab shown in the compact layout: UI state of this view, changed by [onTabChange]. */
    var selectedTab by useState(0)

    val onExit: MouseEventHandler<HTMLButtonElement> = { safeCall { scope.onExit() } }

    val onOpenCart: MouseEventHandler<HTMLButtonElement> = { safeCall { scope.onOpenCart() } }

    val onTabChange: (SyntheticEvent<*, *>, Any) -> Unit = { _, value ->
        selectedTab = value as Int
    }

    var isCompact by useState(kotlinx.browser.window.innerWidth < COMPACT_BREAKPOINT)

    // Track window width for responsiveness
    useEffectWithCleanup(Unit) {
        val handler: (dynamic) -> Unit = {
            isCompact = kotlinx.browser.window.innerWidth < COMPACT_BREAKPOINT
        }
        kotlinx.browser.window.addEventListener("resize", handler)
        onCleanup { kotlinx.browser.window.removeEventListener("resize", handler) }
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
            sx { backgroundColor = StoreColors.Primary.unsafeCast<BackgroundColor>() }

            if (isCompact) {
                // Compact header: two rows
                Box {
                    sx { padding = Padding(10.px, 12.px) }

                    // Top row: logo + "WDC Store" + logout icon (all left)
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
                                backgroundColor = StoreColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                                borderRadius = 8.px
                                marginRight = 8.px
                            }
                            LocalMall { sx { fontSize = 20.px; color = NamedColor.white.unsafeCast<Color>() } }
                        }
                        Box {
                            sx {
                                position = Position.relative
                            }
                            Typography {
                                variant = TypographyVariant.h6
                                sx { fontWeight = FontWeight.bold; color = NamedColor.white.unsafeCast<Color>() }
                                +"WDC Store"
                            }
                            Typography {
                                variant = TypographyVariant.caption
                                sx {
                                    position = Position.absolute
                                    bottom = (-10).px
                                    right = 0.px
                                    color = NamedColor.white.unsafeCast<Color>()
                                    opacity = number(0.45)
                                    fontSize = 9.px
                                }
                                +"native"
                            }
                        }

                        // Logout icon (subtle, near brand)
                        IconButton {
                            onClick = onExit
                            sx {
                                width = 28.px
                                height = 28.px
                                marginLeft = 16.px
                                backgroundColor = StoreColors.WhiteOverlay10.unsafeCast<BackgroundColor>()
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
                            sx { color = StoreColors.WhiteOverlay85.unsafeCast<Color>() }
                            +"Hello, ${scope.nickName ?: ""}"
                        }

                        Badge {
                            badgeContent = ReactNode("${scope.cartItemCount}")
                            color = BadgeColor.error
                            invisible = scope.cartItemCount <= 0

                            Button {
                                variant = ButtonVariant.contained
                                onClick = onOpenCart
                                sx {
                                    backgroundColor = StoreColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                                    borderRadius = 10.px
                                    textTransform = None.none
                                    padding = Padding(4.px, 12.px)
                                    fontSize = 13.px
                                }
                                ShoppingCart { sx { marginRight = 6.px; fontSize = 16.px } }
                                +"Cart"
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
                            backgroundColor = StoreColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                            borderRadius = 10.px
                            marginRight = 10.px
                        }
                        LocalMall { sx { fontSize = 22.px; color = NamedColor.white.unsafeCast<Color>() } }
                    }

                    Box {
                        sx {
                            position = Position.relative
                        }
                        Typography {
                            variant = TypographyVariant.h6
                            sx { fontWeight = FontWeight.bold }
                            +"WDC Store"
                        }
                        Typography {
                            variant = TypographyVariant.caption
                            sx {
                                position = Position.absolute
                                bottom = (-10).px
                                right = 0.px
                                color = NamedColor.white.unsafeCast<Color>()
                                opacity = number(0.45)
                                fontSize = 10.px
                            }
                            +"native"
                        }
                    }

                    // Logout icon (subtle, near brand)
                    IconButton {
                        onClick = onExit
                        sx {
                            width = 32.px
                            height = 32.px
                            marginLeft = 10.px
                            backgroundColor = StoreColors.WhiteOverlay10.unsafeCast<BackgroundColor>()
                        }
                        Logout { sx { fontSize = 18.px; color = NamedColor.white.unsafeCast<Color>(); opacity = number(0.7) } }
                    }

                    // Spacer
                    Box { sx { flexGrow = number(1.0) } }

                    // Greeting (filled pill)
                    Box {
                        sx {
                            backgroundColor = StoreColors.WhiteOverlay15.unsafeCast<BackgroundColor>()
                            borderRadius = 20.px
                            padding = Padding(4.px, 12.px)
                            marginRight = 16.px
                        }
                        Typography {
                            variant = TypographyVariant.body2
                            sx { color = NamedColor.white.unsafeCast<Color>() }
                            +"Hello, ${scope.nickName ?: ""}"
                        }
                    }

                    // Cart button
                    Badge {
                        badgeContent = ReactNode("${scope.cartItemCount}")
                        color = BadgeColor.error
                        invisible = scope.cartItemCount <= 0

                        Button {
                            variant = ButtonVariant.contained
                            asDynamic().disableElevation = true
                            onClick = onOpenCart
                            sx {
                                backgroundColor = StoreColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                                borderRadius = 10.px
                                textTransform = None.none
                                padding = Padding(5.px, 12.px)
                            }
                            ShoppingCart { sx { marginRight = 6.px; fontSize = 16.px } }
                            +"Cart"
                        }
                    }
                }
            }
        }

        // Error message
        val errorMessage = scope.errorMessage
        if (!errorMessage.isNullOrBlank()) {
            Alert {
                severity = "error"
                sx { margin = Margin(8.px, 16.px) }
                +errorMessage
            }
        }

        // Content area
        val content = scope.content
        if (content != null) {
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
                        this.scope = content
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
                    onChange = onTabChange
                    sx {
                        marginBottom = 4.px
                        minHeight = 36.px
                    }

                    Tab {
                        label = ReactNode("Products")
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
                        label = ReactNode("Purchases")
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
                            RenderSlot { this.scope = scope.productsPanel }
                        }
                        1 -> {
                            RenderSlot { this.scope = scope.purchasesPanel }
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

                    RenderSlot { this.scope = scope.productsPanel }
                }

                // Purchases panel (flex: 2)
                Card {
                    sx {
                        flex = Flex(number(2.0), number(1.0), 0.px)
                        borderRadius = 8.px
                        overflow = Overflow.hidden
                    }
                    elevation = 0

                    RenderSlot { this.scope = scope.purchasesPanel }
                }
            }
        }
    }
}
