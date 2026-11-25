package br.com.wdc.store.view.remote.react.kotlin.views.home

import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewProps
import br.com.wdc.framework.cube.remote.bridge.react.useView
import br.com.wdc.framework.cube.remote.bridge.react.remoteView
import br.com.wdc.store.view.remote.react.kotlin.theme.COMPACT_BREAKPOINT
import br.com.wdc.store.view.remote.react.kotlin.views.useBridge
import kotlinx.browser.window
import mui.icons.material.Inventory2
import mui.icons.material.ShoppingBag
import mui.material.Alert
import mui.material.Box
import mui.material.Card
import mui.material.IconPosition
import mui.material.Tab
import mui.material.Tabs
import mui.system.sx
import react.FC
import react.ReactNode
import react.create
import react.useEffectWithCleanup
import react.useState
import web.cssom.*

/**
 * The home: the header, then either the place below it (product, cart, receipt) or the two panels — side by side, or
 * in tabs on a narrow window.
 */
val HomeView = FC<RemoteViewProps> { props ->
    val scope = useBridge().useView(props.vsid)

    var selectedTab by useState(0)
    var isCompact by useState(window.innerWidth < COMPACT_BREAKPOINT)

    useEffectWithCleanup(Unit) {
        val handler: (dynamic) -> Unit = { isCompact = window.innerWidth < COMPACT_BREAKPOINT }
        window.addEventListener("resize", handler)
        onCleanup { window.removeEventListener("resize", handler) }
    }

    val productsPanel = scope.viewId("productsPanel")
    val purchasesPanel = scope.viewId("purchasesPanel")
    val contentViewId = scope.viewId("content")

    Box {
        sx {
            display = Display.flex
            flexDirection = FlexDirection.column
            height = 100.vh
            overflow = Overflow.hidden
        }

        HeaderPanel {
            vsid = props.vsid
            nickName = scope.string("nickName") ?: ""
            cartItemCount = scope.int("cartItemCount") ?: 0
            this.isCompact = isCompact
        }

        val errorMessage = scope.string("errorMessage")
        if (!errorMessage.isNullOrEmpty()) {
            Alert {
                key = "error"
                severity = "error"
                sx { marginLeft = 16.px; marginRight = 16.px; marginTop = 8.px }
                +errorMessage
            }
        }

        if (contentViewId != null) {
            // The place below the home (keyed: the error above must not remount it)
            Box {
                key = "content"
                sx {
                    flex = Flex(number(1.0), number(1.0), 0.px)
                    overflowY = Auto.auto
                    overflowX = Overflow.hidden
                }
                Box {
                    sx {
                        maxWidth = 560.px
                        marginLeft = Auto.auto
                        marginRight = Auto.auto
                    }
                    remoteView(contentViewId)
                }
            }
        } else if (isCompact) {
            // Compact: the panels in tabs
            Box {
                key = "compact"
                sx {
                    flex = Flex(number(1.0), number(1.0), 0.px)
                    padding = 8.px
                    overflow = Overflow.hidden
                    display = Display.flex
                    flexDirection = FlexDirection.column
                }
                Tabs {
                    value = selectedTab
                    onChange = { _, value -> selectedTab = value as Int }
                    sx { marginBottom = 4.px; minHeight = 36.px }
                    Tab {
                        label = ReactNode("Products")
                        icon = ShoppingBag.create()
                        iconPosition = IconPosition.start
                        sx { minHeight = 36.px; paddingTop = 4.px; paddingBottom = 4.px; fontSize = 13.px }
                    }
                    Tab {
                        label = ReactNode("Purchases")
                        icon = Inventory2.create()
                        iconPosition = IconPosition.start
                        sx { minHeight = 36.px; paddingTop = 4.px; paddingBottom = 4.px; fontSize = 13.px }
                    }
                }
                Card {
                    elevation = 0
                    sx {
                        borderRadius = 8.px
                        flex = Flex(number(1.0), number(1.0), 0.px)
                        overflow = if (selectedTab == 0) Auto.auto else Overflow.hidden
                    }
                    remoteView(if (selectedTab == 0) productsPanel else purchasesPanel)
                }
            }
        } else {
            // Wide: the panels side by side
            Box {
                key = "wide"
                sx {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    flex = Flex(number(1.0), number(1.0), 0.px)
                    padding = 16.px
                    gap = 16.px
                    overflow = Overflow.hidden
                    maxWidth = 1200.px
                    marginLeft = Auto.auto
                    marginRight = Auto.auto
                    width = 100.pct
                }
                Card {
                    elevation = 0
                    sx {
                        flex = Flex(number(3.0), number(1.0), 0.px)
                        borderRadius = 8.px
                        overflowY = Auto.auto
                        overflowX = Overflow.hidden
                    }
                    remoteView(productsPanel)
                }
                Card {
                    elevation = 0
                    sx {
                        flex = Flex(number(2.0), number(1.0), 0.px)
                        borderRadius = 8.px
                        overflow = Overflow.hidden
                    }
                    remoteView(purchasesPanel)
                }
            }
        }
    }
}
