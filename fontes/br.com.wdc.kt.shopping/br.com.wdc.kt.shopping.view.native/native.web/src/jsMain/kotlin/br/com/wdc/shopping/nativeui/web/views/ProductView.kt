package br.com.wdc.shopping.nativeui.web.views

import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.nativeui.web.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.web.util.formatPrice
import br.com.wdc.shopping.nativeui.web.util.productImageUrl
import br.com.wdc.shopping.nativeui.web.util.stripHtml
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.shopping.presentation.presenter.RootPresenter
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
import react.Props
import react.ReactNode
import react.dom.html.ReactHTML.img
import react.dom.events.MouseEventHandler
import react.useEffect
import react.useState
import web.cssom.*
import web.html.HTMLButtonElement

class ProductView(
    app: RootPresenter,
    scope: ProductScope,
) : ReactCubeView<ProductScope>("product-view", app, scope) {

    /** The quantity being chosen: UI state of this view, changed by the emitters below. */
    private var quantity = 1

    // :: Emitters — created once per view, so they are the same function on every render

    private val onDecrement: MouseEventHandler<HTMLButtonElement> = {
        if (quantity > 1) {
            quantity--
            forceUpdate()
        }
    }

    private val onIncrement: MouseEventHandler<HTMLButtonElement> = {
        quantity++
        forceUpdate()
    }

    private val onOpenProducts: MouseEventHandler<HTMLButtonElement> = { safeCall { scope.onOpenProducts() } }

    private val onAddToCart: MouseEventHandler<HTMLButtonElement> = {
        val chosen = quantity
        safeCall { scope.onAddToCart(chosen) }
    }

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@ProductView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        val product = scope.product

        Box {
            sx {
                display = Display.flex
                justifyContent = JustifyContent.center
                padding = Padding(16.px, 10.px, 16.px, 16.px)
            }

            if (product != null) {
                Box {
                    sx { maxWidth = 600.px; width = 100.pct }

                    Stack {
                        direction = responsive(StackDirection.column)
                        spacing = responsive(2)

                        // Error message
                        val errorMessage = scope.errorMessage
                        if (!errorMessage.isNullOrBlank()) {
                            Alert {
                                severity = "error"
                                sx { borderRadius = 8.px }
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
                                        padding = 16.px
                                        borderRadius = 8.px
                                        backgroundColor = ShoppingColors.SurfaceVariant50.unsafeCast<BackgroundColor>()
                                    }
                                    elevation = 0
                                    Typography {
                                        variant = TypographyVariant.body1
                                        sx {
                                            color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>()
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
                                        backgroundColor = ShoppingColors.PriceBackground.unsafeCast<BackgroundColor>()
                                        color = ShoppingColors.PriceColor.unsafeCast<Color>()
                                        height = 40.px
                                        borderRadius = 12.px
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
                                        +"Qty:"
                                    }

                                    Paper {
                                        sx {
                                            borderRadius = 12.px
                                            backgroundColor = ShoppingColors.SurfaceVariant.unsafeCast<BackgroundColor>()
                                            display = Display.flex
                                            alignItems = AlignItems.center
                                            paddingLeft = 4.px
                                            paddingRight = 4.px
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
                                    borderRadius = 12.px
                                    overflow = Overflow.hidden
                                    backgroundColor = ShoppingColors.SurfaceVariant.unsafeCast<BackgroundColor>()
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
                                sx { borderRadius = 12.px; height = 48.px; textTransform = None.none }
                                ArrowBack { sx { marginRight = 6.px; fontSize = 18.px } }
                                +"Back"
                            }

                            Button {
                                variant = ButtonVariant.contained
                                onClick = onAddToCart
                                sx { borderRadius = 12.px; height = 48.px; textTransform = None.none }
                                AddShoppingCart { sx { marginRight = 6.px; fontSize = 18.px } }
                                +"Add to Cart"
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
                        sx { color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                        +"Loading product..."
                    }
                }
            }
        }
    }
}
