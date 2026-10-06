package br.com.wdc.framework.jooq

import org.jooq.Field

/** Tipo lógico de um campo no mapeamento JSON. */
enum class JsonFieldType {
    /** BIGINT, INT, DOUBLE, DECIMAL — sem aspas no JSON. */
    NUMBER,

    /** VARCHAR, CHAR, enum — com aspas e escape no JSON. */
    STRING,

    /** BOOLEAN — true/false no JSON. */
    BOOLEAN,

    /** TIMESTAMP, TIMESTAMPTZ — serializado como string ISO-8601. */
    DATETIME,

    /** BINARY/BLOB — serializado como string Base64. */
    BINARY,

    /** Já é JSON válido (subconsultas de relações). Incluído sem formatação adicional. */
    RAW_JSON,
}

/**
 * Entrada de campo para montagem de um objeto JSON pelo [JsonDialect].
 *
 * @property key   nome da chave JSON
 * @property field campo jOOQ cru (não pré-formatado)
 * @property type  tipo lógico para serialização
 */
data class JsonFieldEntry(val key: String, val field: Field<*>, val type: JsonFieldType)
