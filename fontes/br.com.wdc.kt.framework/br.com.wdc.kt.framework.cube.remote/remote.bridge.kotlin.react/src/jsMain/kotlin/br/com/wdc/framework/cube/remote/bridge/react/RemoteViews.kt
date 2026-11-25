package br.com.wdc.framework.cube.remote.bridge.react

import br.com.wdc.framework.cube.remote.bridge.RemoteBridge
import br.com.wdc.framework.cube.remote.bridge.RemoteScope
import react.ChildrenBuilder
import react.FC
import react.Props
import react.createRequiredContext
import react.memo
import react.useEffectWithCleanup
import react.useRequired
import react.useState

/** The props of a remote view: the instance id of the view the host published. */
external interface RemoteViewProps : Props {
    var vsid: String
}

/**
 * The React side of a [RemoteBridge]: which component paints each vid, and the hooks a view uses.
 *
 * ```kotlin
 * val bridge = RemoteBridge()
 * val views = RemoteViews(bridge)
 *     .register(BrowserView.VID, BrowserView)
 *     .register("c677cda52d14", LoginView)
 *
 * // the root of the page
 * RemoteViewsContext.Provider(views) { views.render(this, RemoteBridge.BROWSER_VSID) }
 * bridge.start()
 *
 * // a view
 * val LoginView = FC<RemoteViewProps> { props ->
 *     val bridge = useRequired(RemoteViewsContext).bridge
 *     val scope = bridge.useView(props.vsid)
 *     …
 *     button { onClick = { bridge.setFormField(props.vsid, "userName", name); bridge.submit(props.vsid, 1) } }
 * }
 * ```
 *
 * Each registered component is memoized: a parent repaint does not repaint it. A view repaints itself when the host
 * sends its scope ([useView]), as in the other Cube view layers.
 */
class RemoteViews(val bridge: RemoteBridge) {

    private val components = HashMap<String, FC<RemoteViewProps>>()

    /** [component] paints the views of [vid]. */
    fun register(vid: String, component: FC<RemoteViewProps>): RemoteViews {
        components[vid] = memo(component)
        return this
    }

    /** Draws the view of [vsid] in [builder]; nothing for `null` or a vid with no component. */
    fun render(builder: ChildrenBuilder, vsid: String?) {
        if (vsid.isNullOrEmpty()) return
        val component = components[vsid.substringBefore(':')]
            ?: error("No view registered for vid ${vsid.substringBefore(':')}")
        bridge.bindingOf(vsid)
        with(builder) {
            component {
                key = vsid
                this.vsid = vsid
            }
        }
    }
}

/** The views the page draws with: provided around the root. */
val RemoteViewsContext = createRequiredContext<RemoteViews>()

/** Draws the view of [vsid] with the views of the context: a nested scope's view, in its parent's. */
fun ChildrenBuilder.remoteView(vsid: String?) {
    RemoteSlot {
        this.vsid = vsid ?: ""
    }
}

private val RemoteSlot = FC<RemoteViewProps> { props ->
    useRequired(RemoteViewsContext).render(this, props.vsid.ifEmpty { null })
}

/**
 * Ties the calling component to the view [vsid]: it repaints each time the host sends the view's scope, and the
 * collector knows when it is on screen. Returns the scope as it is now.
 */
fun RemoteBridge.useView(vsid: String): RemoteScope {
    val repaint = useState(0).component2()
    val binding = bindingOf(vsid)
    binding.onChange = { repaint { it + 1 } }
    useEffectWithCleanup(vsid) {
        mounted(vsid)
        onCleanup { unmounted(binding) }
    }
    return binding.scope
}

/** Keeps the view [vsid] while the calling component is on screen, even if nothing shows it yet. */
fun RemoteBridge.useHold(vsid: String?) {
    useEffectWithCleanup(vsid) {
        if (vsid != null) hold(vsid)
        onCleanup { if (vsid != null) release(vsid) }
    }
}
