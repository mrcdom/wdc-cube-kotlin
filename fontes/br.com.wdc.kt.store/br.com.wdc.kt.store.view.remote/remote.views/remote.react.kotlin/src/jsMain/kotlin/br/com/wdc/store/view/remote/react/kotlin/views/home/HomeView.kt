package br.com.wdc.store.view.remote.react.kotlin.views.home

import br.com.wdc.store.i18n.*
import br.com.wdc.store.view.remote.react.kotlin.util.messages
import br.com.wdc.store.view.remote.react.kotlin.util.message
import br.com.wdc.framework.cube.remote.bridge.react.RemoteViewProps
import br.com.wdc.framework.cube.remote.bridge.react.remoteView
import br.com.wdc.framework.cube.remote.bridge.react.useView
import br.com.wdc.store.design.StoreTokens
import br.com.wdc.store.view.remote.react.kotlin.theme.COMPACT_BREAKPOINT
import br.com.wdc.store.view.remote.react.kotlin.views.useBridge
import kotlinx.browser.window
import kotlinx.coroutines.awaitCancellation
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
import react.Key
import react.ReactNode
import react.create
import react.useEffect
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

    useEffect(Unit) {
        val handler: (dynamic) -> Unit = { isCompact = window.innerWidth < COMPACT_BREAKPOINT }
        window.addEventListener("resize", handler)
        try {
            awaitCancellation()
        } finally {
            window.removeEventListener("resize", handler)
        }
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

        val errorMessage = scope.message("errorMessage")
        if (!errorMessage.isNullOrEmpty()) {
            Alert {
                key = Key("error")
                severity = "error"
                sx { marginLeft = StoreTokens.Spacing.Lg.px; marginRight = StoreTokens.Spacing.Lg.px; marginTop = StoreTokens.Spacing.Sm.px }
                +errorMessage
            }
        }

        if (contentViewId != null) {
            // The place below the home (keyed: the error above must not remount it)
            Box {
                key = Key("content")
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
                key = Key("compact")
                sx {
                    flex = Flex(number(1.0), number(1.0), 0.px)
                    padding = StoreTokens.Spacing.Sm.px
                    overflow = Overflow.hidden
                    display = Display.flex
                    flexDirection = FlexDirection.column
                }
                Tabs {
                    value = selectedTab
                    onChange = { _, value -> selectedTab = value as Int }
                    sx { marginBottom = StoreTokens.Spacing.Xs.px; minHeight = 36.px }
                    Tab {
                        label = ReactNode(messages.homeTabProducts())
                        icon = ShoppingBag.create()
                        iconPosition = IconPosition.start
                        sx { minHeight = 36.px; paddingTop = StoreTokens.Spacing.Xs.px; paddingBottom = StoreTokens.Spacing.Xs.px; fontSize = 13.px }
                    }
                    Tab {
                        label = ReactNode(messages.homeTabPurchases())
                        icon = Inventory2.create()
                        iconPosition = IconPosition.start
                        sx { minHeight = 36.px; paddingTop = StoreTokens.Spacing.Xs.px; paddingBottom = StoreTokens.Spacing.Xs.px; fontSize = 13.px }
                    }
                }
                Card {
                    elevation = 0
                    sx {
                        borderRadius = StoreTokens.Radius.Sm.px
                        flex = Flex(number(1.0), number(1.0), 0.px)
                        overflow = if (selectedTab == 0) Auto.auto else Overflow.hidden
                    }
                    remoteView(if (selectedTab == 0) productsPanel else purchasesPanel)
                }
            }
        } else {
            // Wide: the panels side by side
            Box {
                key = Key("wide")
                sx {
                    display = Display.flex
                    flexDirection = FlexDirection.row
                    flex = Flex(number(1.0), number(1.0), 0.px)
                    padding = StoreTokens.Spacing.Lg.px
                    gap = StoreTokens.Spacing.Lg.px
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
                        borderRadius = StoreTokens.Radius.Sm.px
                        overflowY = Auto.auto
                        overflowX = Overflow.hidden
                    }
                    remoteView(productsPanel)
                }
                Card {
                    elevation = 0
                    sx {
                        flex = Flex(number(2.0), number(1.0), 0.px)
                        borderRadius = StoreTokens.Radius.Sm.px
                        overflow = Overflow.hidden
                    }
                    remoteView(purchasesPanel)
                }
            }
        }
    }
}
