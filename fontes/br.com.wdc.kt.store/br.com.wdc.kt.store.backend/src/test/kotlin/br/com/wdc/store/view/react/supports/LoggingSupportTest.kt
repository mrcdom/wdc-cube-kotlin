package br.com.wdc.store.view.react.supports

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
        // back to what Logback would do by itself, so the other tests are not affected
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
                <file>${'$'}{store.log.dir}/probe.log</file>
                <encoder><pattern>%level %logger - %msg%n</pattern></encoder>
              </appender>
              <root level="WARN"><appender-ref ref="FILE"/></root>
            </configuration>
            """.trimIndent(),
        )

        assertTrue(LoggingSupport.configure(config, log))
        val logger = LoggerFactory.getLogger("probe")
        logger.info("below the configured level")
        logger.warn("written to the file")

        val written = Files.readString(log.resolve("probe.log"))
        assertTrue("WARN probe - written to the file" in written, written)
        assertFalse("below the configured level" in written, written)
    }
}
