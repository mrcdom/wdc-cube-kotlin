package br.com.wdc.shopping.view.react

import br.com.wdc.framework.commons.codec.Base62
import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.view.react.skeleton.spi.WebSocketConnection
import br.com.wdc.shopping.view.react.skeleton.util.AppSecurity
import br.com.wdc.shopping.view.react.skeleton.viewimpl.ApplicationReactImpl
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

class DispatcherHandler private constructor(private val appId: String) {

    companion object {
        private val LOG = Log.getLogger(DispatcherHandler::class.java)
        private const val CLOSE_SESSION_INVALID = 4001

        private val GSON: Gson = GsonBuilder()
            .serializeNulls()
            .setObjectToNumberStrategy(ToNumberPolicy.DOUBLE)
            .create()

        private val REQUEST_TYPE = object : TypeToken<Map<String, Any?>>() {}

        private val SESSION_SIGNATURES = ConcurrentHashMap<String, String>()
        private val ACTIVE_HANDLERS = ConcurrentHashMap<String, DispatcherHandler>()

        fun getOrCreate(appId: String): DispatcherHandler {
            return ACTIVE_HANDLERS.computeIfAbsent(appId) { DispatcherHandler(it) }
        }

        fun get(appId: String): DispatcherHandler? {
            return ACTIVE_HANDLERS[appId]
        }

        fun registerSessionSignature(appId: String, signature: String) {
            SESSION_SIGNATURES[appId] = signature
            LOG.debug("Registered signature for session: {}", appId)
        }

        fun unregisterSessionSignature(appId: String) {
            SESSION_SIGNATURES.remove(appId)
            ACTIVE_HANDLERS.remove(appId)
            LOG.debug("Unregistered signature for session: {}", appId)
        }
    }

    private var appSignature: String? = null
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

            val security = AppSecurity
            val b62 = Base62

            val appIdParts = appId.split('.')
            if (appIdParts.size != 2) {
                LOG.warn("WebSocket connection rejected: invalid session ID format")
                ctx.closeSession(CLOSE_SESSION_INVALID, "reload_required")
                return
            }

            val appIdPart1 = appIdParts[0]
            val appIdPart2 = appIdParts[1]

            val expectedAppIdPart2 = b62.encodeToString(
                security.signAsHash(appIdPart1.toByteArray())
            )

            if (appIdPart2 != expectedAppIdPart2) {
                LOG.warn("WebSocket connection rejected: invalid session ID signature")
                ctx.closeSession(CLOSE_SESSION_INVALID, "reload_required")
                return
            }

            val signature = ctx.cookie("app_signature")
            if (signature.isNullOrEmpty()) {
                LOG.warn("WebSocket connection rejected: missing app_signature cookie for session: {}", appId)
                ctx.closeSession(CLOSE_SESSION_INVALID, "reload_required")
                return
            }

            this.appSignature = signature
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
                LOG.warn("Failed to parse WebSocket message as JSON", parseError)
                val app = getApp()
                if (app != null) {
                    app.alertUnexpectedError(LOG, parseError.message ?: "", parseError)
                }
                return
            }

            val app = getOrCreateApp(request)
            try {
                app.wsSession = wsSession
                app.sendResponse(request)
            } catch (processingError: Exception) {
                LOG.error("Error processing WebSocket message", processingError)
                app.alertUnexpectedError(LOG, processingError.message ?: "", processingError)
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
                app.wsSession = null
                if (!app.isAuthenticated()) {
                    LOG.debug("Releasing non-authenticated session: {}", appId)
                    app.release()
                }
            }
            LOG.debug("WebSocket connection closed for session: {}", appId)

            ACTIVE_HANDLERS.remove(appId)
            SESSION_SIGNATURES.remove(appId)
        } catch (e: Exception) {
            LOG.warn("Error during WebSocket close", e)
        }
    }

    fun onError(ctx: WsErrorContext) {
        try {
            ctx.disableAutomaticPings()
            val error = ctx.error()
            LOG.warn("WebSocket error for session {}: {}", appId, error?.message, error)

            val app = getApp()
            if (app != null && error != null) {
                app.alertUnexpectedError(LOG, error.message ?: "", error)
            }
        } catch (e: Exception) {
            LOG.error("Error in WebSocket error handler", e)
        }
    }

    private fun getApp(): ApplicationReactImpl? = ApplicationReactImpl.get(appId)

    private fun getOrCreateApp(request: Map<String, Any?>): ApplicationReactImpl {
        var app = ApplicationReactImpl.get(appId)

        if (app == null) {
            val mutableRequest = request.toMutableMap()
            mutableRequest["secret"] = appSignature
            app = ApplicationReactImpl.getOrCreate(appId, mutableRequest)
        }

        app.wsSession = wsSession
        app.extendLife()

        return app
    }
}
