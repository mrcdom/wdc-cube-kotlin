package br.com.wdc.shopping.view.react.controller

import br.com.wdc.framework.commons.codec.Base62
import br.com.wdc.shopping.view.react.skeleton.util.AppSecurity
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import jakarta.servlet.http.Cookie
import java.security.SecureRandom

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

        val pubKeyCookie = Cookie("app_skey", AppSecurity.webKey)
        pubKeyCookie.path = "/"
        pubKeyCookie.maxAge = -1
        ctx.res().addCookie(pubKeyCookie)
    }

    private fun makeAppId(): String {
        val security = AppSecurity
        val b62 = Base62

        val appIdPart1Bytes = ByteArray(32)
        rnd.nextBytes(appIdPart1Bytes)
        val appIdPart1 = b62.encodeToString(appIdPart1Bytes)

        val appIdPart2 = b62.encodeToString(security.signAsHash(appIdPart1.toByteArray()))

        return "$appIdPart1.$appIdPart2"
    }
}
