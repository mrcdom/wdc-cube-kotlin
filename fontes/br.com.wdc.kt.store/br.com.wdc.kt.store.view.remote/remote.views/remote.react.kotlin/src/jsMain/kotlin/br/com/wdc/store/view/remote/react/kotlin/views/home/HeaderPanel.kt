package br.com.wdc.store.view.remote.react.kotlin.views.home

import br.com.wdc.store.view.remote.react.kotlin.theme.StoreColors
import br.com.wdc.store.view.remote.react.kotlin.views.useBridge
import mui.icons.material.LocalMall
import mui.icons.material.Logout
import mui.icons.material.ShoppingCart
import mui.material.AppBar
import mui.material.AppBarPosition
import mui.material.Avatar
import mui.material.Badge
import mui.material.BadgeColor
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.IconButton
import mui.material.Toolbar
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.ChildrenBuilder
import react.FC
import react.Props
import react.ReactNode
import web.cssom.*

// :: Events of the home view, sent by its header
private const val ON_EXIT = 1
private const val ON_OPEN_CART = 2

external interface HeaderPanelProps : Props {
    /** The home's view: the header's events are the home's. */
    var vsid: String
    var nickName: String
    var cartItemCount: Int
    var isCompact: Boolean
}

/** The home's header: brand, sign out, greeting and the cart button with its count. */
val HeaderPanel = FC<HeaderPanelProps> { props ->
    val bridge = useBridge()
    val onExit = { bridge.submit(props.vsid, ON_EXIT) }
    val onOpenCart = { bridge.submit(props.vsid, ON_OPEN_CART) }
    val white = NamedColor.white

    AppBar {
        position = AppBarPosition.static
        sx { backgroundColor = StoreColors.Primary.unsafeCast<BackgroundColor>() }

        if (props.isCompact) {
            Box {
                sx { padding = Padding(10.px, 12.px) }

                // Top row: logo, brand, sign out
                Box {
                    sx { display = Display.flex; alignItems = AlignItems.center; width = 100.pct }
                    Avatar {
                        sx {
                            width = 32.px
                            height = 32.px
                            backgroundColor = StoreColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                            borderRadius = 8.px
                            marginRight = 8.px
                        }
                        LocalMall { sx { fontSize = 20.px; color = white } }
                    }
                    brand(titleVariant = TypographyVariant.h6, captionSize = 9)
                    IconButton {
                        onClick = { onExit() }
                        sx {
                            width = 28.px
                            height = 28.px
                            marginLeft = 16.px
                            backgroundColor = StoreColors.WhiteOverlay10.unsafeCast<BackgroundColor>()
                        }
                        Logout { sx { fontSize = 16.px; color = white; opacity = number(0.7) } }
                    }
                }

                Box { sx { height = 8.px } }

                // Bottom row: greeting and cart
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
                        +"Hello, ${props.nickName}"
                    }
                    cartButton(props.cartItemCount, compact = true, onOpenCart)
                }
            }
        } else {
            Toolbar {
                sx {
                    asDynamic().padding = "12px 24px !important"
                    asDynamic().minHeight = "auto !important"
                }
                Avatar {
                    sx {
                        width = 36.px
                        height = 36.px
                        backgroundColor = StoreColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                        borderRadius = 10.px
                        marginRight = 10.px
                    }
                    LocalMall { sx { fontSize = 22.px; color = white } }
                }
                brand(titleVariant = TypographyVariant.h6, captionSize = 10)
                IconButton {
                    onClick = { onExit() }
                    sx {
                        width = 32.px
                        height = 32.px
                        marginLeft = 10.px
                        backgroundColor = StoreColors.WhiteOverlay10.unsafeCast<BackgroundColor>()
                    }
                    Logout { sx { fontSize = 18.px; color = white; opacity = number(0.7) } }
                }

                Box { sx { flexGrow = number(1.0) } }

                // Greeting pill
                Box {
                    sx {
                        backgroundColor = StoreColors.WhiteOverlay15.unsafeCast<BackgroundColor>()
                        borderRadius = 20.px
                        padding = Padding(4.px, 12.px)
                        marginRight = 16.px
                    }
                    Typography {
                        variant = TypographyVariant.body2
                        sx { color = white }
                        +"Hello, ${props.nickName}"
                    }
                }
                cartButton(props.cartItemCount, compact = false, onOpenCart)
            }
        }
    }
}

/** "WDC Store", with the client's name in small letters under it. */
private fun ChildrenBuilder.brand(titleVariant: TypographyVariant, captionSize: Int) {
    Box {
        sx { position = Position.relative }
        Typography {
            variant = titleVariant
            sx { fontWeight = FontWeight.bold; color = NamedColor.white }
            +"WDC Store"
        }
        Typography {
            variant = TypographyVariant.caption
            sx {
                position = Position.absolute
                bottom = (-10).px
                right = 0.px
                color = NamedColor.white
                opacity = number(0.45)
                fontSize = captionSize.px
            }
            +"remote.kotlin"
        }
    }
}

private fun ChildrenBuilder.cartButton(count: Int, compact: Boolean, onOpenCart: () -> Unit) {
    Badge {
        badgeContent = ReactNode("$count")
        color = BadgeColor.error
        invisible = count <= 0
        Button {
            variant = ButtonVariant.contained
            if (!compact) disableElevation = true
            onClick = { onOpenCart() }
            sx {
                backgroundColor = StoreColors.WhiteOverlay20.unsafeCast<BackgroundColor>()
                borderRadius = 10.px
                textTransform = None.none
                if (compact) {
                    padding = Padding(4.px, 12.px)
                    fontSize = 13.px
                } else {
                    padding = Padding(5.px, 12.px)
                }
            }
            ShoppingCart { sx { marginRight = 6.px; fontSize = (if (compact) 16 else 18).px } }
            +"Cart"
        }
    }
}
