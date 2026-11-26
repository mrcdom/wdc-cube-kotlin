package br.com.wdc.store.nativeui.web.views

import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.nativeui.web.bridge.ScopeProps
import br.com.wdc.store.nativeui.web.bridge.safeCall
import br.com.wdc.store.nativeui.web.bridge.useScope
import br.com.wdc.store.nativeui.web.theme.StoreColors
import br.com.wdc.store.nativeui.web.theme.jsObject
import br.com.wdc.store.nativeui.web.util.formatPrice
import br.com.wdc.store.nativeui.web.util.productImageUrl
import br.com.wdc.store.nativeui.web.util.stripHtml
import br.com.wdc.store.presentation.presenter.restricted.home.ProductsPanelScope
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
import react.Key
import react.ReactNode
import react.dom.html.ReactHTML.img
import web.cssom.*

val ProductsPanelView = FC<ScopeProps<ProductsPanelScope>> { props ->
    val scope = useScope(props.scope)

    val products = scope.products

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
                        sx {
                            color = StoreColors.OnPrimaryContainer.unsafeCast<Color>()
                        }
                        +"${products.size} ${if (products.size == 1) "item" else "items"}"
                    }
                }
            }
        }

        Divider { sx { marginBottom = StoreTokens.Spacing.Md.px } }

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
                    sx { marginTop = StoreTokens.Spacing.Md.px; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
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
                    gap = StoreTokens.Spacing.Md.px
                }

                for (product in products) {
                    Card {
                        key = Key("${product.id}")
                        elevation = 0
                        sx {
                            borderRadius = StoreTokens.Radius.Sm.px
                            asDynamic()["&:hover"] = js("({'boxShadow': '0 2px 8px rgba(0,0,0,0.12)'})")
                        }

                        CardActionArea {
                            onClick = { safeCall { scope.onOpenProduct(product.id) } }

                            // Product image
                            img {
                                src = productImageUrl(product.id)
                                alt = product.name ?: ""
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
                                        marginTop = StoreTokens.Spacing.Md.px
                                        backgroundColor = StoreColors.PriceBackground.unsafeCast<BackgroundColor>()
                                        color = StoreColors.PriceColor.unsafeCast<Color>()
                                        fontWeight = FontWeight.bold
                                        borderRadius = StoreTokens.Radius.Sm.px
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
