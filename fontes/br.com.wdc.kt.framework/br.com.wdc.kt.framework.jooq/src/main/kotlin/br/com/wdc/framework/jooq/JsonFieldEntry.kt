package br.com.wdc.framework.jooq

import org.jooq.Field

/** Logical type of a field in the JSON mapping. */
enum class JsonFieldType {
    /** BIGINT, INT, DOUBLE, DECIMAL — unquoted in JSON. */
    NUMBER,

    /** VARCHAR, CHAR, enum — quoted and escaped in JSON. */
    STRING,

    /** BOOLEAN — true/false in JSON. */
    BOOLEAN,

    /** TIMESTAMP, TIMESTAMPTZ — serialized as an ISO-8601 string. */
    DATETIME,

    /** BINARY/BLOB — serialized as a Base64 string. */
    BINARY,

    /** Already valid JSON (relation subqueries). Included without further formatting. */
    RAW_JSON,
}

/**
 * Field entry for building a JSON object through the [JsonDialect].
 *
 * @property key   JSON key name
 * @property field raw jOOQ field (not preformatted)
 * @property type  logical type for serialization
 */
data class JsonFieldEntry(val key: String, val field: Field<*>, val type: JsonFieldType)
