package br.com.wdc.shopping.nativeui.web.views

import br.com.wdc.shopping.nativeui.web.bridge.ReactCubeView
import br.com.wdc.shopping.nativeui.web.theme.ShoppingColors
import br.com.wdc.shopping.nativeui.web.theme.ShoppingStyles
import br.com.wdc.shopping.nativeui.web.util.formatDate
import br.com.wdc.shopping.nativeui.web.util.formatPrice
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import mui.icons.material.ChevronLeft
import mui.icons.material.ChevronRight
import mui.icons.material.Inventory2
import mui.material.Box
import mui.material.Card
import mui.material.CardActionArea
import mui.material.CardContent
import mui.material.Chip
import mui.material.ChipColor
import mui.material.Divider
import mui.material.IconButton
import mui.material.Stack
import mui.material.StackDirection
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.FC
import react.Props
import react.ReactNode
import react.dom.html.ReactHTML.div
import react.useEffect
import react.useRef
import react.useState
import web.cssom.*
import web.html.HTMLDivElement

private const val ITEM_HEIGHT_PX = 76

class PurchasesPanelView(private val presenter: PurchasesPanelPresenter) : ReactCubeView("purchases-panel-view", presenter.app) {

    override val component = FC<Props> {
        var rev by useState(revision)
        useEffect(this@PurchasesPanelView) {
            onUpdate = { rev = revision }
        }

        @Suppress("UNUSED_VARIABLE")
        val unused = rev

        val state = presenter.state
        val containerRef = useRef<HTMLDivElement>()
        val lastCapacityRef = useRef(-1)

        // Measure container and report capacity via ResizeObserver
        useEffect(containerRef.current) {
            val el = containerRef.current ?: return@useEffect

            el.asDynamic().__resizeCallback = fun() {
                val h = el.clientHeight
                val capacity = (h / ITEM_HEIGHT_PX).coerceAtLeast(1)
                if (capacity != (lastCapacityRef.current ?: -1)) {
                    lastCapacityRef.current = capacity
                    safeCall { presenter.onItemSizeCapacityChanged(capacity) }
                }
            }

            // Initial calculation
            el.asDynamic().__resizeCallback()

            // Observe resize for dynamic recalculation
            val observer = js("new ResizeObserver(function(entries) { entries[0].target.__resizeCallback(); })")
            observer.observe(el)
        }

        Box {
            sx { padding = 12.px; display = Display.flex; flexDirection = FlexDirection.column; height = 100.pct }

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
                    +"Compras"
                }

                if (state.totalCount > 0) {
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
                            +"${state.totalCount} itens"
                        }
                    }
                }
            }

            Divider { sx { marginBottom = 12.px } }

            // Purchase list
            div {
                ref = containerRef
                style = js("({flex: '1 1 auto', overflow: 'hidden'})").unsafeCast<react.CSSProperties>()

                val purchases = state.purchases
                if (purchases.isEmpty()) {
                    if ((lastCapacityRef.current ?: -1) >= 1 && state.totalCount == 0) {
                        Box {
                            sx {
                                display = Display.flex
                                flexDirection = FlexDirection.column
                                alignItems = AlignItems.center
                                justifyContent = JustifyContent.center
                                padding = 48.px
                            }
                            Inventory2 { sx { fontSize = 48.px; color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() } }
                            Typography {
                                variant = TypographyVariant.body1
                                sx { marginTop = 8.px; color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                                +"Nenhuma compra realizada"
                            }
                        }
                    }
                } else {
                    Stack {
                        direction = responsive(StackDirection.column)
                        spacing = responsive(1)

                        for (purchase in purchases) {
                            Card {
                                key = "${purchase.id}"
                                elevation = 0
                                sx {
                                    borderRadius = 8.px
                                    backgroundColor = ShoppingColors.SurfaceVariant50.unsafeCast<BackgroundColor>()
                                }

                                CardActionArea {
                                    onClick = { safeCall { presenter.onOpenReceipt(purchase.id) } }

                                    CardContent {
                                        sx { padding = 14.px }

                                        Stack {
                                            direction = responsive(StackDirection.row)
                                            sx {
                                                justifyContent = JustifyContent.spaceBetween
                                                alignItems = AlignItems.center
                                            }

                                            Box {
                                                sx {
                                                    flex = Flex(number(1.0), number(1.0), 0.px)
                                                    minWidth = 0.px
                                                    overflow = "hidden".unsafeCast<Overflow>()
                                                }

                                                Typography {
                                                    variant = TypographyVariant.caption
                                                    sx { color = ShoppingColors.OnSurfaceVariant.unsafeCast<Color>() }
                                                    +formatDate(purchase.date)
                                                }
                                                Typography {
                                                    variant = TypographyVariant.body2
                                                    asDynamic().noWrap = true
                                                    asDynamic().style = ShoppingStyles.fontNormal
                                                    +purchase.items.joinToString(", ")
                                                }
                                            }

                                            Chip {
                                                label = ReactNode("R$ ${formatPrice(purchase.total)}")
                                                sx {
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

            // Pagination
            if (state.totalCount > state.pageSize && state.pageSize > 0) {
                val totalPages = (state.totalCount + state.pageSize - 1) / state.pageSize

                Stack {
                    direction = responsive(StackDirection.row)
                    sx {
                        justifyContent = JustifyContent.center
                        alignItems = AlignItems.center
                        marginTop = 8.px
                    }
                    spacing = responsive(2)

                    IconButton {
                        disabled = state.page <= 0
                        onClick = { safeCall { presenter.onPageChange(state.page - 1) } }
                        ChevronLeft {}
                    }

                    Typography {
                        variant = TypographyVariant.body2
                        sx { fontWeight = FontWeight.bold }
                        +"${state.page + 1} / $totalPages"
                    }

                    IconButton {
                        disabled = state.page >= totalPages - 1
                        onClick = { safeCall { presenter.onPageChange(state.page + 1) } }
                        ChevronRight {}
                    }
                }
            }
        }
    }
}
