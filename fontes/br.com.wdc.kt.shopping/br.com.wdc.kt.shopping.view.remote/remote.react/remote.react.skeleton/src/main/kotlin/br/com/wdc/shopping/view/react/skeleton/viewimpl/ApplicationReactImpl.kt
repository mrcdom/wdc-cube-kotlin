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
import br.com.wdc.shopping.domain.repositories.ProductRepository
import br.com.wdc.shopping.domain.repositories.PurchaseItemRepository
import br.com.wdc.shopping.domain.repositories.PurchaseRepository
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.presentation.repository.SecuredProductRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseItemRepository
import br.com.wdc.shopping.presentation.repository.SecuredPurchaseRepository
import br.com.wdc.shopping.presentation.repository.SecuredUserRepository
import br.com.wdc.shopping.presentation.ShoppingApplication
import br.com.wdc.shopping.presentation.presenter.RootPresenter
import br.com.wdc.shopping.presentation.presenter.open.login.LoginPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.cart.CartPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.HomePresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.products.ProductsPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.home.purchases.PurchasesPanelPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.products.ProductPresenter
import br.com.wdc.shopping.presentation.presenter.restricted.receipt.ReceiptPresenter
import br.com.wdc.shopping.view.react.skeleton.spi.WebSocketConnection
import br.com.wdc.shopping.view.react.skeleton.util.AppSecurity
import br.com.wdc.shopping.view.react.skeleton.util.DataSecurity
import br.com.wdc.shopping.view.react.skeleton.util.GenericViewImpl
import br.com.wdc.shopping.view.react.ViewFlushScheduler
import com.google.gson.stream.JsonWriter
import java.io.IOException
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class ApplicationReactImpl(internal val id: String) : ShoppingApplication(), PresenterBase {

    override val app: CubeApplication get() = this

    override fun commitComputedState() {
        browserView.commitComputedState()
    }

    companion object {
        private val LOG = Log.getLogger("ApplicationReactImpl")

        val DEFAULT_TIME_SPAN: Duration = 3.minutes

        private val instanceMap = ConcurrentHashMap<String, ApplicationReactImpl>()

        init {
            RootPresenter.createView = ::RootReactViewImpl
            LoginPresenter.createView = ::LoginReactViewImpl
            HomePresenter.createView = ::HomeReactViewImpl
            ProductPresenter.createView = ::ProductReactViewImpl
            CartPresenter.createView = ::CartReactViewImpl
            ReceiptPresenter.createView = ::ReceiptReactViewImpl
            ProductsPanelPresenter.createView = ::ProductsPanelReactViewImpl
            PurchasesPanelPresenter.createView = ::PurchasesPanelReactViewImpl
        }

        fun get(appId: String?): ApplicationReactImpl? {
            if (appId.isNullOrBlank()) return null
            return instanceMap[appId]
        }

        fun getOrCreate(appId: String, request: Map<String, Any?>): ApplicationReactImpl {
            return instanceMap.computeIfAbsent(appId) { createApp(appId, request) }
        }

        private fun createApp(appId: String, request: Map<String, Any?>): ApplicationReactImpl {
            val app = ApplicationReactImpl(appId)
            try {
                app.addReleaseAction { instanceMap.remove(appId) }

                var path = app.fragment
                @Suppress("UNCHECKED_CAST")
                val browserViewState = request[BrowserReactViewImpl.VSID] as? Map<String, Any?>
                if (browserViewState != null) {
                    val p = browserViewState["p.path"] as? String
                    if (!p.isNullOrBlank()) {
                        path = p
                    }
                }

                runBlocking {
                    app.safeGo(path)
                }
            } catch (caught: Exception) {
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

    override fun createUserDelegate(delegate: UserRepository) =
        SecuredUserRepository(delegate) { getSecurityContext() }

    override fun createProductDelegate(delegate: ProductRepository) =
        SecuredProductRepository(delegate) { getSecurityContext() }

    override fun createPurchaseDelegate(delegate: PurchaseRepository) =
        SecuredPurchaseRepository(delegate) { getSecurityContext() }

    override fun createPurchaseItemDelegate(delegate: PurchaseItemRepository) =
        SecuredPurchaseItemRepository(delegate) { getSecurityContext() }

    override fun createSessionStorage(): SessionStorage = JvmSessionStorage()

    // :: Instance

    private var expireMoment: Long = 0L

    private var dataSecurity: DataSecurity = DataSecurity()
    @Volatile
    var wsSession: WebSocketConnection? = null

    private var removeInstanceAction: (() -> Unit) = {}

    private var rootPresenterField: RootPresenter? = null
    private val dirtyViewMap = ConcurrentHashMap<String, GenericViewImpl<*>>()
    private val viewMap = HashMap<String, GenericViewImpl<*>>()
    private var lastRequestId: Long = 0L
    private var historyDirty: Boolean = false
    private lateinit var browserView: BrowserReactViewImpl
    private var instanceIdGen: Int = 1

    init {
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

    override fun getRootPresenter(): RootPresenter? = rootPresenterField

    fun setRootPresenter(presenter: RootPresenter?) {
        this.rootPresenterField = presenter
    }

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
            go(newInt)
        } else {
            go(intent)
        }
    }

    fun putView(view: GenericViewImpl<*>) {
        viewMap[view.instanceId] = view
    }

    fun removeView(stateId: String): GenericViewImpl<*>? {
        dirtyViewMap.remove(stateId)
        return viewMap.remove(stateId)
    }

    fun markDirty(view: GenericViewImpl<*>) {
        dirtyViewMap[view.instanceId] = view
        ViewFlushScheduler.markDirty(this)
    }

    fun updateAllViews() {
        viewMap.forEach { (_, v) -> markDirty(v) }
    }

    override fun alertUnexpectedError(logger: Log, message: String, e: Throwable) {
        browserView.alertUnexpectedError(message, e)
    }

    @Synchronized
    @Throws(Exception::class)
    fun sendResponse(request: Map<String, Any?>) {
        try {
            runBlocking {
                DispatchPhaseBhv(this@ApplicationReactImpl).run(request)
            }
            ResponsePhaseBhv(this).run(request)
        } catch (e: Exception) {
            val exn = IOException("Sending response")
            exn.addSuppressed(e)
            throw exn
        }
    }

    // :: Flush

    fun flushDirtyViews() {
        if (dirtyViewMap.isEmpty()) return
        val ws = wsSession ?: return

        val allViews = ArrayList<GenericViewImpl<*>>()
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

            json.name("states")
            json.beginArray()
            for (view in allViews) {
                view.writeState(json)
            }
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

    private class DispatchPhaseBhv(private val me: ApplicationReactImpl) {

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
                        me.alertUnexpectedError(LOG, e.message ?: "", e)
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

    private class ResponsePhaseBhv(private val me: ApplicationReactImpl) {

        fun run(request: Map<String, Any?>): Boolean {
            val requestId = getRequestId(request).also { me.lastRequestId = it }
            val isPing = isPing(request)

            me.doUpdateHistory()

            val viewsToFlush = ArrayList<GenericViewImpl<*>>()
            val iter = me.dirtyViewMap.entries.iterator()
            while (iter.hasNext()) {
                var view = iter.next().value
                viewsToFlush.add(view)

                // commitComputedState per view (synchronized on app instance)
                try {
                    view.commitComputedState()
                } catch (e: Exception) {
                    LOG.error("commitComputedState for view {}", view.instanceId, e)
                }

                iter.remove()
            }

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

        private fun writeResponse(json: ExtensibleObjectOutput, requestId: Long, isPing: Boolean, views: List<GenericViewImpl<*>>) {
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
                    json.name("states")
                    json.beginArray()
                    for (view in views) {
                        view.writeState(json)
                    }
                    json.endArray()
                }
            }
            json.endObject()
        }
    }
}
