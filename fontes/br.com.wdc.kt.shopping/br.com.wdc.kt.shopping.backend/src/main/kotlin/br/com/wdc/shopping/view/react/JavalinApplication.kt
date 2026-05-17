package br.com.wdc.shopping.view.react

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.Slf4jLogFactory
import br.com.wdc.framework.commons.serialization.JsonOutputFactory
import br.com.wdc.framework.commons.serialization.JsonInputFactory
import br.com.wdc.framework.commons.serialization.installCommon
import br.com.wdc.shopping.domain.config.AppConfig
import br.com.wdc.shopping.view.react.controller.DispatcherController
import br.com.wdc.shopping.view.react.controller.ImageController
import br.com.wdc.shopping.view.react.controller.IndexHtmlController
import br.com.wdc.shopping.view.react.controller.StatusController
import br.com.wdc.shopping.persistence.rest.RepositoryApiRoutes
import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.http.staticfiles.Location
import io.javalin.json.JsonMapper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

class JavalinApplication(
    private val port: Int = DEFAULT_PORT,
    private val corsAllowAll: Boolean = false,
) {

    companion object {
        private val LOG: Log

        private const val STATIC_FILES_DIR = "META-INF/resources"
        private const val STATIC_IMAGES_FILES_DIR = "$STATIC_FILES_DIR/images"
        private const val STATIC_FILES_EXTERNAL_DIR_ENV = "SHOPPING_STATIC_FILES_DIR"
        private const val STATIC_FILES_EXTERNAL_DIR_PROPERTY = "shopping.staticFilesDir"
        private const val STATIC_HOSTED_IMAGE_PATH = "/images"

        private const val DEFAULT_PORT = 8080

        init {
            Log.setFactory(Slf4jLogFactory())
            LOG = Log.getLogger("JavalinApplication")
        }

        private fun isStaticResource(path: String): Boolean {
            return path.endsWith(".js") ||
                    path.endsWith(".css") ||
                    path.endsWith(".html") ||
                    path.endsWith(".json") ||
                    path.endsWith(".map") ||
                    path.endsWith(".png") ||
                    path.endsWith(".jpg") ||
                    path.endsWith(".gif") ||
                    path.endsWith(".svg") ||
                    path.endsWith(".woff") ||
                    path.endsWith(".woff2") ||
                    path.endsWith(".ttf") ||
                    path == "/"
        }

        private fun resolveStaticFilesSettings(): StaticFilesSettings {
            var customStaticDir = System.getProperty(STATIC_FILES_EXTERNAL_DIR_PROPERTY)
            if (customStaticDir.isNullOrBlank()) {
                customStaticDir = System.getenv(STATIC_FILES_EXTERNAL_DIR_ENV)
            }

            if (!customStaticDir.isNullOrBlank()) {
                val resolvedPath = Paths.get(customStaticDir).toAbsolutePath().normalize()
                if (Files.isDirectory(resolvedPath)) {
                    return StaticFilesSettings(resolvedPath.toString(), Location.EXTERNAL)
                }
                LOG.warn("External static files directory does not exist: {} (resolved to {}). Falling back to classpath.", customStaticDir, resolvedPath)
            }

            return StaticFilesSettings(STATIC_FILES_DIR, Location.CLASSPATH)
        }

        @JvmStatic
        fun main(args: Array<String>) {
            try {
                doMain(args)
            } catch (t: Throwable) {
                t.printStackTrace(System.err)
                System.exit(1)
            }
        }

        fun doMain(args: Array<String>) {
            Log.setFactory(Slf4jLogFactory())
            JsonOutputFactory.installCommon()
            JsonInputFactory.installCommon()
            val config = AppConfig.load()
            var port = config.getInt("server.port", DEFAULT_PORT)

            if (args.isNotEmpty()) {
                try {
                    port = args[0].toInt()
                } catch (_: NumberFormatException) {
                    LOG.warn("Invalid port number: {}, using default {}", args[0], DEFAULT_PORT)
                }
            }

            val portEnv = System.getenv("SERVER_PORT")
            if (!portEnv.isNullOrBlank()) {
                try {
                    port = portEnv.toInt()
                } catch (_: NumberFormatException) {
                    LOG.warn("Invalid SERVER_PORT environment variable: {}, using default {}", portEnv, DEFAULT_PORT)
                }
            }

            LOG.info("Starting WeDoCode Shopping React Server on port {}", port)

            val corsAllowAll = config.getBoolean("server.cors.allowAll", false)
            if (corsAllowAll) {
                LOG.info("CORS: allowing any origin (development mode)")
            }

            val server = JavalinApplication(port, corsAllowAll)

            Runtime.getRuntime().addShutdownHook(Thread {
                LOG.info("Shutdown signal received")
                server.stop()
            })

            server.start()

            try {
                Thread.currentThread().join()
            } catch (_: InterruptedException) {
                LOG.info("Main thread interrupted")
                server.stop()
            }
        }
    }

    private data class StaticFilesSettings(val directory: String, val location: Location)

    private val businessContext = BusinessContext()
    private val staticFilesSettings = resolveStaticFilesSettings()
    private val app: Javalin

    init {
        businessContext.start()
        app = createJavalinApp()
    }

    private fun createJavalinApp(): Javalin {
        val gson = Gson()
        val gsonMapper = object : JsonMapper {
            override fun <T : Any> fromJsonString(json: String, targetType: Type): T {
                return gson.fromJson(json, targetType)
            }
            override fun toJsonString(obj: Any, type: Type): String {
                return gson.toJson(obj, type)
            }
            override fun toJsonStream(obj: Any, type: Type): InputStream {
                return ByteArrayInputStream(toJsonString(obj, type).toByteArray())
            }
        }

        return Javalin.create { config ->
            config.jsonMapper(gsonMapper)
            config.concurrency.useVirtualThreads = true

            config.jetty.modifyWebSocketServletFactory { wsFactory ->
                wsFactory.idleTimeout = Duration.ofMinutes(2)
            }

            config.jetty.modifyServer { server ->
                server.connectors.forEach { connector ->
                    if (connector is org.eclipse.jetty.server.ServerConnector) {
                        connector.idleTimeout = Duration.ofMinutes(5).toMillis()
                    }
                }
            }

            config.bundledPlugins.enableCors { cors ->
                cors.addRule { rule ->
                    if (corsAllowAll) {
                        rule.anyHost()
                    } else {
                        rule.allowHost(
                            "tauri://localhost", "https://tauri.localhost",
                            "http://tauri.localhost",
                            "http://localhost:8080", "http://shopping-wdc.localhost:8080"
                        )
                        rule.allowCredentials = true
                    }
                }
            }

            config.staticFiles.add { staticFileConfig ->
                staticFileConfig.directory = staticFilesSettings.directory
                staticFileConfig.location = staticFilesSettings.location
                staticFileConfig.precompressMaxSize = 0
            }

            config.staticFiles.add { staticFileConfig ->
                staticFileConfig.directory = STATIC_IMAGES_FILES_DIR
                staticFileConfig.location = Location.CLASSPATH
                staticFileConfig.hostedPath = STATIC_HOSTED_IMAGE_PATH
                staticFileConfig.precompressMaxSize = 0
            }

            config.http.defaultContentType = "application/json"

            configureRoutes(config)
        }
    }

    private fun configureRoutes(config: JavalinConfig) {
        config.routes.exception(Exception::class.java) { e, ctx ->
            LOG.error("Unhandled exception in request processing", e)
            ctx.status(500).json(mapOf("error" to "Internal server error"))
        }

        config.routes.before { ctx -> LOG.debug("HTTP {} {}", ctx.method(), ctx.path()) }

        StatusController.configure(config)
        ImageController.configure(config)

        // Repository REST API
        RepositoryApiRoutes.configure(config)

        config.routes.get("/") { ctx -> ctx.redirect("/index.html") }

        DispatcherController.configure(config)
        IndexHtmlController.configure(config)

        config.routes.before { ctx ->
            val path = ctx.path()
            if (!path.startsWith("/api/")
                && !path.startsWith("/ws/")
                && !path.startsWith("/health")
                && !path.startsWith("/dispatcher")
                && path != "/index.html"
                && !isStaticResource(path)
            ) {
                LOG.debug("SPA fallback for path: {}", path)
                ctx.redirect("/index.html")
            }
        }
    }

    fun start() {
        try {
            app.start(port)
            LOG.info("Javalin server started on port {}", port)
            LOG.info("Static files served from {}: {}", staticFilesSettings.location, staticFilesSettings.directory)
        } catch (e: Exception) {
            LOG.error("Failed to start Javalin server", e)
            throw AssertionError("Server startup failed", e)
        }
    }

    fun stop() {
        try {
            businessContext.stop()
            app.stop()
            LOG.info("Javalin server stopped")
        } catch (e: Exception) {
            LOG.error("Error stopping server", e)
        }
    }
}
