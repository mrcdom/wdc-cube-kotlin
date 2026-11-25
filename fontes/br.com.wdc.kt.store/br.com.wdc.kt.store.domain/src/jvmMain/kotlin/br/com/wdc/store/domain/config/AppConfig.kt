package br.com.wdc.store.domain.config

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

    /**
     * A list of strings: `["a", "b"]` on one line (a value that is not between brackets is a list of one). Empty when
     * the key is missing.
     */
    fun getList(key: String): List<String> = parseList(properties[key])

    fun withOverride(key: String, value: String): AppConfig {
        val copy = LinkedHashMap(properties)
        copy[key] = value
        return AppConfig(copy)
    }

    companion object {
        private val LOG = Log.getLogger("AppConfig")

        /** Reads the given configuration file; if it does not exist, everything takes its default. */
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

        /** A configuration without a file: everything at its default. */
        fun empty(): AppConfig = AppConfig(emptyMap())

        internal fun parseList(value: String?): List<String> {
            val text = value?.trim() ?: return emptyList()
            val items = if (text.startsWith("[") && text.endsWith("]")) text.substring(1, text.length - 1).split(",") else listOf(text)
            return items.map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
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
