package br.com.wdc.framework.cube.remote.javalin

import br.com.wdc.framework.cube.remote.WebSocketConnection
import io.javalin.websocket.WsContext

internal class JavalinWebSocketConnection(private val wsContext: WsContext) : WebSocketConnection {
    @Synchronized
    override fun sendText(text: String) {
        wsContext.send(text)
    }
}
