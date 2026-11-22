package br.com.wdc.shopping.view.react.supports

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.util.StatusPrinter2
import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.LoggerFactory

/**
 * Configures logging from the work directory: `config/logback.xml` says what to log and where, and the
 * `log/` folder reaches it through the `${'$'}{shopping.log.dir}` property.
 *
 * Without the file, Logback's default configuration stays (everything to the console).
 */
object LoggingSupport {

    const val CONFIG_FILE_NAME = "logback.xml"
    const val LOG_DIR_PROPERTY = "shopping.log.dir"

    /** @return `true` if logging now follows the configuration file */
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
            // what went wrong in the file is in the context status, printed below
        }
        StatusPrinter2().printInCaseOfErrorsOrWarnings(context)
        return true
    }
}
