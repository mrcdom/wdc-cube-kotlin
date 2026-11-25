package br.com.wdc.framework.cube.remote

/** Where a session sends its messages: the client's WebSocket (Javalin's in [javalin.JavalinWebSocketConnection]). */
interface WebSocketConnection {
    fun sendText(text: String)
}
