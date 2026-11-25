package br.com.wdc.framework.cube.remote.javalin

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.remote.RemoteApplicationRegistry
import io.javalin.config.JavalinConfig
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsErrorContext
import io.javalin.websocket.WsMessageContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The WebSocket of the remote clients, `ws://…/dispatcher/{id}`: one [DispatcherHandler] per session id, which checks
 * the id's signature on connect and hands each message to the session of [registry].
 */
class DispatcherController(private val registry: RemoteApplicationRegistry) {

    companion object {
        private val LOG = Log.getLogger("DispatcherController")

        /** Registers the WebSocket route on [config], for the sessions of [registry]. */
        fun configure(config: JavalinConfig, registry: RemoteApplicationRegistry) {
            val controller = DispatcherController(registry)
            config.routes.ws("/dispatcher/{id}") { ws ->
                ws.onConnect { ctx -> controller.onConnect(ctx) }
                ws.onMessage { ctx -> controller.onMessage(ctx) }
                ws.onClose { ctx -> controller.onClose(ctx) }
                ws.onError { ctx -> controller.onError(ctx) }
            }
        }
    }

    private val handlers = ConcurrentHashMap<String, DispatcherHandler>()

    private fun handlerOf(sessionId: String): DispatcherHandler =
        handlers.computeIfAbsent(sessionId) { DispatcherHandler(it, registry) { handlers.remove(it) } }

    private fun onConnect(ctx: WsConnectContext) {
        try {
            val sessionId = ctx.pathParam("id")
            handlerOf(sessionId).onConnectOpen(ctx)
            LOG.debug("WebSocket dispatcher connected for session: {}", sessionId)
        } catch (e: Exception) {
            LOG.error("Error during WebSocket connect", e)
            try {
                ctx.closeSession()
            } catch (closeErr: Exception) {
                LOG.warn("Error closing session", closeErr)
            }
        }
    }

    private fun onMessage(ctx: WsMessageContext) {
        try {
            handlerOf(ctx.pathParam("id")).onMessage(ctx)
        } catch (e: Throwable) {
            LOG.error("Error during WebSocket message", e)
        }
    }

    private fun onClose(ctx: WsCloseContext) {
        try {
            handlers[ctx.pathParam("id")]?.onClose(ctx)
        } catch (e: Exception) {
            LOG.warn("Error during WebSocket close", e)
        }
    }

    private fun onError(ctx: WsErrorContext) {
        try {
            handlers[ctx.pathParam("id")]?.onError(ctx)
        } catch (e: Exception) {
            LOG.error("Error in WebSocket error handler", e)
        }
    }
}
