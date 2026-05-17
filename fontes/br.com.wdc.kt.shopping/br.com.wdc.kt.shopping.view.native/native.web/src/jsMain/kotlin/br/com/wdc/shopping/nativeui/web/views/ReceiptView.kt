package br.com.wdc.shopping.nativeui.web.views

import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.nativeui.web.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.web.theme.ShoppingStyles
import br.com.wdc.shopping.nativeui.web.util.formatDate
import br.com.wdc.shopping.nativeui.web.util.formatPrice
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import mui.icons.material.ArrowBack
import mui.icons.material.CheckCircle
import mui.icons.material.Receipt
import mui.material.Alert
import mui.material.AlertColor
import mui.material.Box
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Chip
import mui.material.Divider
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
import react.useEffect
import react.useState
import web.cssom.*

class ReceiptView(private val presenter: ReceiptPresenter) : ReactCubeView("receipt-view", presenter.app) {

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@ReceiptView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        val state = presenter.state
        val receipt = state.receipt

        Box {
            sx {
                display = Display.flex
                justifyContent = JustifyContent.center
                padding = 16.px
            }

            Box {
                sx { maxWidth = 600.px; width = 100.pct }

                Stack {
                    direction = responsive(StackDirection.column)
                    spacing = responsive(2)

                    // Success banner
                    if (state.notifySuccess) {
                        Paper {
                            sx {
                                padding = 20.px
                                borderRadius = 12.px
                                backgroundColor = ShoppingColors.SuccessContainer.unsafeCast<BackgroundColor>()
                                textAlign = TextAlign.center
                            }
                            elevation = 0

                            Stack {
                                direction = responsive(StackDirection.column)
                                sx { alignItems = AlignItems.center }
                                spacing = responsive(1)

                                CheckCircle {
                                    sx { fontSize = 32.px; color = ShoppingColors.SuccessColor.unsafeCast<Color>() }
                                }
                                Typography {
                                    variant = TypographyVariant.subtitle1
                                    asDynamic().style = ShoppingStyles.successText
                                    +"Compra realizada com sucesso!"
                                }
                            }
                        }
                    }

                    if (receipt != null) {
                        // Receipt header
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
                                    asDynamic().style = ShoppingStyles.titleH5
                                    +"Recibo"
                                }
                            }

                            receipt.date?.let { date ->
                                Chip {
                                    label = ReactNode(formatDate(date))
                                    sx {
                                        backgroundColor = ShoppingColors.SurfaceVariant.unsafeCast<BackgroundColor>()
                                    }
                                    size = mui.material.Size.small
                                }
                            }
                        }

                        Divider {}

                        // Items header
                        Stack {
                            direction = responsive(StackDirection.row)
                            sx {
                                justifyContent = JustifyContent.spaceBetween
                                paddingLeft = 4.px
                                paddingRight = 4.px
                            }

                            Typography {
                                variant = TypographyVariant.caption
                                sx { flex = Flex(number(1.0), number(1.0), 0.px); color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                                +"Item"
                            }
                            Typography {
                                variant = TypographyVariant.caption
                                sx { width = 50.px; textAlign = TextAlign.center; color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                                +"Qtd"
                            }
                            Typography {
                                variant = TypographyVariant.caption
                                sx { width = 100.px; textAlign = TextAlign.end; color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                                +"Valor"
                            }
                        }

                        // Items
                        Stack {
                            direction = responsive(StackDirection.column)
                            spacing = responsive(1)

                            for (item in receipt.items) {
                                Paper {
                                    key = "${item.id}"
                                    sx {
                                        padding = 12.px
                                        borderRadius = 8.px
                                        backgroundColor = ShoppingColors.SurfaceVariant40.unsafeCast<BackgroundColor>()
                                    }
                                    elevation = 0

                                    Stack {
                                        direction = responsive(StackDirection.row)
                                        sx { justifyContent = JustifyContent.spaceBetween; alignItems = AlignItems.center }

                                        Typography {
                                            variant = TypographyVariant.body2
                                            sx { flex = Flex(number(1.0), number(1.0), 0.px) }
                                            asDynamic().style = ShoppingStyles.fontMedium
                                            +(item.description ?: "")
                                        }
                                        Typography {
                                            variant = TypographyVariant.body2
                                            sx { width = 50.px; textAlign = TextAlign.center; color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                                            +"${item.quantity}x"
                                        }
                                        Typography {
                                            variant = TypographyVariant.body2
                                            sx {
                                                width = 100.px
                                                textAlign = TextAlign.end
                                                color = ShoppingColors.PriceColor.unsafeCast<Color>()
                                            }
                                            asDynamic().style = ShoppingStyles.fontMedium
                                            +"R$ ${formatPrice(item.value)}"
                                        }
                                    }
                                }
                            }
                        }

                        // Total
                        Divider {}
                        Stack {
                            direction = responsive(StackDirection.row)
                            sx { justifyContent = JustifyContent.flexEnd; alignItems = AlignItems.center }
                            spacing = responsive(1)

                            Typography {
                                variant = TypographyVariant.subtitle1
                                sx { color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                                +"Total: "
                            }

                            receipt.total?.let { total ->
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
                                        +"R$ ${formatPrice(total)}"
                                    }
                                }
                            }
                        }
                    }

                    // Action button
                    Stack {
                        direction = responsive(StackDirection.row)
                        sx { justifyContent = JustifyContent.flexEnd }

                        Button {
                            variant = ButtonVariant.contained
                            onClick = { safeCall { presenter.onOpenProducts() } }
                            asDynamic().style = ShoppingStyles.fontMedium
                            sx { borderRadius = 12.px; height = 48.px; textTransform = None.none }
                            ArrowBack { sx { marginRight = 6.px; fontSize = 18.px } }
                            +"Continuar Comprando"
                        }
                    }
                }
            }
        }
    }
}
