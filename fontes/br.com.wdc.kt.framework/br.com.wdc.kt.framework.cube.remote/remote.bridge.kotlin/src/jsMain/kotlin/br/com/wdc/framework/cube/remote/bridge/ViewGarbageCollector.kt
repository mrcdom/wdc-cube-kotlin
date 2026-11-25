package br.com.wdc.framework.cube.remote.bridge

import kotlinx.browser.window

/**
 * Lets go of the bindings of views no longer on screen. A view that leaves the screen is collected 4 seconds later,
 * unless something holds it (a scope that refers to it, or [RemoteBridge.useHold]) or it comes back; a view the host
 * released (`releasedViews`) goes at once, or as soon as it leaves the screen.
 */
internal class ViewGarbageCollector(private val bridge: RemoteBridge) {

    private val garbage = HashMap<String, ViewBinding>()
    private val holdCount = HashMap<String, Int>()
    private val autoHeldByScope = HashMap<String, Set<String>>()

    /** The views on screen now. */
    private val mounted = HashSet<String>()

    /** The views the host let go of while still on screen: dropped when they leave it. */
    private val releasedByHost = HashSet<String>()

    private var task = 0

    fun hold(vsid: String) {
        holdCount[vsid] = (holdCount[vsid] ?: 0) + 1
        recover(vsid)
    }

    fun release(vsid: String) {
        val count = (holdCount[vsid] ?: 0) - 1
        if (count <= 0) holdCount.remove(vsid) else holdCount[vsid] = count
    }

    /** Holds the views [binding]'s scope refers to now, and lets go of those it no longer does. */
    fun updateAutoHolds(binding: ViewBinding) {
        val previous = autoHeldByScope[binding.vsid] ?: emptySet()
        val now = binding.referencedViewIds
        for (vsid in previous) if (vsid !in now) release(vsid)
        for (vsid in now) if (vsid !in previous) hold(vsid)
        if (now.isEmpty()) autoHeldByScope.remove(binding.vsid) else autoHeldByScope[binding.vsid] = now
    }

    fun mount(vsid: String) {
        mounted += vsid
    }

    /** The host let go of these views: each goes now, or when it leaves the screen. */
    fun released(vsids: List<String>) {
        for (vsid in vsids) {
            if (vsid in mounted) releasedByHost += vsid else collect(vsid)
        }
    }

    /** The view left the screen. */
    fun mark(binding: ViewBinding) {
        val vsid = binding.vsid
        mounted -= vsid
        if (vsid in releasedByHost) {
            collect(vsid)
            return
        }
        if (vsid != RemoteBridge.BROWSER_VSID) {
            garbage[vsid] = binding
            schedule()
        }
    }

    /** The binding of [vsid] back from the garbage, if it was there. */
    fun recover(vsid: String): ViewBinding? {
        val binding = garbage.remove(vsid) ?: return null
        bridge.viewMap[vsid] = binding
        cancelIfEmpty()
        return binding
    }

    private fun collect(vsid: String) {
        releaseAutoHolds(vsid)
        bridge.viewMap.remove(vsid)
        garbage.remove(vsid)
        releasedByHost -= vsid
        cancelIfEmpty()
    }

    private fun releaseAutoHolds(vsid: String) {
        autoHeldByScope.remove(vsid)?.forEach(::release)
    }

    private fun schedule() {
        window.clearTimeout(task)
        task = window.setTimeout({ sweep() }, 4000)
    }

    private fun sweep() {
        window.clearTimeout(task)
        val unheld = garbage.keys.filter { it !in holdCount }
        for (vsid in unheld) {
            releaseAutoHolds(vsid)
            bridge.viewMap.remove(vsid)
            garbage.remove(vsid)
        }
    }

    private fun cancelIfEmpty() {
        if (garbage.isEmpty()) window.clearTimeout(task)
    }
}
