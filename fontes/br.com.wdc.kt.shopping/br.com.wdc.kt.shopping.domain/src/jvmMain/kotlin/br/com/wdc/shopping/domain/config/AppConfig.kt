package br.com.wdc.shopping.domain.config

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.getLogger
import java.nio.file.Files
import java.nio.file.Paths

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
        private val LOG = Log.getLogger(AppConfig::class.java)

        private const val CONFIG_FILE_PROPERTY = "shopping.config.file"
        private const val DEFAULT_CONFIG_PATH = "work/config/application.toml"

        fun load(): AppConfig {
            val configPath = resolveConfigPath()
            if (Files.exists(configPath)) {
                LOG.info("Loading configuration from {}", configPath.toAbsolutePath())
                try {
                    val content = Files.readString(configPath)
                    val props = parseToml(content)
                    return AppConfig(props)
                } catch (e: java.io.IOException) {
                    LOG.warn("Failed to read config file {}: {}", configPath, e.message)
                }
            } else {
                LOG.info("No config file found at {}, using defaults", configPath.toAbsolutePath())
            }
            return AppConfig(emptyMap())
        }

        private fun resolveConfigPath(): java.nio.file.Path {
            val configured = System.getProperty(CONFIG_FILE_PROPERTY)
            return if (!configured.isNullOrBlank()) Paths.get(configured)
            else Paths.get(DEFAULT_CONFIG_PATH)
        }

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
