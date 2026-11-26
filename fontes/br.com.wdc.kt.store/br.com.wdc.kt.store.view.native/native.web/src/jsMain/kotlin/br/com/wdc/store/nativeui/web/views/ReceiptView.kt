package br.com.wdc.store.nativeui.web.views

import br.com.wdc.store.i18n.*
import br.com.wdc.store.nativeui.web.util.messages
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.nativeui.web.bridge.ScopeProps
import br.com.wdc.store.nativeui.web.bridge.safeCall
import br.com.wdc.store.nativeui.web.bridge.useScope
import br.com.wdc.store.nativeui.web.theme.StoreColors
import br.com.wdc.store.nativeui.web.theme.StoreStyles
import br.com.wdc.store.nativeui.web.util.formatDate
import br.com.wdc.store.nativeui.web.util.formatPrice
import br.com.wdc.store.presentation.presenter.restricted.receipt.ReceiptScope
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
import react.Key
import react.ReactNode
import react.dom.events.MouseEventHandler
import web.cssom.*
import web.html.HTMLButtonElement

val ReceiptView = FC<ScopeProps<ReceiptScope>> { props ->
    val scope = useScope(props.scope)

    val onOpenProducts: MouseEventHandler<HTMLButtonElement> = { safeCall { scope.onOpenProducts() } }

    val receipt = scope.receipt

    Box {
        sx {
            display = Display.flex
            justifyContent = JustifyContent.center
            padding = Padding(StoreTokens.Spacing.Lg.px, 10.px, StoreTokens.Spacing.Lg.px, StoreTokens.Spacing.Lg.px)
        }

        Box {
            sx { maxWidth = 600.px; width = 100.pct }

            Stack {
                direction = responsive(StackDirection.column)
                spacing = responsive(2)

                // Success banner
                if (scope.notifySuccess) {
                    Paper {
                        sx {
                            padding = 20.px
                            borderRadius = StoreTokens.Radius.Md.px
                            backgroundColor = StoreColors.SuccessContainer.unsafeCast<BackgroundColor>()
                            textAlign = TextAlign.center
                        }
                        elevation = 0

                        Stack {
                            direction = responsive(StackDirection.column)
                            sx { alignItems = AlignItems.center }
                            spacing = responsive(1)

                            CheckCircle {
                                sx { fontSize = 32.px; color = StoreColors.SuccessColor.unsafeCast<Color>() }
                            }
                            Typography {
                                variant = TypographyVariant.subtitle1
                                asDynamic().style = StoreStyles.successText
                                +messages.receiptPurchaseCompleted()
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
                                asDynamic().style = StoreStyles.titleH5
                                +messages.receiptTitle()
                            }
                        }

                        receipt.date?.let { date ->
                            Chip {
                                label = ReactNode(formatDate(date))
                                sx {
                                    backgroundColor = StoreColors.SurfaceVariant.unsafeCast<BackgroundColor>()
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
                            paddingLeft = StoreTokens.Spacing.Xs.px
                            paddingRight = StoreTokens.Spacing.Xs.px
                        }

                        Typography {
                            variant = TypographyVariant.caption
                            sx { flex = Flex(number(1.0), number(1.0), 0.px); color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                            +messages.receiptColumnItem()
                        }
                        Typography {
                            variant = TypographyVariant.caption
                            sx { width = 50.px; textAlign = TextAlign.center; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                            +messages.receiptColumnQty()
                        }
                        Typography {
                            variant = TypographyVariant.caption
                            sx { width = 100.px; textAlign = TextAlign.end; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                            +messages.receiptColumnAmount()
                        }
                    }

                    // Items
                    Stack {
                        direction = responsive(StackDirection.column)
                        spacing = responsive(1)

                        for (item in receipt.items) {
                            Paper {
                                key = Key("${item.id}")
                                sx {
                                    padding = StoreTokens.Spacing.Md.px
                                    borderRadius = StoreTokens.Radius.Sm.px
                                    backgroundColor = StoreColors.SurfaceVariant40.unsafeCast<BackgroundColor>()
                                }
                                elevation = 0

                                Stack {
                                    direction = responsive(StackDirection.row)
                                    sx { justifyContent = JustifyContent.spaceBetween; alignItems = AlignItems.center }

                                    Typography {
                                        variant = TypographyVariant.body2
                                        sx { flex = Flex(number(1.0), number(1.0), 0.px) }
                                        asDynamic().style = StoreStyles.fontMedium
                                        +(item.description ?: "")
                                    }
                                    Typography {
                                        variant = TypographyVariant.body2
                                        sx { width = 50.px; textAlign = TextAlign.center; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                                        +messages.receiptQuantityTimes(item.quantity)
                                    }
                                    Typography {
                                        variant = TypographyVariant.body2
                                        sx {
                                            width = 100.px
                                            textAlign = TextAlign.end
                                            color = StoreColors.PriceColor.unsafeCast<Color>()
                                        }
                                        asDynamic().style = StoreStyles.fontMedium
                                        +"\$${formatPrice(item.value)}"
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
                            sx { color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                            +"${messages.commonTotal()} "
                        }

                        receipt.total?.let { total ->
                            Box {
                                sx {
                                    backgroundColor = StoreColors.PriceBackground.unsafeCast<BackgroundColor>()
                                    borderRadius = 10.px
                                    padding = Padding(6.px, 14.px)
                                }
                                Typography {
                                    variant = TypographyVariant.h6
                                    sx {
                                        fontWeight = FontWeight.bold
                                        fontSize = 22.px
                                        color = StoreColors.PriceColor.unsafeCast<Color>()
                                    }
                                    +"\$${formatPrice(total)}"
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
                        onClick = onOpenProducts
                        asDynamic().style = StoreStyles.fontMedium
                        sx { borderRadius = StoreTokens.Radius.Md.px; height = 48.px; textTransform = None.none }
                        ArrowBack { sx { marginRight = 6.px; fontSize = 18.px } }
                        +messages.commonContinueShopping()
                    }
                }
            }
        }
    }
}
