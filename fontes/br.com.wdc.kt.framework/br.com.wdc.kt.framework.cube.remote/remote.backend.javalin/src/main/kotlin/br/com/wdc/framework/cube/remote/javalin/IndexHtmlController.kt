package br.com.wdc.framework.cube.remote.javalin

import br.com.wdc.framework.cube.remote.RemoteAppSecurity
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import jakarta.servlet.http.Cookie

/**
 * Before a remote client's page (`/index.html`, or `/<context>/index.html` for a client served under a context) is
 * served: plants the cookie the page starts its session with — `app_id`, a new session id signed by the host
 * ([RemoteAppSecurity.newSessionId]) — and keeps the page out of caches.
 */
class IndexHtmlController {

    companion object {
        /**
         * Plants the cookies on `/index.html` and on `/<context>/index.html` of each of [contexts] (the contexts that
         * serve a remote client; the other pages of the site do not start a session).
         */
        fun configure(config: JavalinConfig, vararg contexts: String) {
            val controller = IndexHtmlController()
            config.routes.before("/index.html", controller::handle)
            for (context in contexts) config.routes.before("/$context/index.html", controller::handle)
        }
    }

    private fun handle(ctx: Context) {
        ctx.res().setHeader("Cache-Control", "no-cache, no-store")
        ctx.res().setHeader("Pragma", "no-cache")
        ctx.res().setDateHeader("Expires", 0)

        val appIdCookie = Cookie("app_id", RemoteAppSecurity.newSessionId())
        appIdCookie.path = "/"
        appIdCookie.maxAge = 10
        ctx.res().addCookie(appIdCookie)
    }
}
