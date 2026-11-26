package br.com.wdc.store.nativeui.web.views

import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.nativeui.web.bridge.RenderSlot
import br.com.wdc.store.nativeui.web.bridge.ScopeProps
import br.com.wdc.store.nativeui.web.bridge.safeCall
import br.com.wdc.store.nativeui.web.bridge.scopeKey
import br.com.wdc.store.nativeui.web.bridge.useScope
import br.com.wdc.store.nativeui.web.theme.StoreColors
import br.com.wdc.store.nativeui.web.theme.StoreStyles
import br.com.wdc.store.nativeui.web.util.formatPrice
import br.com.wdc.store.presentation.presenter.restricted.cart.CartItemScope
import br.com.wdc.store.presentation.presenter.restricted.cart.CartScope
import mui.icons.material.Add
import mui.icons.material.ArrowBack
import mui.icons.material.Remove
import mui.icons.material.ShoppingCart
import mui.material.Alert
import mui.material.AlertColor
import mui.material.Box
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import mui.material.Card
import mui.material.CardContent
import mui.material.Chip
import mui.material.Divider
import mui.material.IconButton
import mui.material.Paper
import mui.material.Stack
import mui.material.StackDirection
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.FC
import react.Key
import react.ReactNode
import react.dom.events.MouseEventHandler
import web.cssom.*
import web.html.HTMLButtonElement

val CartView = FC<ScopeProps<CartScope>> { props ->
    val scope = useScope(props.scope)

    val onOpenProducts: MouseEventHandler<HTMLButtonElement> = { safeCall { scope.onOpenProducts() } }

    val onBuy: MouseEventHandler<HTMLButtonElement> = { safeCall { scope.onBuy() } }

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
                    asDynamic().style = StoreStyles.titleH5
                    +"Shopping Cart"
                }
            }

            Divider {}

            // Error message
            val errorMessage = scope.errorMessage
            if (!errorMessage.isNullOrBlank()) {
                Alert {
                    severity = "error"
                    sx { marginTop = StoreTokens.Spacing.Md.px; borderRadius = StoreTokens.Radius.Sm.px }
                    +errorMessage
                }
            }

            val rows = scope.rows
            if (rows.isEmpty()) {
                // Empty state
                Box {
                    sx {
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                        flex = Flex(number(1.0), number(1.0), 0.px)
                        padding = 48.px
                    }
                    ShoppingCart { sx { fontSize = 48.px; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() } }
                    Typography {
                        variant = TypographyVariant.h6
                        sx { marginTop = StoreTokens.Spacing.Lg.px; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                        +"Your cart is empty"
                    }
                    Typography {
                        variant = TypographyVariant.body2
                        sx { marginTop = StoreTokens.Spacing.Sm.px; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>(); opacity = number(0.7) }
                        +"Add products to start shopping"
                    }
                }
            } else {
                // Cart items
                Box {
                    sx {
                        marginTop = StoreTokens.Spacing.Md.px
                        marginRight = (-6).px
                    }

                    Stack {
                        direction = responsive(StackDirection.column)
                        spacing = responsive(1)
                        // Each row is a view of its own: it repaints only when its own scope changes
                        for (row in rows) {
                            RenderSlot {
                                key = Key(scopeKey(row))
                                this.scope = row
                            }
                        }
                    }
                }

                // Total section
                Divider { sx { marginTop = StoreTokens.Spacing.Sm.px; marginBottom = StoreTokens.Spacing.Sm.px } }

                Stack {
                    direction = responsive(StackDirection.row)
                    sx { justifyContent = JustifyContent.flexEnd; alignItems = AlignItems.center }
                    spacing = responsive(1)

                    Typography {
                        variant = TypographyVariant.subtitle1
                        sx { color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                        +"Total: "
                    }

                    val total = scope.total
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
                            +"\$${formatPrice(total)}"
                        }
                    }
                }
            }

            // Action buttons
            Stack {
                direction = responsive(StackDirection.row)
                spacing = responsive(2)
                sx { justifyContent = JustifyContent.flexEnd; marginTop = StoreTokens.Spacing.Lg.px }

                Button {
                    variant = ButtonVariant.outlined
                    onClick = onOpenProducts
                    sx { borderRadius = StoreTokens.Radius.Md.px; height = 48.px; textTransform = None.none }
                    ArrowBack { sx { marginRight = 6.px; fontSize = 18.px } }
                    +"Continue Shopping"
                }

                if (rows.isNotEmpty()) {
                    Button {
                        variant = ButtonVariant.contained
                        onClick = onBuy
                        sx {
                            borderRadius = StoreTokens.Radius.Md.px
                            height = 48.px
                            textTransform = None.none
                            fontWeight = FontWeight.bold
                            fontSize = StoreTokens.FontSize.BodyLarge.px
                            backgroundColor = StoreColors.PriceColor.unsafeCast<BackgroundColor>()
                        }
                        +"Buy"
                    }
                }
            }
        }
    }
}

/** One row of the cart: a view of its own, repainted only when its row's scope changes. */
val CartItemView = FC<ScopeProps<CartItemScope>> { props ->
    val scope = useScope(props.scope)

    val decrement: MouseEventHandler<HTMLButtonElement> = {
        val next = (scope.quantity - 1).coerceAtLeast(1)
        safeCall { scope.onModifyQuantity(next) }
    }

    val increment: MouseEventHandler<HTMLButtonElement> = {
        val next = scope.quantity + 1
        safeCall { scope.onModifyQuantity(next) }
    }

    val remove: MouseEventHandler<HTMLButtonElement> = { safeCall { scope.onRemove() } }

    Card {
        sx {
            borderRadius = StoreTokens.Radius.Sm.px
            backgroundColor = StoreColors.SurfaceVariant40.unsafeCast<BackgroundColor>()
        }
        elevation = 0

        CardContent {
            sx { padding = StoreTokens.Spacing.Lg.px }

            Stack {
                direction = responsive(StackDirection.row)
                sx { alignItems = AlignItems.center }
                spacing = responsive(2)

                // Product info
                Box {
                    sx { flex = Flex(number(1.0), number(1.0), 0.px) }

                    Typography {
                        variant = TypographyVariant.body2
                        asDynamic().style = StoreStyles.subtitleBody2
                        asDynamic().noWrap = true
                        +(scope.name ?: "")
                    }
                    Typography {
                        variant = TypographyVariant.body2
                        asDynamic().style = StoreStyles.fontMedium
                        sx { color = StoreColors.PriceColor.unsafeCast<Color>() }
                        +"\$${formatPrice(scope.price)}"
                    }
                }

                // Quantity controls
                Paper {
                    sx {
                        borderRadius = 10.px
                        backgroundColor = StoreColors.SurfaceVariant.unsafeCast<BackgroundColor>()
                        display = Display.flex
                        alignItems = AlignItems.center
                        paddingLeft = StoreTokens.Spacing.Xs.px
                        paddingRight = StoreTokens.Spacing.Xs.px
                    }
                    elevation = 0

                    IconButton {
                        size = mui.material.Size.small
                        sx {
                            backgroundColor = StoreColors.SecondaryContainer.unsafeCast<BackgroundColor>()
                            width = 32.px
                            height = 32.px
                        }
                        onClick = decrement
                        Remove { sx { fontSize = 16.px } }
                    }

                    Typography {
                        variant = TypographyVariant.subtitle1
                        sx { fontWeight = FontWeight.bold; paddingLeft = 14.px; paddingRight = 14.px }
                        +"${scope.quantity}"
                    }

                    IconButton {
                        size = mui.material.Size.small
                        sx {
                            backgroundColor = StoreColors.SecondaryContainer.unsafeCast<BackgroundColor>()
                            width = 32.px
                            height = 32.px
                        }
                        onClick = increment
                        Add { sx { fontSize = 16.px } }
                    }
                }

                // Remove button
                Button {
                    variant = ButtonVariant.text
                    color = ButtonColor.error
                    onClick = remove
                    asDynamic().style = StoreStyles.smallAction
                    +"Remove"
                }
            }
        }
    }
}
