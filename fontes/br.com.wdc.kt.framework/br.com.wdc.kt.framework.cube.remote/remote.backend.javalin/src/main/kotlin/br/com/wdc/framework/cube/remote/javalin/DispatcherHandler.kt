package br.com.wdc.framework.cube.remote.javalin

import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.cube.remote.RemoteAppSecurity
import br.com.wdc.framework.cube.remote.RemoteApplication
import br.com.wdc.framework.cube.remote.RemoteApplicationRegistry
import br.com.wdc.framework.cube.remote.WebSocketConnection
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsErrorContext
import io.javalin.websocket.WsMessageContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * One client session's connection: checks the session id on connect (its second half is the host's signature of the
 * first, [IndexHtmlController]), hands each message to the session's application — made on the first one — and, when
 * the socket closes, releases a session that does not outlive its connection.
 */
internal class DispatcherHandler(
    private val appId: String,
    private val registry: RemoteApplicationRegistry,
    private val onClosed: () -> Unit,
) {

    companion object {
        private val LOG = Log.getLogger("DispatcherHandler")
        private const val CLOSE_SESSION_INVALID = 4001

        private val GSON: Gson = GsonBuilder()
            .serializeNulls()
            .setObjectToNumberStrategy(ToNumberPolicy.DOUBLE)
            .create()

        private val REQUEST_TYPE = object : TypeToken<Map<String, Any?>>() {}
    }

    private var wsSession: WebSocketConnection? = null
    private var activeWsSessionId: String? = null

    fun onConnectOpen(ctx: WsConnectContext) {
        try {
            val sessionId = ctx.pathParam("id")
            if (sessionId.isBlank()) {
                LOG.warn("WebSocket connection rejected: empty session ID")
                ctx.closeSession(CLOSE_SESSION_INVALID, "reload_required")
                return
            }

            if (sessionId != appId) {
                LOG.warn("WebSocket connection rejected: session ID mismatch")
                ctx.closeSession(CLOSE_SESSION_INVALID, "reload_required")
                return
            }

            // the id the page got from the host: a random part and the host's signature of it
            if (!RemoteAppSecurity.isSessionId(appId)) {
                LOG.warn("WebSocket connection rejected: invalid session ID signature")
                ctx.closeSession(CLOSE_SESSION_INVALID, "reload_required")
                return
            }

            ctx.enableAutomaticPings(15, TimeUnit.SECONDS)
            this.activeWsSessionId = ctx.sessionId()
            this.wsSession = JavalinWebSocketConnection(ctx)

            LOG.debug("WebSocket connection established for session: {} (wsId: {})", appId, activeWsSessionId)
        } catch (e: Exception) {
            LOG.error("Error during WebSocket connection open", e)
            try {
                ctx.closeSession()
            } catch (closeError: Exception) {
                LOG.warn("Error closing session after error", closeError)
            }
        }
    }

    fun onMessage(ctx: WsMessageContext) {
        try {
            val jsonRequest = ctx.message()

            val request: Map<String, Any?>
            try {
                request = GSON.fromJson(jsonRequest, REQUEST_TYPE.type)
            } catch (parseError: Exception) {
                val app = getApp()
                if (app != null) {
                    app.alertUnexpectedError(LOG, "Could not read the request", parseError)
                } else {
                    LOG.warn("Failed to parse WebSocket message as JSON", parseError)
                }
                return
            }

            val app = getOrCreateApp(request)
            try {
                app.remote.wsSession = wsSession
                app.remote.sendResponse(request)
            } catch (processingError: Exception) {
                app.alertUnexpectedError(LOG, "Could not process the request", processingError)
            }
        } catch (e: Exception) {
            LOG.error("Unexpected error in WebSocket message handler", e)
        }
    }

    fun onClose(ctx: WsCloseContext) {
        try {
            ctx.disableAutomaticPings()

            val closingWsId = ctx.sessionId()
            if (activeWsSessionId != null && activeWsSessionId != closingWsId) {
                LOG.debug("Ignoring close for superseded WebSocket session: {} (active: {})", closingWsId, activeWsSessionId)
                return
            }

            this.activeWsSessionId = null
            this.wsSession = null

            val app = getApp()
            if (app != null) {
                app.remote.wsSession = null
                if (!app.keepsSessionWithoutConnection()) {
                    LOG.debug("Releasing non-authenticated session: {}", appId)
                    app.release()
                }
            }
            LOG.debug("WebSocket connection closed for session: {}", appId)

            onClosed()
        } catch (e: Exception) {
            LOG.warn("Error during WebSocket close", e)
        }
    }

    fun onError(ctx: WsErrorContext) {
        try {
            ctx.disableAutomaticPings()
            val error = ctx.error()
            val app = getApp()
            if (app != null && error != null) {
                app.alertUnexpectedError(LOG, "Connection error", error)
            } else {
                LOG.warn("WebSocket error for session {}: {}", appId, error?.message, error)
            }
        } catch (e: Exception) {
            LOG.error("Error in WebSocket error handler", e)
        }
    }

    private fun getApp(): RemoteApplication? = registry.get(appId)

    private fun getOrCreateApp(request: Map<String, Any?>): RemoteApplication {
        var app = registry.get(appId)

        if (app == null) {
            app = registry.getOrCreate(appId, request)
        }

        app.remote.wsSession = wsSession
        app.remote.extendLife()

        return app
    }
}
