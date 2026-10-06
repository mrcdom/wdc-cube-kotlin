package br.com.wdc.shopping.domain.config

import br.com.wdc.framework.commons.log.Log
import java.nio.file.Files
import java.nio.file.Path

class AppConfig private constructor(
    private val properties: Map<String, String>,
) {

    fun get(key: String): String? = properties[key]

    fun get(key: String, defaultValue: String): String = properties[key] ?: defaultValue

    fun getInt(key: String, defaultValue: Int): Int {
        val value = properties[key] ?: return defaultValue
        return try {
            value.toInt()
        } catch (_: NumberFormatException) {
            LOG.warn("Invalid integer value for key '{}': '{}'", key, value)
            defaultValue
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val value = properties[key] ?: return defaultValue
        return value.equals("true", ignoreCase = true)
    }

    fun withOverride(key: String, value: String): AppConfig {
        val copy = LinkedHashMap(properties)
        copy[key] = value
        return AppConfig(copy)
    }

    companion object {
        private val LOG = Log.getLogger("AppConfig")

        /** Lê o arquivo de configuração informado; se ele não existir, vale tudo no padrão. */
        fun load(configFile: Path): AppConfig {
            if (Files.exists(configFile)) {
                LOG.info("Loading configuration from {}", configFile.toAbsolutePath())
                try {
                    return AppConfig(parseToml(Files.readString(configFile)))
                } catch (e: java.io.IOException) {
                    LOG.warn("Failed to read config file {}: {}", configFile, e.message)
                }
            } else {
                LOG.info("No config file found at {}, using defaults", configFile.toAbsolutePath())
            }
            return AppConfig(emptyMap())
        }

        /** Uma configuração sem arquivo: tudo no padrão. */
        fun empty(): AppConfig = AppConfig(emptyMap())

        internal fun parseToml(content: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            var currentSection = ""

            for (rawLine in content.split("\n")) {
                val line = rawLine.trim()

                if (line.isEmpty() || line.startsWith("#")) continue

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length - 1).trim()
                    continue
                }

                val eqIdx = line.indexOf('=')
                if (eqIdx <= 0) continue

                val key = line.substring(0, eqIdx).trim()
                var value = line.substring(eqIdx + 1).trim()

                if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length - 1)
                }

                val fullKey = if (currentSection.isEmpty()) key else "$currentSection.$key"
                result[fullKey] = value
            }

            return result
        }
    }
}
