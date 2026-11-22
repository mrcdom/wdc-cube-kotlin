package br.com.wdc.framework.rest

/**
 * CORS, through Javalin's plugin. The preflight (`OPTIONS`) is answered by the plugin, before the routes and
 * security. The methods and headers requested by the browser are accepted (Spring's `allowedMethods("*")`).
 */
class CorsOptions {
    internal val origins = ArrayList<String>()
    internal var anyOrigin = false

    /** `Access-Control-Allow-Credentials`. Doesn't combine with any origin: use [reflectClientOrigin]. */
    var allowCredentials: Boolean = false

    /** Returns the request's origin as allowed — "any origin" with credentials. */
    var reflectClientOrigin: Boolean = false

    /** `Access-Control-Max-Age`, in seconds; negative = not sent. */
    var maxAge: Int = -1

    /** Paths the rule applies to, in Javalin's format. */
    var path: String = "*"

    val exposedHeaders = ArrayList<String>()

    fun anyOrigin() {
        anyOrigin = true
    }

    fun allowOrigins(vararg origins: String) {
        this.origins += origins
    }
}

/** Response compression. */
class CompressionOptions {
    /** Gzip level, from 1 to 9. */
    var gzipLevel: Int = 6

    /** Responses smaller than this go out uncompressed. The default is Spring Boot's (2 KB). */
    var minSize: Int = 2048

    /** Only these types are compressed; empty = Javalin's (all except images, video and archives). */
    val mimeTypes = ArrayList<String>()

    companion object {
        /** The types Spring Boot compresses by default. */
        val SPRING_MIME_TYPES = listOf(
            "text/html", "text/xml", "text/plain", "text/css", "text/javascript", "application/javascript",
            "application/json", "application/xml",
        )
    }
}
