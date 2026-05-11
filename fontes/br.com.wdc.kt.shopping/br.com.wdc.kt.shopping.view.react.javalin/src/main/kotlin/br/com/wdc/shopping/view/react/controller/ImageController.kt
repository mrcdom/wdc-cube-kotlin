package br.com.wdc.shopping.view.react.controller

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.shopping.domain.repositories.ProductRepository
import io.javalin.config.JavalinConfig
import io.javalin.http.Context

class ImageController {

    companion object {
        private val LOG = Log.getLogger(ImageController::class.java)

        fun configure(config: JavalinConfig) {
            val controller = ImageController()
            config.routes.get("/image/product/{productId}.png", controller::handle)
        }
    }

    private fun handle(ctx: Context) {
        val productId: Long
        try {
            productId = ctx.pathParam("productId").toLong()
        } catch (e: Exception) {
            LOG.error("Parsing productId from URL", e)
            ctx.status(400)
            return
        }

        val imageBytes: ByteArray?
        try {
            imageBytes = ProductRepository.BEAN.get().fetchImage(productId)
        } catch (caught: Exception) {
            LOG.error("Processing image request", caught)
            ctx.status(500)
            return
        }

        if (imageBytes == null) {
            ctx.status(204)
            return
        }

        ctx.contentType("image/png")
        ctx.result(imageBytes)
    }
}
