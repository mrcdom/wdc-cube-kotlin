package br.com.wdc.shopping.domain

import br.com.wdc.shopping.domain.config.AppConfig
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * O diretório de trabalho do serviço e o que a configuração diz de geral.
 *
 * **O diretório de trabalho é informado por quem sobe o serviço** — não há padrão, nem ele é deduzido de onde o
 * processo roda. Dele saem todas as pastas de execução:
 *
 * | Pasta | Para quê |
 * |---|---|
 * | `config/` | a configuração (`application.toml`) e os arquivos que a apoiam, inclusive a do log |
 * | `data/` | dados locais que a aplicação reaproveita entre reinícios (o banco H2 padrão) |
 * | `log/` | o arquivo de log e as suas rotações, conforme a configuração do log |
 * | `tmp/` | arquivos temporários gerados durante a execução |
 * | `deployment/` | cada subpasta é um contexto de recursos estáticos que o servidor publica |
 */
object ShoppingConfig {

    const val CONFIG_FILE_NAME = "application.toml"

    var workDir: Path? = null
        private set

    var configDir: Path? = null
        private set

    var dataDir: Path? = null
        private set

    var logDir: Path? = null
        private set

    var tmpDir: Path? = null
        private set

    var deploymentDir: Path? = null
        private set

    var jwtSecret: String? = null
        private set

    var refreshTokenTtlDays: Int = 7
        private set

    object Internals {

        fun setWorkDir(path: Path) { ShoppingConfig.workDir = path }
        fun setConfigDir(path: Path) { ShoppingConfig.configDir = path }
        fun setDataDir(path: Path) { ShoppingConfig.dataDir = path }
        fun setLogDir(path: Path) { ShoppingConfig.logDir = path }
        fun setTmpDir(path: Path) { ShoppingConfig.tmpDir = path }
        fun setDeploymentDir(path: Path) { ShoppingConfig.deploymentDir = path }
        fun setJwtSecret(secret: String?) { ShoppingConfig.jwtSecret = secret }
        fun setRefreshTokenTtlDays(days: Int) { ShoppingConfig.refreshTokenTtlDays = days }

        /**
         * Fixa as pastas de execução a partir do diretório de trabalho, criando as que faltarem.
         *
         * @param workDir diretório de trabalho; **precisa existir** — um caminho errado não vira, em silêncio,
         *                um ambiente novo e vazio
         */
        fun useWorkDir(workDir: Path) {
            val work = workDir.toAbsolutePath().normalize()
            require(Files.isDirectory(work)) { "O diretório de trabalho não existe: $work" }
            try {
                setWorkDir(work)
                setConfigDir(createDirectory(work.resolve("config")))
                setDataDir(createDirectory(work.resolve("data")))
                setLogDir(createDirectory(work.resolve("log")))
                setTmpDir(createDirectory(work.resolve("tmp")))
                setDeploymentDir(createDirectory(work.resolve("deployment")))
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
        }

        /** Carrega a configuração de `config/application.toml`, no diretório de trabalho já fixado. */
        fun loadConfig(): AppConfig {
            val configDir = ShoppingConfig.configDir ?: throw IllegalStateException("Diretório de trabalho não informado")
            val config = AppConfig.load(configDir.resolve(CONFIG_FILE_NAME))
            setJwtSecret(config.get("security.jwt.secret"))
            setRefreshTokenTtlDays(config.getInt("security.refresh.token.ttl.days", 7))
            return config
        }

        private fun createDirectory(dir: Path): Path {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir)
            }
            return dir
        }
    }
}
