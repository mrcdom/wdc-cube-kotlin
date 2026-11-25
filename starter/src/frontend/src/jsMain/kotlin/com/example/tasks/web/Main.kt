package com.example.tasks.web

import br.com.wdc.framework.cube.CubeIntent
import com.example.tasks.RootKeys
import com.example.tasks.TasksApp
import com.example.tasks.data.CallHttpClient
import kotlinx.browser.window
import kotlinx.coroutines.launch
import react.FC
import react.Props
import react.create
import react.dom.client.createRoot
import web.dom.ElementId
import web.dom.document

/** The application in the browser: the address of the current place goes to the location's hash. */
private class WebTasksApp : TasksApp() {

    override fun updateHistory() {
        val intent = CubeIntent()
        intent.place = getLastPlace() ?: RootKeys.PLACE
        publishParameters(intent)
        val address = intent.toString()
        fragment = address
        if (hash() != address) window.location.hash = "#$address"
    }
}

private fun hash(): String = window.location.hash.removePrefix("#")

fun main() {
    // the calls reach the backend that served this page
    CallHttpClient.BEAN.set(FetchCallHttpClient())

    val app = WebTasksApp()
    PaintCycle.app = app

    // the address in the page (a link, a reload, back and forward) is a navigation like any other
    fun go(address: String) = app.presenterScope.launch {
        try {
            if (address.isBlank()) RootKeys(app).flip() else app.flip(address)
        } finally {
            PaintCycle.flush()
        }
    }
    go(hash())
    window.addEventListener("hashchange", { if (hash() != app.fragment) go(hash()) })

    val root = FC<Props> {
        ScopeViewsContext.Provider {
            value = catalog
            Slot { scope = app.scope }
        }
    }
    createRoot(document.getElementById(ElementId("app"))!!).render(root.create())
}
