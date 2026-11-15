package br.com.wdc.framework.rest.openapi

import br.com.wdc.framework.rest.RestHandler
import br.com.wdc.framework.rest.RestResponse
import com.google.gson.Gson
import io.javalin.http.NotFoundResponse
import java.util.Properties

/** Um documento no seletor da página do Swagger: onde ele é servido e o nome que aparece. */
data class SwaggerDocument(val url: String, val name: String)

/**
 * A página do Swagger UI ([OpenApiConfig.ui]), servida do webjar oficial pelo classpath, sem CDN.
 *
 * O webjar não vem com a biblioteca: quem liga a página o declara (`runtimeOnly("org.webjars:swagger-ui:<versão>")`).
 * A versão é lida do próprio webjar; sem ele no classpath, a montagem da [br.com.wdc.framework.rest.RestApi] falha.
 *
 * Rotas, públicas e fora do documento OpenAPI:
 * - `GET {path}.html` → 302 para `{path}/index.html`;
 * - `GET {path}/index.html` → a página, com o documento da própria API e os de [documents] (com mais de um, o
 *   seletor do topo, aberto em [primary]);
 * - `GET {path}/{arquivo}` → só os arquivos que a página carrega, com `Cache-Control: public, max-age=86400`; o resto
 *   do webjar é 404.
 */
class SwaggerUiOptions {

    /** Onde a página fica. */
    var path: String = "/swagger-ui"

    /** O título da aba. `null` = o título do documento OpenAPI. */
    var title: String? = null

    /** Lista o documento da própria API, em primeiro lugar. */
    var includeOwn: Boolean = true

    /** Outros documentos: o de outra `RestApi` no mesmo servidor, um documento estático. */
    val documents = ArrayList<SwaggerDocument>()

    /** O nome do documento aberto quando há vários. `null` = o primeiro. */
    var primary: String? = null

    fun document(url: String, name: String) {
        documents += SwaggerDocument(url, name)
    }
}

/** As rotas da página. */
internal class SwaggerUiRoutes(private val options: SwaggerUiOptions, private val api: OpenApiConfig) {

    private val base: String = locateWebjar()

    val documents: List<SwaggerDocument> =
        (if (options.includeOwn) listOf(SwaggerDocument(api.path, api.title)) else emptyList()) + options.documents

    init {
        require(documents.isNotEmpty()) { "openApi { ui { } }: nenhum documento a mostrar" }
        options.primary?.let { name -> require(documents.any { it.name == name }) { "openApi { ui { primary = \"$name\" } }: documento inexistente" } }
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
        // o Gson padrão escapa < > & = ' como \uXXXX: o JSON não fecha o <script>
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
        /** Os arquivos que a página carrega, com o tipo de cada um. */
        val FILES = mapOf(
            "swagger-ui.css" to "text/css; charset=utf-8",
            "swagger-ui-bundle.js" to "text/javascript; charset=utf-8",
            "swagger-ui-standalone-preset.js" to "text/javascript; charset=utf-8",
            "favicon-32x32.png" to "image/png",
        )

        private const val POM_PROPERTIES = "META-INF/maven/org.webjars/swagger-ui/pom.properties"

        /** O diretório do webjar no classpath, pela versão que ele mesmo declara. */
        fun locateWebjar(): String {
            val loader = SwaggerUiRoutes::class.java.classLoader
            val stream = loader.getResourceAsStream(POM_PROPERTIES)
                ?: error("openApi { ui { } } precisa do webjar org.webjars:swagger-ui no classpath")
            val version = stream.use { Properties().apply { load(it) }.getProperty("version") }
                ?: error("$POM_PROPERTIES sem versão")
            return "META-INF/resources/webjars/swagger-ui/$version/"
        }
    }
}
