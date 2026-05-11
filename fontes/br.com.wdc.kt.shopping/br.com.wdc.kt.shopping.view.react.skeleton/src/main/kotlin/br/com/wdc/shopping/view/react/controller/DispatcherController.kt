package br.com.wdc.shopping.view.react.controller

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.getLogger
import br.com.wdc.shopping.view.react.DispatcherHandler
import io.javalin.config.JavalinConfig
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsErrorContext
import io.javalin.websocket.WsMessageContext

class DispatcherController {

    companion object {
        private val LOG = Log.getLogger(DispatcherController::class.java)

        fun configure(config: JavalinConfig) {
            val controller = DispatcherController()
            config.routes.ws("/dispatcher/{id}") { ws ->
                ws.onConnect { ctx -> controller.onConnect(ctx) }
                ws.onMessage { ctx -> controller.onMessage(ctx) }
                ws.onClose { ctx -> controller.onClose(ctx) }
                ws.onError { ctx -> controller.onError(ctx) }
            }
        }
    }

    private fun onConnect(ctx: WsConnectContext) {
        try {
            val sessionId = ctx.pathParam("id")
            val handler = DispatcherHandler.getOrCreate(sessionId)
            handler.onConnectOpen(ctx)
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
            val sessionId = ctx.pathParam("id")
            val handler = DispatcherHandler.getOrCreate(sessionId)
            handler.onMessage(ctx)
        } catch (e: Throwable) {
            LOG.error("Error during WebSocket message", e)
        }
    }

    private fun onClose(ctx: WsCloseContext) {
        try {
            val sessionId = ctx.pathParam("id")
            val handler = DispatcherHandler.get(sessionId)
            handler?.onClose(ctx)
        } catch (e: Exception) {
            LOG.warn("Error during WebSocket close", e)
        }
    }

    private fun onError(ctx: WsErrorContext) {
        try {
            val sessionId = ctx.pathParam("id")
            val handler = DispatcherHandler.get(sessionId)
            handler?.onError(ctx)
        } catch (e: Exception) {
            LOG.error("Error in WebSocket error handler", e)
        }
    }
}
