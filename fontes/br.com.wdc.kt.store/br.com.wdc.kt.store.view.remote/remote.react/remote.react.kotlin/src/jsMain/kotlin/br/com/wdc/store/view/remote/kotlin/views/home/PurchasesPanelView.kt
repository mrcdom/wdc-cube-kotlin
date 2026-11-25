package br.com.wdc.store.view.remote.kotlin.views.home

import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewProps
import br.com.wdc.framework.cube.remote.bridge.react.useView
import br.com.wdc.store.view.remote.kotlin.theme.StoreColors
import br.com.wdc.store.view.remote.kotlin.util.formatDate
import br.com.wdc.store.view.remote.kotlin.util.formatPrice
import br.com.wdc.store.view.remote.kotlin.views.useBridge
import kotlinx.browser.window
import mui.icons.material.ChevronLeft
import mui.icons.material.ChevronRight
import mui.icons.material.Inventory2
import mui.material.Box
import mui.material.Card
import mui.material.CardActionArea
import mui.material.CardContent
import mui.material.Chip
import mui.material.Divider
import mui.material.IconButton
import mui.material.Size
import mui.material.Stack
import mui.material.StackDirection
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.responsive
import mui.system.sx
import react.FC
import react.ReactNode
import react.dom.html.ReactHTML.div
import react.useEffectOnceWithCleanup
import react.useRef
import web.cssom.*
import web.html.HTMLDivElement
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// :: Events of the purchases panel
private const val ON_OPEN_RECEIPT = 1
private const val ON_PAGE_CHANGE = 2
private const val ON_PAGE_SIZE_CHANGE = 3

/** The height of a row before the first one can be measured. */
private const val ITEM_HEIGHT_PX = 76.0

/**
 * The home's purchases panel: one page of the user's purchases. It tells the server how many rows fit (`p.capacity`,
 * a silent event), on opening and when the window is resized; the server answers with pages of that size.
 */
val PurchasesPanelView = FC<RemoteViewProps> { props ->
    val bridge = useBridge()
    val scope = bridge.useView(props.vsid)
    val listRef = useRef<HTMLDivElement>(null)

    useEffectOnceWithCleanup {
        val computePageSize = {
            val el = listRef.current
            val containerHeight = el?.clientHeight ?: 0
            if (el != null && containerHeight > 0) {
                var itemHeight = ITEM_HEIGHT_PX
                val first = el.asDynamic().querySelector("[data-purchase-item]")
                if (first != null && (first.offsetHeight as Int) > 0) {
                    val style = window.getComputedStyle(first)
                    itemHeight = (first.offsetHeight as Int) +
                        (style.marginTop.removeSuffix("px").toDoubleOrNull() ?: 0.0) +
                        (style.marginBottom.removeSuffix("px").toDoubleOrNull() ?: 0.0)
                }
                val capacity = max(1, floor(containerHeight / itemHeight).toInt())
                bridge.setFormField(props.vsid, "p.capacity", capacity)
                bridge.submitSilent(props.vsid, ON_PAGE_SIZE_CHANGE)
            }
        }
        var resizeTimer = 0
        val onResize: (dynamic) -> Unit = {
            window.clearTimeout(resizeTimer)
            resizeTimer = window.setTimeout({ computePageSize() }, 150)
        }
        val frame = window.requestAnimationFrame { computePageSize() }
        window.addEventListener("resize", onResize)
        onCleanup {
            window.cancelAnimationFrame(frame)
            window.clearTimeout(resizeTimer)
            window.removeEventListener("resize", onResize)
        }
    }

    val purchases = scope.objects("purchases")
    val page = scope.int("page") ?: 0
    val pageSize = scope.int("pageSize") ?: -1
    val totalCount = scope.int("totalCount") ?: 0
    val totalPages = max(1, ceil(totalCount.toDouble() / max(1, pageSize)).toInt())

    val changePage = { newPage: Int ->
        bridge.setFormField(props.vsid, "p.page", newPage)
        bridge.submit(props.vsid, ON_PAGE_CHANGE)
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
                +"Purchases"
            }
            if (totalCount > 0) {
                Box {
                    sx {
                        backgroundColor = StoreColors.SecondaryContainer.unsafeCast<BackgroundColor>()
                        borderRadius = 8.px
                        padding = Padding(6.px, 16.px)
                    }
                    Typography {
                        variant = TypographyVariant.caption
                        sx { color = StoreColors.OnPrimaryContainer.unsafeCast<Color>() }
                        +"$totalCount ${if (totalCount == 1) "item" else "items"}"
                    }
                }
            }
        }

        Divider { sx { marginBottom = 12.px } }

        // Purchase list: its height decides the page size
        div {
            ref = listRef
            style = js("({flex: '1 1 auto', overflow: 'hidden'})").unsafeCast<react.CSSProperties>()

            if (purchases.isEmpty()) {
                if (totalCount == 0) {
                    Box {
                        sx {
                            display = Display.flex
                            flexDirection = FlexDirection.column
                            alignItems = AlignItems.center
                            justifyContent = JustifyContent.center
                            padding = 48.px
                        }
                        Inventory2 { sx { fontSize = 48.px; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() } }
                        Typography {
                            variant = TypographyVariant.body1
                            sx { marginTop = 8.px; color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                            +"No purchases yet"
                        }
                    }
                }
            } else {
                Stack {
                    direction = responsive(StackDirection.column)
                    spacing = responsive(1)
                    for (purchase in purchases) {
                        val id = purchase["id"]
                        Card {
                            key = "$id"
                            elevation = 0
                            asDynamic()["data-purchase-item"] = true
                            sx {
                                borderRadius = 8.px
                                backgroundColor = StoreColors.SurfaceVariant50.unsafeCast<BackgroundColor>()
                            }
                            CardActionArea {
                                onClick = {
                                    bridge.setFormField(props.vsid, "p.purchaseId", id)
                                    bridge.submit(props.vsid, ON_OPEN_RECEIPT)
                                }
                                CardContent {
                                    sx { asDynamic().padding = "14px !important" }
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
                                                overflow = Overflow.hidden
                                            }
                                            Typography {
                                                variant = TypographyVariant.caption
                                                sx { color = StoreColors.OnSurfaceVariant.unsafeCast<Color>() }
                                                +formatDate(purchase.double("date"))
                                            }
                                            Typography {
                                                variant = TypographyVariant.body2
                                                noWrap = true
                                                sx { fontWeight = FontWeight.normal }
                                                +purchase.strings("items").joinToString(", ")
                                            }
                                        }
                                        Chip {
                                            label = ReactNode("\$${formatPrice(purchase.double("total"))}")
                                            size = Size.small
                                            sx {
                                                backgroundColor = StoreColors.PriceBackground.unsafeCast<BackgroundColor>()
                                                color = StoreColors.PriceColor.unsafeCast<Color>()
                                                fontWeight = FontWeight.bold
                                                borderRadius = 8.px
                                                marginLeft = 8.px
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

        // Pagination
        if (totalCount > pageSize && pageSize > 0) {
            Stack {
                direction = responsive(StackDirection.row)
                spacing = responsive(2)
                sx { justifyContent = JustifyContent.center; alignItems = AlignItems.center; marginTop = 8.px }
                IconButton {
                    disabled = page <= 0
                    onClick = { changePage(page - 1) }
                    ChevronLeft()
                }
                Typography {
                    variant = TypographyVariant.body2
                    sx { fontWeight = FontWeight.bold }
                    +"${page + 1} / $totalPages"
                }
                IconButton {
                    disabled = page >= totalPages - 1
                    onClick = { changePage(page + 1) }
                    ChevronRight()
                }
            }
        }
    }
}
