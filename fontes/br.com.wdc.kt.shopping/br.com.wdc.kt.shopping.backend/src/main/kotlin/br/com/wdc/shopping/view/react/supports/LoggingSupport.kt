package br.com.wdc.shopping.view.react.supports

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.util.StatusPrinter2
import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.LoggerFactory

/**
 * Configura o log a partir do diretório de trabalho: `config/logback.xml` diz o que registrar e onde, e a
 * pasta `log/` chega a ele pela propriedade `${'$'}{shopping.log.dir}`.
 *
 * Sem o arquivo, fica a configuração padrão do Logback (tudo no console).
 */
object LoggingSupport {

    const val CONFIG_FILE_NAME = "logback.xml"
    const val LOG_DIR_PROPERTY = "shopping.log.dir"

    /** @return `true` se o log passou a seguir o arquivo de configuração */
    fun configure(configDir: Path, logDir: Path): Boolean {
        val configFile = configDir.resolve(CONFIG_FILE_NAME)
        if (!Files.isRegularFile(configFile)) {
            return false
        }
        val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return false
        System.setProperty(LOG_DIR_PROPERTY, logDir.toAbsolutePath().toString())
        context.reset()
        try {
            JoranConfigurator().apply { setContext(context) }.doConfigure(configFile.toFile())
        } catch (e: JoranException) {
            // o que houve de errado no arquivo está no status do contexto, impresso abaixo
        }
        StatusPrinter2().printInCaseOfErrorsOrWarnings(context)
        return true
    }
}
