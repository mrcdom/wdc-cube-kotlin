package br.com.wdc.shopping.domain

import br.com.wdc.shopping.domain.config.AppConfig
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object ShoppingConfig {

    var baseDir: Path? = null
        private set

    var configDir: Path? = null
        private set

    var dataDir: Path? = null
        private set

    var logDir: Path? = null
        private set

    var tempDir: Path? = null
        private set

    var jwtSecret: String? = null
        private set

    object Internals {

        fun setBaseDir(path: Path) { ShoppingConfig.baseDir = path }
        fun setConfigDir(path: Path) { ShoppingConfig.configDir = path }
        fun setDataDir(path: Path) { ShoppingConfig.dataDir = path }
        fun setLogDir(path: Path) { ShoppingConfig.logDir = path }
        fun setTempDir(path: Path) { ShoppingConfig.tempDir = path }
        fun setJwtSecret(secret: String?) { ShoppingConfig.jwtSecret = secret }

        fun configure(config: AppConfig) {
            try {
                val base = resolveRuntimeBaseDir(config)
                val cfg = createDirectory(base.resolve("config"))
                val data = createDirectory(base.resolve("data"))
                val log = createDirectory(base.resolve("log"))
                val temp = createDirectory(base.resolve("temp"))

                setBaseDir(base)
                setConfigDir(cfg)
                setDataDir(data)
                setLogDir(log)
                setTempDir(temp)
                setJwtSecret(config.get("security.jwt.secret"))
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }

        private fun createDirectory(dir: Path): Path {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir)
            }
            return dir
        }

        private fun resolveRuntimeBaseDir(config: AppConfig): Path {
            val configuredDir = config.get("app.basedir")
            val base = if (!configuredDir.isNullOrBlank()) Paths.get(configuredDir) else Paths.get("work")
            return createDirectory(base.toAbsolutePath().normalize())
        }
    }
}
