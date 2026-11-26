package br.com.wdc.store.view.remote.react.kotlin.views.home

import br.com.wdc.store.design.StoreTokens
import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewProps
import br.com.wdc.framework.cube.remote.bridge.react.useView
import br.com.wdc.store.view.remote.react.kotlin.theme.StoreColors
import br.com.wdc.store.view.remote.react.kotlin.theme.jsObject
import br.com.wdc.store.view.remote.react.kotlin.util.formatPrice
import br.com.wdc.store.view.remote.react.kotlin.util.productImagePath
import br.com.wdc.store.view.remote.react.kotlin.views.useBridge
import mui.material.Box
import mui.material.Card
import mui.material.CardActionArea
import mui.material.CardContent
import mui.material.Chip
import mui.material.CircularProgress
import mui.material.Divider
import mui.material.Size
import mui.material.Stack
import mui.material.StackDirection
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.FC
import react.ReactNode
import react.dom.html.ReactHTML.img
import web.cssom.*

// :: Events of the products panel
private const val ON_OPEN_PRODUCT = 1

/** The home's products panel: the catalog as a grid of cards; a card opens its product. */
val ProductsPanelView = FC<RemoteViewProps> { props ->
    val bridge = useBridge()
    val scope = bridge.useView(props.vsid)
    // absent until the server has loaded them
    val products = if (scope.has("products")) scope.objects("products") else null

    Box {
        sx { padding = Padding(StoreTokens.Spacing.Md.px, 6.px, StoreTokens.Spacing.Md.px, StoreTokens.Spacing.Md.px) }

        // Section header
        Stack {
            direction = responsive(StackDirection.row)
            sx {
                justifyContent = JustifyContent.spaceBetween
                alignItems = AlignItems.center
                marginBottom = StoreTokens.Spacing.Md.px
            }
            Typography {
                variant = TypographyVariant.h6
                sx { fontWeight = FontWeight.bold }
                +"Products"
            }
            if (products != null) {
                Box {
                    sx {
                        backgroundColor = StoreColors.SecondaryContainer.unsafeCast<BackgroundColor>()
                        borderRadius = StoreTokens.Radius.Sm.px
                        padding = Padding(6.px, StoreTokens.Spacing.Lg.px)
                    }
                    Typography {
                        variant = TypographyVariant.caption
                        sx { color = StoreColors.OnPrimaryContainer.unsafeCast<Color>() }
                        +"${products.size} ${if (products.size == 1) "item" else "items"}"
                    }
                }
            }
        }

        Divider { sx { marginBottom = StoreTokens.Spacing.Md.px } }

        when {
            products == null -> Box {
                sx {
                    display = Display.flex
                    flexDirection = FlexDirection.column
                    alignItems = AlignItems.center
                    justifyContent = JustifyContent.center
                    padding = 48.px
                }
                CircularProgress()
                Typography {
                    variant = TypographyVariant.body2
                    sx { marginTop = StoreTokens.Spacing.Md.px; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                    +"Loading products..."
                }
            }

            products.isEmpty() -> Box {
                sx { display = Display.flex; justifyContent = JustifyContent.center; padding = 48.px }
                Typography {
                    variant = TypographyVariant.body1
                    sx { color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                    +"No products available"
                }
            }

            else -> Box {
                sx {
                    display = Display.grid
                    gridTemplateColumns = "repeat(auto-fill, minmax(160px, 1fr))".unsafeCast<GridTemplateColumns>()
                    gap = StoreTokens.Spacing.Md.px
                }
                for (product in products) {
                    val id = product["id"]
                    val name = product.string("name") ?: ""
                    Card {
                        key = "$id"
                        elevation = 0
                        sx {
                            borderRadius = StoreTokens.Radius.Sm.px
                            asDynamic()["&:hover"] = js("({boxShadow: '0 2px 8px rgba(0,0,0,0.12)'})")
                        }
                        CardActionArea {
                            onClick = {
                                bridge.setFormField(props.vsid, "p.productId", id)
                                bridge.submit(props.vsid, ON_OPEN_PRODUCT)
                            }
                            img {
                                src = productImagePath(id)
                                alt = name
                                style = jsObject(
                                    "width" to "100%",
                                    "height" to "140px",
                                    "objectFit" to "contain",
                                    "backgroundColor" to StoreColors.SurfaceVariant,
                                ).unsafeCast<react.CSSProperties>()
                            }
                            CardContent {
                                Typography {
                                    variant = TypographyVariant.subtitle1
                                    sx { fontWeight = FontWeight.bold }
                                    +name
                                }
                                Chip {
                                    label = ReactNode("\$${formatPrice(product.double("price"))}")
                                    size = Size.small
                                    sx {
                                        marginTop = StoreTokens.Spacing.Md.px
                                        backgroundColor = StoreColors.PriceBackground.unsafeCast<BackgroundColor>()
                                        color = StoreColors.PriceColor.unsafeCast<Color>()
                                        fontWeight = FontWeight.bold
                                        borderRadius = StoreTokens.Radius.Sm.px
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
