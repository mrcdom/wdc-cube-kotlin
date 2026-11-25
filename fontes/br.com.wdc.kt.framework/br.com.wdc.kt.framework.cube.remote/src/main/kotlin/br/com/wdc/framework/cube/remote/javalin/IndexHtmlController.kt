package br.com.wdc.framework.cube.remote.javalin

import br.com.wdc.framework.commons.codec.Base62
import br.com.wdc.framework.cube.remote.RemoteAppSecurity
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import jakarta.servlet.http.Cookie
import java.security.SecureRandom

/**
 * Before the client's page (`/index.html`) is served: plants the cookies it starts a session with — `app_id`, a new
 * session id signed by the host ([RemoteAppSecurity.signAsHash]), and `app_skey`, the host's public key for the
 * handshake — and keeps the page out of caches.
 */
class IndexHtmlController {

    companion object {
        fun configure(config: JavalinConfig) {
            val controller = IndexHtmlController()
            config.routes.before("/index.html", controller::handle)
        }
    }

    private val rnd = SecureRandom()

    private fun handle(ctx: Context) {
        ctx.res().setHeader("Cache-Control", "no-cache, no-store")
        ctx.res().setHeader("Pragma", "no-cache")
        ctx.res().setDateHeader("Expires", 0)

        val appIdCookie = Cookie("app_id", makeAppId())
        appIdCookie.path = "/"
        appIdCookie.maxAge = 10
        ctx.res().addCookie(appIdCookie)

        val pubKeyCookie = Cookie("app_skey", RemoteAppSecurity.webKey)
        pubKeyCookie.path = "/"
        pubKeyCookie.maxAge = -1
        ctx.res().addCookie(pubKeyCookie)
    }

    private fun makeAppId(): String {
        val security = RemoteAppSecurity
        val b62 = Base62

        val appIdPart1Bytes = ByteArray(32)
        rnd.nextBytes(appIdPart1Bytes)
        val appIdPart1 = b62.encodeToString(appIdPart1Bytes)

        val appIdPart2 = b62.encodeToString(security.signAsHash(appIdPart1.toByteArray()))

        return "$appIdPart1.$appIdPart2"
    }
}
