package br.com.wdc.store.view.remote.react.kotlin.views

import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewProps
import br.com.wdc.framework.cube.remote.bridge.react.useView
import br.com.wdc.store.view.remote.react.kotlin.theme.StoreColors
import br.com.wdc.store.view.remote.react.kotlin.util.formatDate
import br.com.wdc.store.view.remote.react.kotlin.util.formatPrice
import mui.icons.material.ArrowBack
import mui.icons.material.CheckCircle
import mui.icons.material.Receipt
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Chip
import mui.material.CircularProgress
import mui.material.Divider
import mui.material.Paper
import mui.material.Size
import mui.material.Stack
import mui.material.StackDirection
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.FC
import react.ReactNode
import react.create
import web.cssom.*

// :: Events of the receipt view
private const val ON_OPEN_PRODUCTS = 1

/** A purchase's receipt; right after buying, with the success banner (`notifySuccess`). */
val ReceiptView = FC<RemoteViewProps> { props ->
    val bridge = useBridge()
    val scope = bridge.useView(props.vsid)
    val receipt = scope.obj("receipt")
    val muted = StoreColors.OnSurfaceVariant.unsafeCast<Color>()
    val priceColor = StoreColors.PriceColor.unsafeCast<Color>()
    val medium = integer(500).unsafeCast<FontWeight>()

    if (receipt == null) {
        Box {
            sx { display = Display.flex; justifyContent = JustifyContent.center; padding = 48.px }
            CircularProgress()
        }
        return@FC
    }

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

                if (scope.boolean("notifySuccess")) {
                    Paper {
                        elevation = 0
                        sx {
                            padding = 20.px
                            borderRadius = 12.px
                            backgroundColor = StoreColors.SuccessContainer.unsafeCast<BackgroundColor>()
                            textAlign = TextAlign.center
                        }
                        Stack {
                            direction = responsive(StackDirection.column)
                            spacing = responsive(1)
                            sx { alignItems = AlignItems.center }
                            CheckCircle {
                                sx { fontSize = 32.px; color = StoreColors.SuccessColor.unsafeCast<Color>() }
                            }
                            Typography {
                                variant = TypographyVariant.subtitle1
                                sx { fontWeight = medium; color = StoreColors.SuccessColor.unsafeCast<Color>() }
                                +"Purchase completed successfully!"
                            }
                        }
                    }
                }

                // Header
                Stack {
                    direction = responsive(StackDirection.row)
                    sx { justifyContent = JustifyContent.spaceBetween; alignItems = AlignItems.center }
                    Stack {
                        direction = responsive(StackDirection.row)
                        spacing = responsive(1)
                        sx { alignItems = AlignItems.center }
                        Receipt { sx { fontSize = 28.px } }
                        Typography {
                            variant = TypographyVariant.h5
                            sx { fontWeight = medium; fontSize = 28.px }
                            +"Receipt"
                        }
                    }
                    receipt.double("date")?.let { date ->
                        Chip {
                            label = ReactNode(formatDate(date))
                            size = Size.small
                            sx { backgroundColor = StoreColors.SurfaceVariant.unsafeCast<BackgroundColor>() }
                        }
                    }
                }

                Divider()

                // Column titles
                Stack {
                    direction = responsive(StackDirection.row)
                    sx { justifyContent = JustifyContent.spaceBetween; paddingLeft = 4.px; paddingRight = 4.px }
                    Typography {
                        variant = TypographyVariant.caption
                        sx { flex = Flex(number(1.0), number(1.0), 0.px); color = muted }
                        +"Item"
                    }
                    Typography {
                        variant = TypographyVariant.caption
                        sx { width = 50.px; textAlign = TextAlign.center; color = muted }
                        +"Qty"
                    }
                    Typography {
                        variant = TypographyVariant.caption
                        sx { width = 100.px; textAlign = TextAlign.right; color = muted }
                        +"Amount"
                    }
                }

                // Items
                Stack {
                    direction = responsive(StackDirection.column)
                    spacing = responsive(1)
                    receipt.objects("items").forEachIndexed { index, item ->
                        Paper {
                            key = "$index"
                            elevation = 0
                            sx {
                                padding = 12.px
                                borderRadius = 8.px
                                backgroundColor = StoreColors.SurfaceVariant40.unsafeCast<BackgroundColor>()
                            }
                            Stack {
                                direction = responsive(StackDirection.row)
                                sx { justifyContent = JustifyContent.spaceBetween; alignItems = AlignItems.center }
                                Typography {
                                    variant = TypographyVariant.body2
                                    sx { flex = Flex(number(1.0), number(1.0), 0.px); fontWeight = medium }
                                    +(item.string("description") ?: "")
                                }
                                Typography {
                                    variant = TypographyVariant.body2
                                    sx { width = 50.px; textAlign = TextAlign.center; color = muted }
                                    +"${item.int("quantity") ?: 0}x"
                                }
                                Typography {
                                    variant = TypographyVariant.body2
                                    sx {
                                        width = 100.px
                                        textAlign = TextAlign.right
                                        color = priceColor
                                        fontWeight = medium
                                    }
                                    +"\$${formatPrice(item.double("value"))}"
                                }
                            }
                        }
                    }
                }

                // Total
                Divider()
                Stack {
                    direction = responsive(StackDirection.row)
                    spacing = responsive(1)
                    sx { justifyContent = JustifyContent.flexEnd; alignItems = AlignItems.center }
                    Typography {
                        variant = TypographyVariant.subtitle1
                        sx { color = muted }
                        +"Total:"
                    }
                    Box {
                        sx {
                            backgroundColor = StoreColors.PriceBackground.unsafeCast<BackgroundColor>()
                            borderRadius = 10.px
                            padding = Padding(6.px, 14.px)
                        }
                        Typography {
                            variant = TypographyVariant.h6
                            sx { fontWeight = FontWeight.bold; fontSize = 22.px; color = priceColor }
                            +"\$${formatPrice(receipt.double("total"))}"
                        }
                    }
                }

                Stack {
                    direction = responsive(StackDirection.row)
                    sx { justifyContent = JustifyContent.flexEnd }
                    Button {
                        variant = ButtonVariant.contained
                        startIcon = ArrowBack.create()
                        onClick = { bridge.submit(props.vsid, ON_OPEN_PRODUCTS) }
                        sx { borderRadius = 12.px; height = 48.px; textTransform = None.none; fontWeight = medium }
                        +"Continue Shopping"
                    }
                }
            }
        }
    }
}
