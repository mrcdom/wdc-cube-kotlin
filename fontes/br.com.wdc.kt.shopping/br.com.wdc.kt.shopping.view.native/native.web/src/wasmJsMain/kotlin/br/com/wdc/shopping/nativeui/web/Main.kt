package br.com.wdc.shopping.nativeui.web

import kotlinx.browser.document

fun main() {
    val app = document.getElementById("app") ?: return

    app.innerHTML = "<h1>Shopping — Native Web</h1><p>Skeleton pronto.</p>"

    println("Native Web app initialized")
}
