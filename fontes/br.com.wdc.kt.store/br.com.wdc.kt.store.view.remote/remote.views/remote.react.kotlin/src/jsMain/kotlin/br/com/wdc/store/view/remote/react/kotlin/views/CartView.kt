package br.com.wdc.store.view.remote.react.kotlin.views

import br.com.wdc.store.i18n.*
import br.com.wdc.store.view.remote.react.kotlin.util.messages
import br.com.wdc.store.view.remote.react.kotlin.util.message
import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewProps
import br.com.wdc.framework.cube.remote.bridge.react.remoteView
import br.com.wdc.framework.cube.remote.bridge.react.useView
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.view.remote.react.kotlin.theme.StoreColors
import br.com.wdc.store.view.remote.react.kotlin.util.formatPrice
import mui.icons.material.Add
import mui.icons.material.ArrowBack
import mui.icons.material.Remove
import mui.icons.material.ShoppingCart
import mui.material.Alert
import mui.material.Box
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import mui.material.Card
import mui.material.CardContent
import mui.material.Divider
import mui.material.IconButton
import mui.material.Paper
import mui.material.Size
import mui.material.Stack
import mui.material.StackDirection
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.FC
import react.Fragment
import react.Key
import react.create
import web.cssom.*

// :: Events of the cart view
private const val ON_BUY = 1
private const val ON_OPEN_PRODUCTS = 3

// :: Events of a cart row, sent by each row on its own view
private const val ON_REMOVE = 1
private const val ON_MODIFY_QUANTITY = 2

/** The cart: one view per row (`rowsViewIds`), the total computed on the server, and the buy button. */
val CartView = FC<RemoteViewProps> { props ->
    val bridge = useBridge()
    val scope = bridge.useView(props.vsid)
    val rows = scope.viewIds("rows")
    val muted = StoreColors.OnSurfaceVariant.unsafeCast<Color>()

    Box {
        sx {
            display = Display.flex
            justifyContent = JustifyContent.center
            padding = Padding(StoreTokens.Spacing.Lg.px, 10.px, StoreTokens.Spacing.Lg.px, StoreTokens.Spacing.Lg.px)
        }
        Box {
            sx {
                maxWidth = 700.px
                width = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            // Title
            Stack {
                direction = responsive(StackDirection.row)
                spacing = responsive(1)
                sx { alignItems = AlignItems.center; marginBottom = StoreTokens.Spacing.Md.px }
                ShoppingCart { sx { fontSize = 28.px } }
                Typography {
                    variant = TypographyVariant.h5
                    sx { fontWeight = integer(StoreTokens.FontWeight.Medium).unsafeCast<FontWeight>(); fontSize = StoreTokens.FontSize.Display.px }
                    +messages.cartTitle()
                }
            }

            Divider()

            val errorMessage = scope.message("errorMessage")
            if (!errorMessage.isNullOrEmpty()) {
                Alert {
                    severity = "error"
                    sx { marginTop = StoreTokens.Spacing.Md.px; borderRadius = StoreTokens.Radius.Sm.px }
                    +errorMessage
                }
            }

            if (rows.isEmpty()) {
                Box {
                    sx {
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                        flex = Flex(number(1.0), number(1.0), 0.px)
                        padding = 48.px
                    }
                    ShoppingCart { sx { fontSize = 48.px; color = muted } }
                    Typography {
                        variant = TypographyVariant.h6
                        sx { marginTop = StoreTokens.Spacing.Lg.px; color = muted }
                        +messages.cartEmpty()
                    }
                    Typography {
                        variant = TypographyVariant.body2
                        sx { marginTop = StoreTokens.Spacing.Sm.px; color = muted; opacity = number(0.7) }
                        +messages.cartEmptyHint()
                    }
                }
            } else {
                Box {
                    sx { marginTop = StoreTokens.Spacing.Md.px; marginRight = (-6).px }
                    Stack {
                        direction = responsive(StackDirection.column)
                        spacing = responsive(1)
                        for (row in rows) {
                            Fragment {
                                key = Key(row)
                                remoteView(row)
                            }
                        }
                    }
                }

                // Total
                Divider { sx { marginTop = StoreTokens.Spacing.Sm.px; marginBottom = StoreTokens.Spacing.Sm.px } }
                Stack {
                    direction = responsive(StackDirection.row)
                    spacing = responsive(1)
                    sx { justifyContent = JustifyContent.flexEnd; alignItems = AlignItems.center }
                    Typography {
                        variant = TypographyVariant.subtitle1
                        sx { color = muted }
                        +messages.commonTotal()
                    }
                    Box {
                        sx {
                            backgroundColor = StoreColors.PriceBackground.unsafeCast<BackgroundColor>()
                            borderRadius = 10.px
                            padding = Padding(6.px, 14.px)
                        }
                        Typography {
                            variant = TypographyVariant.h6
                            sx {
                                fontWeight = FontWeight.bold
                                fontSize = 22.px
                                color = StoreColors.PriceColor.unsafeCast<Color>()
                            }
                            +"\$${formatPrice(scope.double("total") ?: 0.0)}"
                        }
                    }
                }
            }

            // Actions
            Stack {
                direction = responsive(StackDirection.row)
                spacing = responsive(2)
                sx { justifyContent = JustifyContent.flexEnd; marginTop = StoreTokens.Spacing.Lg.px }
                Button {
                    variant = ButtonVariant.outlined
                    startIcon = ArrowBack.create()
                    onClick = { bridge.submit(props.vsid, ON_OPEN_PRODUCTS) }
                    sx { borderRadius = StoreTokens.Radius.Md.px; height = 48.px; textTransform = None.none }
                    +messages.commonContinueShopping()
                }
                if (rows.isNotEmpty()) {
                    Button {
                        variant = ButtonVariant.contained
                        onClick = { bridge.submit(props.vsid, ON_BUY) }
                        sx {
                            borderRadius = StoreTokens.Radius.Md.px
                            height = 48.px
                            textTransform = None.none
                            fontWeight = FontWeight.bold
                            fontSize = StoreTokens.FontSize.BodyLarge.px
                            backgroundColor = StoreColors.PriceColor.unsafeCast<BackgroundColor>()
                        }
                        +messages.cartBuy()
                    }
                }
            }
        }
    }
}

/** One row of the cart, a view of its own: the quantity goes as `p.quantity` with its event 2. */
val CartItemView = FC<RemoteViewProps> { props ->
    val bridge = useBridge()
    val scope = bridge.useView(props.vsid)
    val quantity = scope.int("quantity") ?: 0

    val modifyQuantity = { newQuantity: Int ->
        bridge.setFormField(props.vsid, "p.quantity", newQuantity)
        bridge.submit(props.vsid, ON_MODIFY_QUANTITY)
    }

    Card {
        elevation = 0
        sx {
            borderRadius = StoreTokens.Radius.Sm.px
            backgroundColor = StoreColors.SurfaceVariant40.unsafeCast<BackgroundColor>()
        }
        CardContent {
            sx { padding = StoreTokens.Spacing.Lg.px }
            Stack {
                direction = responsive(StackDirection.row)
                spacing = responsive(2)
                sx { alignItems = AlignItems.center }

                Box {
                    sx { flex = Flex(number(1.0), number(1.0), 0.px); minWidth = 0.px }
                    Typography {
                        variant = TypographyVariant.body2
                        noWrap = true
                        sx { fontWeight = integer(StoreTokens.FontWeight.Medium).unsafeCast<FontWeight>(); fontSize = StoreTokens.FontSize.Body.px }
                        +(scope.string("name") ?: "")
                    }
                    Typography {
                        variant = TypographyVariant.body2
                        sx {
                            fontWeight = integer(StoreTokens.FontWeight.Medium).unsafeCast<FontWeight>()
                            color = StoreColors.PriceColor.unsafeCast<Color>()
                        }
                        +"\$${formatPrice(scope.double("price"))}"
                    }
                }

                // Quantity
                Paper {
                    elevation = 0
                    sx {
                        borderRadius = 10.px
                        backgroundColor = StoreColors.SurfaceVariant.unsafeCast<BackgroundColor>()
                        display = Display.flex
                        alignItems = AlignItems.center
                        paddingLeft = StoreTokens.Spacing.Xs.px
                        paddingRight = StoreTokens.Spacing.Xs.px
                    }
                    IconButton {
                        size = Size.small
                        onClick = { if (quantity > 1) modifyQuantity(quantity - 1) }
                        sx {
                            backgroundColor = StoreColors.SecondaryContainer.unsafeCast<BackgroundColor>()
                            width = 32.px
                            height = 32.px
                        }
                        Remove { sx { fontSize = 16.px } }
                    }
                    Typography {
                        variant = TypographyVariant.subtitle1
                        sx { fontWeight = FontWeight.bold; paddingLeft = 14.px; paddingRight = 14.px }
                        +"$quantity"
                    }
                    IconButton {
                        size = Size.small
                        onClick = { modifyQuantity(quantity + 1) }
                        sx {
                            backgroundColor = StoreColors.SecondaryContainer.unsafeCast<BackgroundColor>()
                            width = 32.px
                            height = 32.px
                        }
                        Add { sx { fontSize = 16.px } }
                    }
                }

                Button {
                    variant = ButtonVariant.text
                    color = ButtonColor.error
                    onClick = { bridge.submit(props.vsid, ON_REMOVE) }
                    sx { fontWeight = FontWeight.normal; fontSize = StoreTokens.FontSize.Caption.px; textTransform = None.none }
                    +messages.cartRemove()
                }
            }
        }
    }
}
