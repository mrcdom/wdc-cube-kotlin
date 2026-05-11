package br.com.wdc.shopping.view.react.controller

import io.javalin.config.JavalinConfig
import io.javalin.http.Context

class StatusController {

    companion object {
        fun configure(config: JavalinConfig) {
            val controller = StatusController()
            config.routes.get("/health", controller::handle)
        }
    }

    private fun handle(ctx: Context) {
        ctx.json(mapOf("status" to "UP"))
    }
}
