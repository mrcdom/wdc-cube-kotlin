package br.com.wdc.shopping.nativeui.web.views

import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.nativeui.web.bridge.WorkerProxy
import br.com.wdc.shopping.nativeui.web.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.web.util.formatPrice
import br.com.wdc.shopping.nativeui.web.util.productImageUrl
import br.com.wdc.shopping.nativeui.web.util.stripHtml
import mui.material.Box
import mui.material.Card
import mui.material.CardActionArea
import mui.material.CardContent
import mui.material.Chip
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
import react.useEffect
import react.useState
import web.cssom.*

private class ProductItem(
    val id: Long,
    val image: String?,
    val name: String?,
    val description: String?,
    val price: Double
)

class ProductsPanelView(viewId: String, proxy: WorkerProxy) : ReactCubeView(viewId, proxy) {

    // Local state
    private var products: List<ProductItem>? = null

    override fun readState(json: String) {
        val inp = JsonInputFactory.createStringInput(json).input
        inp.beginObject()
        while (inp.hasNext()) {
            when (inp.nextName()) {
                "id" -> inp.skipValue()
                "products" -> {
                    val list = mutableListOf<ProductItem>()
                    inp.beginArray()
                    while (inp.hasNext()) {
                        var id = 0L
                        var image: String? = null
                        var name: String? = null
                        var description: String? = null
                        var price = 0.0
                        inp.beginObject()
                        while (inp.hasNext()) {
                            when (inp.nextName()) {
                                "id" -> id = inp.nextLong()
                                "image" -> image = inp.nextString()
                                "name" -> name = inp.nextString()
                                "description" -> description = inp.nextString()
                                "price" -> price = inp.nextDouble()
                                else -> inp.skipValue()
                            }
                        }
                        inp.endObject()
                        list.add(ProductItem(id, image, name, description, price))
                    }
                    inp.endArray()
                    products = list
                }
                else -> inp.skipValue()
            }
        }
        inp.endObject()
    }

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@ProductsPanelView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        val prods = products

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
                    +"Produtos"
                }

                if (prods != null) {
                    Box {
                        sx {
                            backgroundColor = ShoppingColors.SecondaryContainer.unsafeCast<BackgroundColor>()
                            borderRadius = 8.px
                            padding = Padding(6.px, 16.px)
                        }
                        Typography {
                            variant = TypographyVariant.caption
                            sx {
                                color = ShoppingColors.OnPrimaryContainer.unsafeCast<Color>()
                            }
                            +"${prods.size} itens"
                        }
                    }
                }
            }

            Divider { sx { marginBottom = 12.px } }

            if (prods == null) {
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
                        sx { marginTop = 12.px; color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                        +"Carregando produtos..."
                    }
                }
            } else if (prods.isEmpty()) {
                Box {
                    sx {
                        display = Display.flex
                        justifyContent = JustifyContent.center
                        padding = 48.px
                    }
                    Typography {
                        variant = TypographyVariant.body1
                        sx { color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                        +"Nenhum produto disponível"
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

                    for (product in prods) {
                        Card {
                            key = "${product.id}"
                            elevation = 0
                            sx {
                                borderRadius = 8.px
                                asDynamic()["&:hover"] = js("({'boxShadow': '0 2px 8px rgba(0,0,0,0.12)'})")
                            }

                            CardActionArea {
                                onClick = { action("onOpenProduct", product.id) }

                                // Product image
                                img {
                                    src = productImageUrl(product.id)
                                    alt = product.name ?: ""
                                    style = js("({width: '100%', height: '140px', objectFit: 'contain', backgroundColor: '${ShoppingColors.SurfaceVariant}'})").unsafeCast<react.CSSProperties>()
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
                                                color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>()
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
                                        label = ReactNode("R$ ${formatPrice(product.price)}")
                                        sx {
                                            marginTop = 12.px
                                            backgroundColor = ShoppingColors.PriceBackground.unsafeCast<BackgroundColor>()
                                            color = ShoppingColors.PriceColor.unsafeCast<Color>()
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
