package br.com.wdc.framework.jooq

import br.com.wdc.framework.jooq.dialect.H2JsonDialect
import br.com.wdc.framework.jooq.dialect.PostgresJsonDialect
import java.sql.Connection
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.InsertReturningStep
import org.jooq.Table
import org.jooq.SQLDialect
import org.jooq.SortField

/**
 * Geração de JSON em SQL nativo, por banco de dados.
 *
 * Os campos entram sempre como **placeholders de template** (`{N}`) com o `QueryPart` correspondente, nunca
 * como texto concatenado — é o que preserva os bind values das subconsultas filtradas.
 */
interface JsonDialect {

    /** Campo SQL que produz um objeto JSON (como texto) a partir das entradas. */
    fun jsonObject(entries: List<JsonFieldEntry>): Field<String>

    /** Agregação que produz um array JSON a partir de várias linhas — usada nas relações 1:N. */
    fun jsonArrayAgg(element: Field<String>): Field<String>

    /**
     * Idem, com ordem definida **dentro** da agregação.
     *
     * A ordem tem de entrar aqui, e não como `ORDER BY` da consulta: a coleção sai de uma subconsulta
     * correlacionada com a linha do pai, e envolvê-la numa tabela derivada — onde caberia um `ORDER BY` — põe
     * a correlação fora de alcance.
     *
     * O padrão devolve a agregação **sem ordem**; quem chama consulta [supportsOrderedAggregation] antes.
     */
    fun jsonArrayAgg(element: Field<String>, order: List<SortField<*>>?): Field<String> = jsonArrayAgg(element)

    /** Se este dialeto sabe ordenar dentro da agregação. */
    fun supportsOrderedAggregation(): Boolean = false

    /**
     * Executa [insert] e devolve [row] calculado sobre a linha inserida, ou `null` se nenhuma linha entrou (conflito
     * descartado). Em [row], as colunas são as de [inserted], a tabela sob o alias da leitura.
     *
     * @param writtenKey os valores gravados da chave primária, pelo nome da coluna; o que faltar é gerado pelo banco
     */
    fun insertAndSelect(
        dsl: DSLContext,
        insert: InsertReturningStep<*>,
        inserted: Table<*>,
        alias: String,
        row: Field<String>,
        writtenKey: Map<String, Any?>,
    ): String? = throw UnsupportedOperationException("o dialeto ${dsl.dialect()} não sabe ler de volta a linha inserida")

    /** Preparação do banco, uma vez no bootstrap (ex.: o H2 registra a função `TO_BASE64`). */
    fun initialize(connection: Connection) {
        // nada a fazer por padrão
    }

    companion object {
        /**
         * O dialeto JSON do banco informado.
         *
         * @throws UnsupportedOperationException para banco sem dialeto — só H2 e PostgreSQL são suportados e
         *         testados; adivinhar a sintaxe de outro produziria SQL errado ou, pior, JSON truncado
         */
        fun of(dialect: SQLDialect): JsonDialect = when (dialect.family()) {
            SQLDialect.H2 -> H2JsonDialect
            SQLDialect.POSTGRES -> PostgresJsonDialect
            else -> throw UnsupportedOperationException("sem JsonDialect para ${dialect.family()}; suportados: H2, POSTGRES")
        }
    }
}
