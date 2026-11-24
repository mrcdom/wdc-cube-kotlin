package br.com.wdc.shopping.view.react.skeleton.viewimpl

import br.com.wdc.framework.commons.codec.Base62
import br.com.wdc.framework.commons.gson.JsonExtensibleObjectOutput
import br.com.wdc.framework.commons.lang.CoerceUtils
import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.storage.JvmSessionStorage
import br.com.wdc.framework.commons.storage.SessionStorage
import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput
import br.com.wdc.framework.cube.CubeApplication
import br.com.wdc.framework.cube.CubeIntent
import br.com.wdc.framework.cube.CubePresenter
import br.com.wdc.framework.cube.PresenterBase
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.view.react.skeleton.spi.WebSocketConnection
import br.com.wdc.shopping.view.react.skeleton.util.AppSecurity
import br.com.wdc.shopping.view.react.skeleton.util.DataSecurity
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl
import br.com.wdc.framework.cube.ScopeViews
import br.com.wdc.shopping.presentation.presenter.RootScope
import br.com.wdc.shopping.presentation.presenter.open.login.LoginScope
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomeScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelScope
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductScope
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptScope
import br.com.wdc.shopping.view.react.skeleton.util.ScopeViewImpl
import br.com.wdc.shopping.view.react.ViewFlushScheduler
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ReactRootPresenter(internal val id: String) : RootPresenter(), PresenterBase {

    override val app: CubeApplication get() = this

    companion object {
        private val LOG = Log.getLogger("ReactRootPresenter")

        val DEFAULT_TIME_SPAN: Duration = 3.minutes

        private val instanceMap = ConcurrentHashMap<String, ReactRootPresenter>()


        fun get(appId: String?): ReactRootPresenter? {
            if (appId.isNullOrBlank()) return null
            return instanceMap[appId]
        }

        fun getActiveSessionCount(): Int = instanceMap.size

        fun getOrCreate(appId: String, request: Map<String, Any?>): ReactRootPresenter {
            return instanceMap.computeIfAbsent(appId) { createApp(appId, request) }
        }

        private fun createApp(appId: String, request: Map<String, Any?>): ReactRootPresenter {
            val app = ReactRootPresenter(appId)
            try {
                app.addReleaseAction { instanceMap.remove(appId) }

                var path = app.fragment
                @Suppress("UNCHECKED_CAST")
                val browserScope = request[BrowserReactViewImpl.VSID] as? Map<String, Any?>
                if (browserScope != null) {
                    val p = browserScope["p.path"] as? String
                    if (!p.isNullOrBlank()) {
                        path = p
                    }
                }

                LOG.debug("[createApp] Before safeGo, path={}, dirtyViewMap.keys={}", path, app.dirtyViewMap.keys)
                runBlocking {
                    app.safeGo(path)
                }
                LOG.debug("[createApp] After safeGo, dirtyViewMap.keys={}, viewMap.keys={}", app.dirtyViewMap.keys, app.viewMap.keys)
            } catch (caught: Exception) {
                LOG.error("[createApp] Exception during safeGo", caught)
                app.release()
                throw caught
            }
            return app
        }

        fun removeExpireds() {
            val now = System.currentTimeMillis()
            val appIterator = instanceMap.values.iterator()
            while (appIterator.hasNext()) {
                val app = appIterator.next()
                if (app.expireMoment < now) {
                    if (app.wsSession == null) {
                        app.release()
                    } else {
                        app.extendLife()
                    }
                }
            }
        }
    }

    override fun createSessionStorage(): SessionStorage = JvmSessionStorage()

    // :: Instance

    private var expireMoment: Long = 0L

    private var dataSecurity: DataSecurity = DataSecurity()
    @Volatile
    var wsSession: WebSocketConnection? = null

    private var removeInstanceAction: (() -> Unit) = {}

    private val dirtyViewMap = ConcurrentHashMap<String, GenericViewImpl>()
    private val viewMap = HashMap<String, GenericViewImpl>()
    private var lastRequestId: Long = 0L
    private var historyDirty: Boolean = false
    internal lateinit var browserView: BrowserReactViewImpl
        private set
    private var instanceIdGen: Int = 1

    init {
        // which client component paints each scope (the vid); the login and the receipt have their own server side
        scopeViews = ScopeViews()
            .register<RootScope> { ScopeViewImpl("f2d345c4a610", this, it) }
            .register<LoginScope> { LoginReactViewImpl(this, it) }
            .register<HomeScope> { ScopeViewImpl("473dbdd7a36a", this, it) }
            .register<ProductScope> { ScopeViewImpl("48b693f67410", this, it) }
            .register<CartScope> { ScopeViewImpl("7eb485e5f843", this, it) }
            .register<ReceiptScope> { ReceiptReactViewImpl(this, it) }
            .register<ProductsPanelScope> { ScopeViewImpl("a1b2c3d4e5f6", this, it) }
            .register<PurchasesPanelScope> { ScopeViewImpl("b3c4d5e6f7a8", this, it) }

        this.dataSecurity = DataSecurity()
        this.browserView = BrowserReactViewImpl(this)
        this.viewMap[browserView.instanceId] = browserView
        this.dirtyViewMap[browserView.instanceId] = browserView
        this.expireMoment = System.currentTimeMillis() + DEFAULT_TIME_SPAN.inWholeMilliseconds
    }

    override fun release() {
        try {
            try {
                this.browserView.release()
                this.removeInstanceAction()
                this.removeInstanceAction = {}
                super.release()
            } finally {
                ViewFlushScheduler.removeDirty(id)
                instanceMap.remove(id)
                LOG.info("Application removed: {}", this.id)
            }
        } catch (caught: Exception) {
            LOG.error("Running removeInstanceAction", caught)
        }
    }

    fun extendLife() {
        this.expireMoment = System.currentTimeMillis() + DEFAULT_TIME_SPAN.inWholeMilliseconds
    }

    fun nextInstanceId(): Int = instanceIdGen++

    fun addReleaseAction(newAction: () -> Unit) {
        val oldAction = this.removeInstanceAction
        this.removeInstanceAction = {
            newAction()
            oldAction()
        }
    }

    fun isAuthenticated(): Boolean = subject != null

    fun getDataSecurity(): DataSecurity = dataSecurity

    override fun updateHistory() {
        this.historyDirty = true
    }

    fun doUpdateHistory() {
        if (this.historyDirty) {
            val security = AppSecurity
            val intent = CubeIntent()
            intent.place = getLastPlace() ?: getRootPlace()
            publishParameters(intent)

            val b62 = Base62
            val signature = b62.encodeToString(security.signAsHash(intent.toString().toByteArray(StandardCharsets.UTF_8)))
            intent.setParameter("sign", signature)

            fragment = intent.toString()
            this.historyDirty = false
        }
    }

    @Throws(Exception::class)
    suspend fun safeGo(path: String?) {
        val security = AppSecurity
        val intent = CubeIntent.parse(path ?: "")
        if (intent.place == null) {
            intent.place = getRootPlace()
        }

        val b62 = Base62

        val actualSignature = intent.removeParameter("sign") ?: ""
        val expectedSignature = b62.encodeToString(
            security.signAsHash(intent.toString().toByteArray(StandardCharsets.UTF_8))
        )

        if (actualSignature != expectedSignature) {
            updateHistory()
            val newInt = newIntent()
            if (newInt.place == null) {
                newInt.place = getRootPlace()
            }
            flip(newInt)
        } else {
            flip(intent)
        }
    }

    fun putView(view: GenericViewImpl) {
        viewMap[view.instanceId] = view
    }

    fun removeView(scopeId: String): GenericViewImpl? {
        dirtyViewMap.remove(scopeId)
        return viewMap.remove(scopeId)
    }

    fun markDirty(view: GenericViewImpl) {
        dirtyViewMap[view.instanceId] = view
        ViewFlushScheduler.markDirty(this)
    }

    fun updateAllViews() {
        viewMap.forEach { (_, v) -> markDirty(v) }
    }

    /** The browser's alert shows the error; the stack trace goes to the server log, never to the client. */
    override fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        val text = errorText(message, e)
        logger.error(text, e)
        browserView.alertUnexpectedError(text)
    }

    @Synchronized
    @Throws(Exception::class)
    fun sendResponse(request: Map<String, Any?>) {
        try {
            runBlocking {
                DispatchPhaseBhv(this@ReactRootPresenter).run(request)
            }
            ResponsePhaseBhv(this).run(request)
        } catch (e: Exception) {
            val exn = IOException("Sending response")
            exn.addSuppressed(e)
            throw exn
        }
    }

    // :: Flush

    /**
     * Writes the scopes of [views] and of every view created while writing them (a scope written for the first time
     * creates the views of the scopes inside it), so the client gets a parent and its children in the same message.
     * The presenters compute their derived fields first, once per paint cycle.
     */
    private fun writeScopes(json: ExtensibleObjectOutput, views: List<GenericViewImpl>) {
        try {
            commitComputedState()
        } catch (e: Exception) {
            LOG.error("commitComputedState", e)
        }
        val written = HashSet<String>()
        var pending = views
        while (pending.isNotEmpty()) {
            for (view in pending) {
                if (written.add(view.instanceId)) view.writeScope(json)
            }
            pending = dirtyViewMap.values.filter { it.instanceId !in written }
            pending.forEach { dirtyViewMap.remove(it.instanceId) }
        }
    }

    @Synchronized
    fun flushDirtyViews() {
        if (dirtyViewMap.isEmpty()) return
        val ws = wsSession ?: run {
            LOG.debug("[flushDirtyViews] wsSession is null, skipping. dirtyViewMap.keys={}", dirtyViewMap.keys)
            return
        }

        LOG.debug("[flushDirtyViews] Flushing dirtyViewMap.keys={}", dirtyViewMap.keys)
        val allViews = ArrayList<GenericViewImpl>()
        val iter = dirtyViewMap.entries.iterator()
        while (iter.hasNext()) {
            allViews.add(iter.next().value)
            iter.remove()
        }

        if (allViews.isEmpty()) return

        val strWriter = StringWriter()
        val json = JsonExtensibleObjectOutput(JsonWriter(strWriter))
        try {
            json.beginObject()
            json.name("requestId").value(lastRequestId)

            if (historyDirty) {
                doUpdateHistory()
            }

            if (fragment != null) {
                json.name("uri").value(fragment)
            }

            json.name("scopes")
            json.beginArray()
            writeScopes(json, allViews)
            json.endArray()
            json.endObject()
        } finally {
            json.flush()
        }

        ws.sendText(strWriter.toString())
    }

    internal fun sendTextToClient(text: String) {
        val ws = wsSession ?: throw AssertionError("Missing WebSocket Session")
        ws.sendText(text)
    }

    private class DispatchPhaseBhv(private val me: ReactRootPresenter) {

        @Throws(Exception::class)
        suspend fun run(request: Map<String, Any?>): Boolean {
            updateSecret(request)
            updateApplicationState(request)

            @Suppress("UNCHECKED_CAST")
            val eventList = request["event"] as? List<String>
            if (eventList.isNullOrEmpty()) {
                return false
            }

            var viewNotFound = false
            for (eventEntry in computeEventMap(eventList)) {
                viewNotFound = submitEvent(request, viewNotFound, eventEntry.key, eventEntry.value)
            }

            if (viewNotFound) {
                me.updateAllViews()
            }

            return true
        }

        @Throws(Exception::class)
        private suspend fun submitEvent(
            request: Map<String, Any?>,
            viewNotFoundInput: Boolean,
            rawEvent: String,
            eventQtde: Int
        ): Boolean {
            var viewNotFound = viewNotFoundInput
            val pos = rawEvent.lastIndexOf(':')
            if (pos != -1) {
                val viewId = rawEvent.substring(0, pos)
                val view = me.viewMap[viewId]
                if (view != null) {
                    try {
                        val eventCode = rawEvent.substring(pos + 1).toInt()

                        @Suppress("UNCHECKED_CAST")
                        val formData = (request[viewId] as? Map<String, Any?>) ?: emptyMap()

                        view.submit(eventCode, eventQtde, formData)
                    } catch (e: Throwable) {
                        me.alertUnexpectedError(LOG, "Unexpected error", e)
                        view.update()
                    }
                } else {
                    viewNotFound = true
                }
            }
            return viewNotFound
        }

        private fun updateApplicationState(request: Map<String, Any?>) {
            for ((key, value) in request) {
                val view = me.viewMap[key]
                if (view != null) {
                    @Suppress("UNCHECKED_CAST")
                    val formData = value as? Map<String, Any?> ?: continue
                    view.syncClientToServer(formData)
                }
            }
        }

        private fun updateSecret(request: Map<String, Any?>) {
            val signature = CoerceUtils.asString(request["secret"])
            if (!signature.isNullOrBlank()) {
                me.getDataSecurity().updateSecret(signature)
            }
        }

        private fun computeEventMap(eventList: List<String>): Map<String, Int> {
            val eventMap = HashMap<String, Int>(eventList.size)
            eventList.forEach { eventId ->
                eventMap[eventId] = (eventMap[eventId] ?: 0) + 1
            }
            return eventMap
        }
    }

    private class ResponsePhaseBhv(private val me: ReactRootPresenter) {

        fun run(request: Map<String, Any?>): Boolean {
            val requestId = getRequestId(request).also { me.lastRequestId = it }
            val isPing = isPing(request)

            me.doUpdateHistory()

            LOG.debug("[ResponsePhase] reqId={} dirtyViewMap.keys={} viewMap.keys={}", requestId, me.dirtyViewMap.keys, me.viewMap.keys)

            val viewsToFlush = ArrayList<GenericViewImpl>()
            val iter = me.dirtyViewMap.entries.iterator()
            while (iter.hasNext()) {
                viewsToFlush.add(iter.next().value)
                iter.remove()
            }

            LOG.debug("[ResponsePhase] viewsToFlush={}", viewsToFlush.map { it.instanceId })

            if (!isPing && !me.historyDirty && viewsToFlush.isEmpty()) {
                return false
            }

            val strWriter = StringWriter()
            val json = JsonExtensibleObjectOutput(JsonWriter(strWriter))
            try {
                writeResponse(json, requestId, isPing, viewsToFlush)
            } finally {
                json.flush()
            }

            val jsonResponse = strWriter.toString()
            me.sendTextToClient(jsonResponse)

            return true
        }

        private fun getRequestId(request: Map<String, Any?>): Long {
            return CoerceUtils.asLong(request["requestId"], me.lastRequestId) ?: me.lastRequestId
        }

        private fun isPing(request: Map<String, Any?>): Boolean {
            return CoerceUtils.asBoolean(request["ping"], false) == true
        }

        private fun writeResponse(json: ExtensibleObjectOutput, requestId: Long, isPing: Boolean, views: List<GenericViewImpl>) {
            json.beginObject()
            run {
                json.name("requestId").value(requestId)

                if (isPing) {
                    json.name("ping").value(true)
                }

                if (me.fragment != null) {
                    json.name("uri").value(me.fragment)
                }

                if (views.isNotEmpty()) {
                    json.name("scopes")
                    json.beginArray()
                    me.writeScopes(json, views)
                    json.endArray()
                }
            }
            json.endObject()
        }
    }
}
