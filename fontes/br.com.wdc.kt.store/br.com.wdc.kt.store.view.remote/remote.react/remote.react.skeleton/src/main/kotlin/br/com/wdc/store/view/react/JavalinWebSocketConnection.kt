package br.com.wdc.store.view.react

import br.com.wdc.store.view.react.skeleton.spi.WebSocketConnection
import io.javalin.websocket.WsContext

internal class JavalinWebSocketConnection(private val wsContext: WsContext) : WebSocketConnection {
    @Synchronized
    override fun sendText(text: String) {
        wsContext.send(text)
    }
}
