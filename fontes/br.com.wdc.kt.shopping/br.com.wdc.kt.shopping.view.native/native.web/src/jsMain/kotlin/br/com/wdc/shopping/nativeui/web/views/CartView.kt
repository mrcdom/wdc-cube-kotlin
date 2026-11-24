package br.com.wdc.shopping.nativeui.web.views

import br.com.wdc.shopping.nativeui.web.bridge.EmitterCache
import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.nativeui.web.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.web.theme.ShoppingStyles
import br.com.wdc.shopping.nativeui.web.util.formatPrice
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
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
import react.Props
import react.ReactNode
import react.dom.events.MouseEventHandler
import react.useEffect
import react.useState
import web.cssom.*
import web.html.HTMLButtonElement

class CartView(private val presenter: CartPresenter) : ReactCubeView("cart-view", presenter) {

    // :: Emitters — created once per view (and once per cart item), so they are the same function on every render

    private val onOpenProducts: MouseEventHandler<HTMLButtonElement> = { safeCall { presenter.scope.onOpenProducts() } }

    private val onBuy: MouseEventHandler<HTMLButtonElement> = { safeCall { presenter.scope.onBuy() } }

    /** The emitters of one cart item. They read the item's current quantity from the state when they run. */
    private inner class ItemEmitters(val id: Long) {
        private fun quantity(): Int = presenter.scope.items.firstOrNull { it.id == id }?.quantity ?: 1

        val decrement: MouseEventHandler<HTMLButtonElement> = {
            val next = (quantity() - 1).coerceAtLeast(1)
            safeCall { presenter.scope.onModifyQuantity(id, next) }
        }

        val increment: MouseEventHandler<HTMLButtonElement> = {
            val next = quantity() + 1
            safeCall { presenter.scope.onModifyQuantity(id, next) }
        }

        val remove: MouseEventHandler<HTMLButtonElement> = { safeCall { presenter.scope.onRemoveProduct(id) } }
    }

    private val itemEmitters = EmitterCache<Long, ItemEmitters> { id -> ItemEmitters(id) }

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@CartView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        val scope = presenter.scope

        Box {
            sx {
                display = Display.flex
                justifyContent = JustifyContent.center
                padding = Padding(16.px, 10.px, 16.px, 16.px)
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
                    sx { alignItems = AlignItems.center; marginBottom = 12.px }

                    ShoppingCart { sx { fontSize = 28.px } }
                    Typography {
                        variant = TypographyVariant.h5
                        asDynamic().style = ShoppingStyles.titleH5
                        +"Shopping Cart"
                    }
                }

                Divider {}

                // Error message
                val errorMessage = scope.errorMessage
                if (!errorMessage.isNullOrBlank()) {
                    Alert {
                        severity = "error"
                        sx { marginTop = 12.px; borderRadius = 8.px }
                        +errorMessage
                    }
                }

                if (scope.items.isEmpty()) {
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
                        ShoppingCart { sx { fontSize = 48.px; color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() } }
                        Typography {
                            variant = TypographyVariant.h6
                            sx { marginTop = 16.px; color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                            +"Your cart is empty"
                        }
                        Typography {
                            variant = TypographyVariant.body2
                            sx { marginTop = 8.px; color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>(); opacity = number(0.7) }
                            +"Add products to start shopping"
                        }
                    }
                } else {
                    // Cart items
                    Box {
                        sx {
                            marginTop = 12.px
                            marginRight = (-6).px
                        }

                        Stack {
                            direction = responsive(StackDirection.column)
                            spacing = responsive(1)

                            itemEmitters.retainOnly(scope.items.map { it.id })
                            for (item in scope.items) {
                                val emitters = itemEmitters[item.id]
                                Card {
                                    key = "${item.id}"
                                    sx {
                                        borderRadius = 8.px
                                        backgroundColor = ShoppingColors.SurfaceVariant40.unsafeCast<BackgroundColor>()
                                    }
                                    elevation = 0

                                    CardContent {
                                        sx { padding = 16.px }

                                        Stack {
                                            direction = responsive(StackDirection.row)
                                            sx { alignItems = AlignItems.center }
                                            spacing = responsive(2)

                                            // Product info
                                            Box {
                                                sx { flex = Flex(number(1.0), number(1.0), 0.px) }

                                                Typography {
                                                    variant = TypographyVariant.body2
                                                    asDynamic().style = ShoppingStyles.subtitleBody2
                                                    asDynamic().noWrap = true
                                                    +(item.name ?: "")
                                                }
                                                Typography {
                                                    variant = TypographyVariant.body2
                                                    asDynamic().style = ShoppingStyles.fontMedium
                                                    sx { color = ShoppingColors.PriceColor.unsafeCast<Color>() }
                                                    +"\$${formatPrice(item.price)}"
                                                }
                                            }

                                            // Quantity controls
                                            Paper {
                                                sx {
                                                    borderRadius = 10.px
                                                    backgroundColor = ShoppingColors.SurfaceVariant.unsafeCast<BackgroundColor>()
                                                    display = Display.flex
                                                    alignItems = AlignItems.center
                                                    paddingLeft = 4.px
                                                    paddingRight = 4.px
                                                }
                                                elevation = 0

                                                IconButton {
                                                    size = mui.material.Size.small
                                                    sx {
                                                        backgroundColor = ShoppingColors.SecondaryContainer.unsafeCast<BackgroundColor>()
                                                        width = 32.px
                                                        height = 32.px
                                                    }
                                                    onClick = emitters.decrement
                                                    Remove { sx { fontSize = 16.px } }
                                                }

                                                Typography {
                                                    variant = TypographyVariant.subtitle1
                                                    sx { fontWeight = FontWeight.bold; paddingLeft = 14.px; paddingRight = 14.px }
                                                    +"${item.quantity}"
                                                }

                                                IconButton {
                                                    size = mui.material.Size.small
                                                    sx {
                                                        backgroundColor = ShoppingColors.SecondaryContainer.unsafeCast<BackgroundColor>()
                                                        width = 32.px
                                                        height = 32.px
                                                    }
                                                    onClick = emitters.increment
                                                    Add { sx { fontSize = 16.px } }
                                                }
                                            }

                                            // Remove button
                                            Button {
                                                variant = ButtonVariant.text
                                                color = ButtonColor.error
                                                onClick = emitters.remove
                                                asDynamic().style = ShoppingStyles.smallAction
                                                +"Remove"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Total section
                    Divider { sx { marginTop = 8.px; marginBottom = 8.px } }

                    Stack {
                        direction = responsive(StackDirection.row)
                        sx { justifyContent = JustifyContent.flexEnd; alignItems = AlignItems.center }
                        spacing = responsive(1)

                        Typography {
                            variant = TypographyVariant.subtitle1
                            sx { color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                            +"Total: "
                        }

                        val total = scope.items.sumOf { it.price * it.quantity }
                        Box {
                            sx {
                                backgroundColor = ShoppingColors.PriceBackground.unsafeCast<BackgroundColor>()
                                borderRadius = 10.px
                                padding = Padding(6.px, 14.px)
                            }
                            Typography {
                                variant = TypographyVariant.h6
                                sx {
                                    fontWeight = FontWeight.bold
                                    fontSize = 22.px
                                    color = ShoppingColors.PriceColor.unsafeCast<Color>()
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
                    sx { justifyContent = JustifyContent.flexEnd; marginTop = 16.px }

                    Button {
                        variant = ButtonVariant.outlined
                        onClick = onOpenProducts
                        sx { borderRadius = 12.px; height = 48.px; textTransform = None.none }
                        ArrowBack { sx { marginRight = 6.px; fontSize = 18.px } }
                        +"Continue Shopping"
                    }

                    if (scope.items.isNotEmpty()) {
                        Button {
                            variant = ButtonVariant.contained
                            onClick = onBuy
                            sx {
                                borderRadius = 12.px
                                height = 48.px
                                textTransform = None.none
                                fontWeight = FontWeight.bold
                                fontSize = 16.px
                                backgroundColor = ShoppingColors.PriceColor.unsafeCast<BackgroundColor>()
                            }
                            +"Buy"
                        }
                    }
                }
            }
        }
    }
}
