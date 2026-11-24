package br.com.wdc.store.nativeui.web.views

import br.com.wdc.store.nativeui.web.bridge.EmitterCache
import br.com.wdc.store.nativeui.web.bridge.ReactCubeView
import br.com.wdc.store.nativeui.web.theme.StoreColors
import br.com.wdc.store.nativeui.web.util.formatPrice
import br.com.wdc.store.nativeui.web.util.productImageUrl
import br.com.wdc.store.nativeui.web.util.stripHtml
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
import br.com.wdc.store.presentation.presenter.RootPresenter
import mui.icons.material.ShoppingBag
import mui.material.Box
import mui.material.Card
import mui.material.CardActionArea
import mui.material.CardContent
import mui.material.CardMedia
import mui.material.Chip
import mui.material.ChipColor
import mui.material.CircularProgress
import mui.material.Divider
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

class ProductsPanelView(
    app: RootPresenter,
    scope: ProductsPanelScope,
) : ReactCubeView<ProductsPanelScope>("products-panel-view", app, scope) {

    // :: Emitters — created once per view (and once per list item), so they are the same function on every render

    private val openProduct = EmitterCache<Long, MouseEventHandler<HTMLButtonElement>> { id ->
        { safeCall { scope.onOpenProduct(id) } }
    }

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@ProductsPanelView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        val products = scope.products

        Box {
            sx { padding = Padding(12.px, 6.px, 12.px, 12.px) }

            // Section header
            Stack {
                direction = responsive(StackDirection.row)
                sx {
                    justifyContent = JustifyContent.spaceBetween
                    alignItems = AlignItems.center
                    marginBottom = 12.px
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
                            borderRadius = 8.px
                            padding = Padding(6.px, 16.px)
                        }
                        Typography {
                            variant = TypographyVariant.caption
                            sx {
                                color = StoreColors.OnPrimaryContainer.unsafeCast<Color>()
                            }
                            +"${products.size} ${if (products.size == 1) "item" else "items"}"
                        }
                    }
                }
            }

            Divider { sx { marginBottom = 12.px } }

            if (products == null) {
                // Loading
                Box {
                    sx {
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        alignItems = AlignItems.center
                        justifyContent = JustifyContent.center
                        padding = 48.px
                    }
                    CircularProgress {}
                    Typography {
                        variant = TypographyVariant.body2
                        sx { marginTop = 12.px; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                        +"Loading products..."
                    }
                }
            } else if (products.isEmpty()) {
                Box {
                    sx {
                        display = Display.flex
                        justifyContent = JustifyContent.center
                        padding = 48.px
                    }
                    Typography {
                        variant = TypographyVariant.body1
                        sx { color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                        +"No products available"
                    }
                }
            } else {
                // Product grid (auto-fill flow)
                Box {
                    sx {
                        display = Display.grid
                        asDynamic().gridTemplateColumns = "repeat(auto-fill, minmax(160px, 1fr))"
                        gap = 12.px
                    }

                    openProduct.retainOnly(products.map { it.id })
                    for (product in products) {
                        Card {
                            key = "${product.id}"
                            elevation = 0
                            sx {
                                borderRadius = 8.px
                                asDynamic()["&:hover"] = js("({'boxShadow': '0 2px 8px rgba(0,0,0,0.12)'})")
                            }

                            CardActionArea {
                                onClick = openProduct[product.id]

                                // Product image
                                img {
                                    src = productImageUrl(product.id)
                                    alt = product.name ?: ""
                                    style = js("({width: '100%', height: '140px', objectFit: 'contain', backgroundColor: '${StoreColors.SurfaceVariant}'})").unsafeCast<react.CSSProperties>()
                                }

                                CardContent {
                                    Typography {
                                        variant = TypographyVariant.subtitle1
                                        sx { fontWeight = FontWeight.bold }
                                        +(product.name ?: "")
                                    }

                                    val desc = product.description
                                    if (!desc.isNullOrBlank() && desc != "unknown") {
                                        Typography {
                                            variant = TypographyVariant.body2
                                            sx {
                                                color = StoreColors.OnSurfaceVariant.unsafeCast<Color>()
                                                overflow = Overflow.hidden
                                                display = Display.block
                                                asDynamic().WebkitLineClamp = 2
                                                asDynamic().WebkitBoxOrient = "vertical"
                                                asDynamic().display = "-webkit-box"
                                            }
                                            +stripHtml(desc)
                                        }
                                    }

                                    Chip {
                                        label = ReactNode("\$${formatPrice(product.price)}")
                                        sx {
                                            marginTop = 12.px
                                            backgroundColor = StoreColors.PriceBackground.unsafeCast<BackgroundColor>()
                                            color = StoreColors.PriceColor.unsafeCast<Color>()
                                            fontWeight = FontWeight.bold
                                            borderRadius = 8.px
                                        }
                                        size = mui.material.Size.small
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
