package br.com.wdc.framework.cube.remote

/**
 * Where a session sends its messages: the client's WebSocket, given by the web server's routes
 * (`framework-cube-remote-host-javalin` on Javalin).
 */
interface WebSocketConnection {
    fun sendText(text: String)
}
