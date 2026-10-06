package br.com.wdc.shopping.view.react.supports

import ch.qos.logback.classic.LoggerContext
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory

class LoggingSupportTest {

    @AfterTest
    fun restoreTheDefaultLogging() {
        // volta ao que o Logback faria sozinho, para não afetar os outros testes
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        context.reset()
        ch.qos.logback.classic.util.ContextInitializer(context).autoConfig()
        System.clearProperty(LoggingSupport.LOG_DIR_PROPERTY)
    }

    @Test
    fun withoutTheFile_nothingChanges() {
        val config = Files.createTempDirectory("config")
        assertFalse(LoggingSupport.configure(config, Files.createTempDirectory("log")))
    }

    @Test
    fun theFileInConfig_decidesWhatIsLogged_andTheLogFolderReceivesIt() {
        val config = Files.createTempDirectory("config")
        val log = Files.createTempDirectory("log")
        Files.writeString(
            config.resolve("logback.xml"),
            """
            <configuration>
              <appender name="FILE" class="ch.qos.logback.core.FileAppender">
                <file>${'$'}{shopping.log.dir}/probe.log</file>
                <encoder><pattern>%level %logger - %msg%n</pattern></encoder>
              </appender>
              <root level="WARN"><appender-ref ref="FILE"/></root>
            </configuration>
            """.trimIndent(),
        )

        assertTrue(LoggingSupport.configure(config, log))
        val logger = LoggerFactory.getLogger("probe")
        logger.info("abaixo do nível configurado")
        logger.warn("registrado no arquivo")

        val written = Files.readString(log.resolve("probe.log"))
        assertTrue("WARN probe - registrado no arquivo" in written, written)
        assertFalse("abaixo do nível" in written, written)
    }
}
