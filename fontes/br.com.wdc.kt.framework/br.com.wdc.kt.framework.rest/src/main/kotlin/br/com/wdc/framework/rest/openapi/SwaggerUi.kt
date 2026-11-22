package br.com.wdc.framework.rest.openapi

import br.com.wdc.framework.rest.RestHandler
import br.com.wdc.framework.rest.RestResponse
import com.google.gson.Gson
import io.javalin.http.NotFoundResponse
import java.util.Properties

/** A document in the Swagger page's selector: where it is served and the name shown. */
data class SwaggerDocument(val url: String, val name: String)

/**
 * The Swagger UI page ([OpenApiConfig.ui]), served from the official webjar on the classpath, without a CDN.
 *
 * The webjar doesn't come with the library: whoever turns the page on declares it
 * (`runtimeOnly("org.webjars:swagger-ui:<version>")`). The version is read from the webjar itself; without it on the
 * classpath, building the [br.com.wdc.framework.rest.RestApi] fails.
 *
 * Routes, public and left out of the OpenAPI document:
 * - `GET {path}.html` → 302 to `{path}/index.html`;
 * - `GET {path}/index.html` → the page, with the API's own document and those in [documents] (with more than one,
 *   the selector at the top, opened on [primary]);
 * - `GET {path}/{file}` → only the files the page loads, with `Cache-Control: public, max-age=86400`; the rest
 *   of the webjar is 404.
 */
class SwaggerUiOptions {

    /** Where the page lives. */
    var path: String = "/swagger-ui"

    /** The tab title. `null` = the OpenAPI document's title. */
    var title: String? = null

    /** Lists the API's own document, first. */
    var includeOwn: Boolean = true

    /** Other documents: another `RestApi`'s on the same server, a static document. */
    val documents = ArrayList<SwaggerDocument>()

    /** The name of the document opened when there are several. `null` = the first. */
    var primary: String? = null

    fun document(url: String, name: String) {
        documents += SwaggerDocument(url, name)
    }
}

/** The page routes. */
internal class SwaggerUiRoutes(private val options: SwaggerUiOptions, private val api: OpenApiConfig) {

    private val base: String = locateWebjar()

    val documents: List<SwaggerDocument> =
        (if (options.includeOwn) listOf(SwaggerDocument(api.path, api.title)) else emptyList()) + options.documents

    init {
        require(documents.isNotEmpty()) { "openApi { ui { } }: no document to show" }
        options.primary?.let { name -> require(documents.any { it.name == name }) { "openApi { ui { primary = \"$name\" } }: no such document" } }
    }

    val root: String = options.path.trimEnd('/')

    val redirect: RestHandler = { _ -> RestResponse(302, null, headers = mapOf("Location" to "$root/index.html")) }

    val file: RestHandler = { call ->
        when (val name = call.path("file")) {
            "index.html" -> RestResponse(200, page, contentType = "text/html; charset=utf-8")
            in FILES -> {
                val bytes = javaClass.classLoader.getResourceAsStream(base + name)?.use { it.readBytes() }
                    ?: throw NotFoundResponse("Not found")
                RestResponse(200, bytes, contentType = FILES.getValue(name), headers = mapOf("Cache-Control" to "public, max-age=86400"))
            }
            else -> throw NotFoundResponse("Not found")
        }
    }

    private val page: String by lazy { renderPage() }

    private fun renderPage(): String {
        val config = LinkedHashMap<String, Any>()
        if (documents.size == 1) {
            config["url"] = documents.single().url
        } else {
            config["urls"] = documents.map { mapOf("url" to it.url, "name" to it.name) }
            config["urls.primaryName"] = options.primary ?: documents.first().name
        }
        // default Gson escapes < > & = ' as \uXXXX: the JSON can't close the <script>
        val json = Gson().toJson(config)
        val title = escapeHtml(options.title ?: api.title)
        return """
            |<!DOCTYPE html>
            |<html lang="en">
            |<head>
            |<meta charset="UTF-8">
            |<title>$title</title>
            |<link rel="stylesheet" type="text/css" href="swagger-ui.css">
            |<link rel="icon" type="image/png" href="favicon-32x32.png" sizes="32x32">
            |<style>html{box-sizing:border-box;overflow-y:scroll}*,*:before,*:after{box-sizing:inherit}body{margin:0;background:#fafafa}</style>
            |</head>
            |<body>
            |<div id="swagger-ui"></div>
            |<script src="swagger-ui-bundle.js" charset="UTF-8"></script>
            |<script src="swagger-ui-standalone-preset.js" charset="UTF-8"></script>
            |<script>
            |window.onload = function () {
            |  window.ui = SwaggerUIBundle(Object.assign($json, {
            |    dom_id: "#swagger-ui",
            |    deepLinking: true,
            |    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
            |    plugins: [SwaggerUIBundle.plugins.DownloadUrl],
            |    layout: "StandaloneLayout"
            |  }));
            |};
            |</script>
            |</body>
            |</html>
            |""".trimMargin()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        /** The files the page loads, with each one's type. */
        val FILES = mapOf(
            "swagger-ui.css" to "text/css; charset=utf-8",
            "swagger-ui-bundle.js" to "text/javascript; charset=utf-8",
            "swagger-ui-standalone-preset.js" to "text/javascript; charset=utf-8",
            "favicon-32x32.png" to "image/png",
        )

        private const val POM_PROPERTIES = "META-INF/maven/org.webjars/swagger-ui/pom.properties"

        /** The webjar's directory on the classpath, by the version it declares itself. */
        fun locateWebjar(): String {
            val loader = SwaggerUiRoutes::class.java.classLoader
            val stream = loader.getResourceAsStream(POM_PROPERTIES)
                ?: error("openApi { ui { } } needs the org.webjars:swagger-ui webjar on the classpath")
            val version = stream.use { Properties().apply { load(it) }.getProperty("version") }
                ?: error("$POM_PROPERTIES has no version")
            return "META-INF/resources/webjars/swagger-ui/$version/"
        }
    }
}
