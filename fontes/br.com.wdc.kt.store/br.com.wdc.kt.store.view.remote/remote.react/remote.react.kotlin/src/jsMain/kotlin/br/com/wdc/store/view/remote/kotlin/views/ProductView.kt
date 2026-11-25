package br.com.wdc.store.view.remote.kotlin.views

import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewProps
import br.com.wdc.framework.cube.remote.bridge.react.useView
import br.com.wdc.store.view.remote.kotlin.theme.StoreColors
import br.com.wdc.store.view.remote.kotlin.util.formatPrice
import br.com.wdc.store.view.remote.kotlin.util.productImagePath
import br.com.wdc.store.view.remote.kotlin.util.stripHtml
import mui.icons.material.Add
import mui.icons.material.AddShoppingCart
import mui.icons.material.ArrowBack
import mui.icons.material.Remove
import mui.material.Alert
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.CircularProgress
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
import react.create
import react.dom.html.ReactHTML.img
import react.useEffect
import react.useState
import web.cssom.*

// :: Events of the product view
private const val ON_OPEN_PRODUCTS = 1
private const val ON_ADD_TO_CART = 2

/**
 * A product: name, description, price and image, with a quantity chosen here (client-side state, starting at 1) that
 * goes as `p.quantity` with "Add to Cart".
 */
val ProductView = FC<RemoteViewProps> { props ->
    val bridge = useBridge()
    val scope = bridge.useView(props.vsid)
    val product = scope.obj("product")
    val productId = product?.get("id")

    var quantity by useState(1)
    // another product, a fresh quantity
    useEffect(productId) { quantity = 1 }

    if (product == null) {
        Stack {
            direction = responsive(StackDirection.column)
            spacing = responsive(2)
            sx { alignItems = AlignItems.center; padding = 48.px }
            CircularProgress()
            Typography {
                variant = TypographyVariant.body2
                sx { color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                +"Loading product..."
            }
        }
        return@FC
    }

    val name = product.string("name") ?: ""
    val description = product.string("description")?.let(::stripHtml) ?: ""

    Box {
        sx {
            display = Display.flex
            justifyContent = JustifyContent.center
            padding = Padding(16.px, 10.px, 16.px, 16.px)
        }
        Box {
            sx { maxWidth = 600.px; width = 100.pct }
            Stack {
                direction = responsive(StackDirection.column)
                spacing = responsive(2)

                val errorMessage = scope.string("errorMessage")
                if (!errorMessage.isNullOrEmpty()) {
                    Alert {
                        severity = "error"
                        sx { borderRadius = 8.px }
                        +errorMessage
                    }
                }

                Typography {
                    variant = TypographyVariant.h4
                    sx { fontWeight = FontWeight.bold }
                    +name
                }

                Divider()

                if (description.isNotEmpty()) {
                    Paper {
                        elevation = 0
                        sx {
                            padding = 16.px
                            borderRadius = 8.px
                            backgroundColor = StoreColors.SurfaceVariant50.unsafeCast<BackgroundColor>()
                        }
                        Typography {
                            variant = TypographyVariant.body1
                            sx {
                                color = StoreColors.OnSurfaceVariant.unsafeCast<Color>()
                                lineHeight = 24.px.unsafeCast<LineHeight>()
                                whiteSpace = WhiteSpace.preWrap
                            }
                            +description
                        }
                    }
                }

                // Price and quantity, then the image
                Stack {
                    direction = responsive(StackDirection.row)
                    spacing = responsive(3)
                    sx { alignItems = AlignItems.center }

                    Stack {
                        direction = responsive(StackDirection.column)
                        spacing = responsive(2)
                        sx { flex = Flex(number(1.0), number(1.0), 0.px) }

                        Box {
                            sx {
                                backgroundColor = StoreColors.PriceBackground.unsafeCast<BackgroundColor>()
                                borderRadius = 12.px
                                paddingTop = 12.px
                                paddingBottom = 12.px
                                textAlign = TextAlign.center
                            }
                            Typography {
                                variant = TypographyVariant.body1
                                sx {
                                    color = StoreColors.PriceColor.unsafeCast<Color>()
                                    fontWeight = integer(500).unsafeCast<FontWeight>()
                                    fontSize = 16.px
                                }
                                +"\$${formatPrice(product.double("price"))}"
                            }
                        }

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
                                elevation = 0
                                sx {
                                    borderRadius = 12.px
                                    backgroundColor = StoreColors.SurfaceVariant.unsafeCast<BackgroundColor>()
                                    display = Display.flex
                                    alignItems = AlignItems.center
                                    paddingLeft = 4.px
                                    paddingRight = 4.px
                                }
                                IconButton {
                                    size = Size.small
                                    disabled = quantity <= 1
                                    onClick = { if (quantity > 1) quantity -= 1 }
                                    Remove()
                                }
                                Typography {
                                    variant = TypographyVariant.h6
                                    sx { fontWeight = FontWeight.bold; paddingLeft = 20.px; paddingRight = 20.px }
                                    +"$quantity"
                                }
                                IconButton {
                                    size = Size.small
                                    onClick = { quantity += 1 }
                                    Add()
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
                            backgroundColor = StoreColors.SurfaceVariant.unsafeCast<BackgroundColor>()
                        }
                        img {
                            src = productImagePath(productId)
                            alt = name
                            style = js("({width: '120px', height: '120px', objectFit: 'contain'})")
                                .unsafeCast<react.CSSProperties>()
                        }
                    }
                }

                Stack {
                    direction = responsive(StackDirection.row)
                    spacing = responsive(2)
                    sx { justifyContent = JustifyContent.center }
                    Button {
                        variant = ButtonVariant.outlined
                        startIcon = ArrowBack.create()
                        onClick = { bridge.submit(props.vsid, ON_OPEN_PRODUCTS) }
                        sx { borderRadius = 12.px; height = 48.px; textTransform = None.none }
                        +"Back"
                    }
                    Button {
                        variant = ButtonVariant.contained
                        startIcon = AddShoppingCart.create()
                        onClick = {
                            bridge.setFormField(props.vsid, "p.quantity", quantity)
                            bridge.submit(props.vsid, ON_ADD_TO_CART)
                        }
                        sx { borderRadius = 12.px; height = 48.px; textTransform = None.none }
                        +"Add to Cart"
                    }
                }
            }
        }
    }
}
