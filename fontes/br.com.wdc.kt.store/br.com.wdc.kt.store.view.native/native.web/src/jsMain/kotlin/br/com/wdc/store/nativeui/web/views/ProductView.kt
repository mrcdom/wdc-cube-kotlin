package br.com.wdc.store.nativeui.web.views

import br.com.wdc.store.i18n.*
import br.com.wdc.store.nativeui.web.util.messages
import br.com.wdc.store.nativeui.web.util.show
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.nativeui.web.bridge.ScopeProps
import br.com.wdc.store.nativeui.web.bridge.safeCall
import br.com.wdc.store.nativeui.web.bridge.useScope
import br.com.wdc.store.nativeui.web.theme.StoreColors
import br.com.wdc.store.nativeui.web.util.formatPrice
import br.com.wdc.store.nativeui.web.util.productImageUrl
import br.com.wdc.store.nativeui.web.util.stripHtml
import br.com.wdc.store.presentation.presenter.restricted.products.ProductScope
import mui.icons.material.Add
import mui.icons.material.AddShoppingCart
import mui.icons.material.ArrowBack
import mui.icons.material.Remove
import mui.icons.material.ShoppingBag
import mui.material.Alert
import mui.material.AlertColor
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Card
import mui.material.CardContent
import mui.material.CardMedia
import mui.material.Chip
import mui.material.CircularProgress
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
import react.ReactNode
import react.dom.html.ReactHTML.img
import react.dom.events.MouseEventHandler
import react.useState
import web.cssom.*
import web.html.HTMLButtonElement

val ProductView = FC<ScopeProps<ProductScope>> { props ->
    val scope = useScope(props.scope)

    /** The quantity being chosen: UI state of this view, fresh for each product (the slot keys it by scope). */
    var quantity by useState(1)

    val onDecrement: MouseEventHandler<HTMLButtonElement> = {
        if (quantity > 1) quantity--
    }

    val onIncrement: MouseEventHandler<HTMLButtonElement> = {
        quantity++
    }

    val onOpenProducts: MouseEventHandler<HTMLButtonElement> = { safeCall { scope.onOpenProducts() } }

    val onAddToCart: MouseEventHandler<HTMLButtonElement> = {
        val chosen = quantity
        safeCall { scope.onAddToCart(chosen) }
    }

    val product = scope.product

    Box {
        sx {
            display = Display.flex
            justifyContent = JustifyContent.center
            padding = Padding(StoreTokens.Spacing.Lg.px, 10.px, StoreTokens.Spacing.Lg.px, StoreTokens.Spacing.Lg.px)
        }

        if (product != null) {
            Box {
                sx { maxWidth = 600.px; width = 100.pct }

                Stack {
                    direction = responsive(StackDirection.column)
                    spacing = responsive(2)

                    // Error message
                    val errorMessage = scope.errorMessage?.let(messages::show)
                    if (!errorMessage.isNullOrBlank()) {
                        Alert {
                            severity = "error"
                            sx { borderRadius = StoreTokens.Radius.Sm.px }
                            +errorMessage
                        }
                    }

                    // Product name
                    Typography {
                        variant = TypographyVariant.h4
                        sx { fontWeight = FontWeight.bold }
                        +(product.name ?: "")
                    }

                    Divider {}

                    // Description
                    val rawDesc = product.description
                    if (!rawDesc.isNullOrBlank() && rawDesc != "unknown") {
                        val cleanDesc = stripHtml(rawDesc)
                        if (cleanDesc.isNotBlank()) {
                            Paper {
                                sx {
                                    padding = StoreTokens.Spacing.Lg.px
                                    borderRadius = StoreTokens.Radius.Sm.px
                                    backgroundColor = StoreColors.SurfaceVariant50.unsafeCast<BackgroundColor>()
                                }
                                elevation = 0
                                Typography {
                                    variant = TypographyVariant.body1
                                    sx {
                                        color = StoreColors.OnSurfaceVariant.unsafeCast<Color>()
                                        lineHeight = 24.px
                                        whiteSpace = WhiteSpace.preWrap
                                    }
                                    +cleanDesc
                                }
                            }
                        }
                    }

                    // Price + quantity + image row
                    Stack {
                        direction = responsive(StackDirection.row)
                        spacing = responsive(3)
                        sx { alignItems = AlignItems.center }

                        // Left: price + quantity
                        Stack {
                            direction = responsive(StackDirection.column)
                            spacing = responsive(2)
                            sx { flex = Flex(number(1.0), number(1.0), 0.px) }

                            // Price
                            Chip {
                                label = ReactNode("\$${formatPrice(product.price)}")
                                sx {
                                    backgroundColor = StoreColors.PriceBackground.unsafeCast<BackgroundColor>()
                                    color = StoreColors.PriceColor.unsafeCast<Color>()
                                    height = 40.px
                                    borderRadius = StoreTokens.Radius.Md.px
                                }
                            }

                            // Quantity selector
                            Stack {
                                direction = responsive(StackDirection.row)
                                spacing = responsive(1)
                                sx { alignItems = AlignItems.center }

                                Typography {
                                    variant = TypographyVariant.body1
                                    sx { fontWeight = FontWeight.bold }
                                    +messages.productQuantity()
                                }

                                Paper {
                                    sx {
                                        borderRadius = StoreTokens.Radius.Md.px
                                        backgroundColor = StoreColors.SurfaceVariant.unsafeCast<BackgroundColor>()
                                        display = Display.flex
                                        alignItems = AlignItems.center
                                        paddingLeft = StoreTokens.Spacing.Xs.px
                                        paddingRight = StoreTokens.Spacing.Xs.px
                                    }
                                    elevation = 0

                                    IconButton {
                                        disabled = quantity <= 1
                                        onClick = onDecrement
                                        size = mui.material.Size.small
                                        Remove {}
                                    }

                                    Typography {
                                        variant = TypographyVariant.h6
                                        sx { fontWeight = FontWeight.bold; paddingLeft = 20.px; paddingRight = 20.px }
                                        +"$quantity"
                                    }

                                    IconButton {
                                        onClick = onIncrement
                                        size = mui.material.Size.small
                                        Add {}
                                    }
                                }
                            }
                        }

                        Box {
                            sx {
                                width = 120.px
                                height = 120.px
                                borderRadius = StoreTokens.Radius.Md.px
                                overflow = Overflow.hidden
                                backgroundColor = StoreColors.SurfaceVariant.unsafeCast<BackgroundColor>()
                            }
                            img {
                                src = productImageUrl(product.id)
                                alt = product.name ?: ""
                                style = js("({width: '120px', height: '120px', objectFit: 'contain'})").unsafeCast<react.CSSProperties>()
                            }
                        }
                    }

                    // Action buttons
                    Stack {
                        direction = responsive(StackDirection.row)
                        spacing = responsive(2)
                        sx { justifyContent = JustifyContent.flexEnd }

                        Button {
                            variant = ButtonVariant.outlined
                            onClick = onOpenProducts
                            sx { borderRadius = StoreTokens.Radius.Md.px; height = 48.px; textTransform = None.none }
                            ArrowBack { sx { marginRight = 6.px; fontSize = 18.px } }
                            +messages.productBack()
                        }

                        Button {
                            variant = ButtonVariant.contained
                            onClick = onAddToCart
                            sx { borderRadius = StoreTokens.Radius.Md.px; height = 48.px; textTransform = None.none }
                            AddShoppingCart { sx { marginRight = 6.px; fontSize = 18.px } }
                            +messages.productAddToCart()
                        }
                    }
                }
            }
        } else {
            // Loading
            Stack {
                direction = responsive(StackDirection.column)
                sx { alignItems = AlignItems.center; padding = 48.px }
                spacing = responsive(2)

                CircularProgress {}
                Typography {
                    variant = TypographyVariant.body2
                    sx { color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                    +messages.productLoading()
                }
            }
        }
    }
}
