package br.com.wdc.framework.jooq

import br.com.wdc.framework.domain.projection.HasCriteria
import br.com.wdc.framework.domain.projection.HasSlice
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Instant
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record1
import org.jooq.SelectJoinStep
import org.jooq.SelectQuery
import org.jooq.SortField
import org.jooq.Table
import org.jooq.impl.DSL

/**
 * Mapeamento declarativo bean ↔ tabela jOOQ, com projeção em JSON.
 *
 * Gera `SELECT <objeto JSON> FROM <tabela> WHERE …` — **uma coluna de texto por linha**, com o objeto
 * completo, inclusive as relações aninhadas, que entram como subconsulta escalar correlacionada (sem N+1).
 *
 * - **Projeção:** o bean de projeção diz o que trazer — campo não-nulo = "inclua esta coluna". O id não é
 *   forçado aqui; quem o garante é o repositório.
 * - **Leitura sem reflexão:** cada campo registra como se lê do JSON; nome desconhecido, tipo inesperado ou
 *   `null` são pulados (o campo fica `null`).
 * - **[lazy]:** adia o registro das relações até o primeiro uso, o que quebra a dependência circular entre
 *   os mapeamentos de duas entidades que se referem.
 *
 * Só para SELECT — escrita é com o jOOQ direto.
 * ```
 * val QUERY: JsonQuery<User, EnUser> = JsonQueryBuilder<User, EnUser>()
 *     .setAlias("u").setBeanFactory(::User).setTableFactory(EN_USER::`as`).setDSLContextSupplier(::dsl)
 *     .addI64("id", { it.id }, { b, v -> b.id = v }, { it.ID })
 *     .addStr("name", { it.name }, { b, v -> b.name = v }, { it.NAME })
 *     .build()
 *
 * val users = QUERY.fetchToList(projection) { t, q -> q.where(t.NAME.like("A%")) }
 * ```
 *
 * @param B tipo do bean de domínio
 * @param T tipo da tabela jOOQ
 */
class JsonQueryBuilder<B : Any, T : Table<*>> {

    private var tableName: String = "t"
    private var beanFactory: (() -> B)? = null
    private var tableFactory: ((String) -> T)? = null
    private var dslContextSupplier: (() -> DSLContext)? = null

    /** Marca os campos escalares num bean de projeção. */
    private val projectionFillers = ArrayList<(B) -> Unit>()

    /** Acrescenta ao objeto JSON os campos que a projeção pede. */
    private val fieldProjections = ArrayList<(fields: MutableList<JsonFieldEntry>, ctx: QueryContext, bean: B, table: T) -> Unit>()

    /** Como ler cada campo do JSON. */
    private val fieldReaders = HashMap<String, (bean: B, reader: JsonReader) -> Unit>()

    /** Como perguntar a um bean de projeção se cada campo foi pedido — escalar ou relação. */
    private val fieldPresence = LinkedHashMap<String, (B) -> Any?>()

    /** Tradução do `OrderBy` do critério desta entidade; `null` quando a entidade não a registrou. */
    private var ordering: ((T, Any?) -> List<SortField<*>>?)? = null

    private val lazyInits = ArrayList<(JsonQueryBuilder<B, T>) -> Unit>()
    private val lazyLock = ReentrantLock()

    @Volatile
    private var lazyDone = false

    // :: Configuração

    /** Alias base da tabela principal (ex.: `"u"`). */
    fun setAlias(name: String) = apply { tableName = name }

    /** Fábrica do bean de domínio (ex.: `::User`). */
    fun setBeanFactory(beanFactory: () -> B) = apply { this.beanFactory = beanFactory }

    /** Fábrica da tabela com alias (ex.: `` EN_USER::`as` ``). */
    fun setTableFactory(tableFactory: (alias: String) -> T) = apply { this.tableFactory = tableFactory }

    /**
     * Fornecedor do [DSLContext], resolvido **a cada execução** — cada módulo injeta o seu, e o framework não
     * depende de holder global.
     */
    fun setDSLContextSupplier(dslContextSupplier: () -> DSLContext) = apply { this.dslContextSupplier = dslContextSupplier }

    /**
     * Como traduzir o `OrderBy` do critério desta entidade em `ORDER BY`.
     *
     * Registrando-a aqui, a coleção filha passa a poder ser ordenada pelo mesmo vocabulário da consulta de
     * raiz. Recebe o critério como `Any?` porque a coleção filha carrega o critério da **sua** entidade, que
     * o chamador não conhece estaticamente: a função confere o tipo e devolve lista vazia se não reconhecer.
     */
    fun setOrdering(ordering: (table: T, criteria: Any?) -> List<SortField<*>>?) = apply { this.ordering = ordering }

    // :: Campos escalares

    /** Campo `Long` (BIGINT). */
    fun addI64(fn: String, getter: (B) -> Long?, setter: (B, Long) -> Unit, column: (T) -> Field<out Number?>) =
        scalar(fn, getter, setter, 0L, JsonFieldType.NUMBER, column, { it == JsonToken.NUMBER }) { it.nextLong() }

    /** Campo `Int` (INT). */
    fun addI32(fn: String, getter: (B) -> Int?, setter: (B, Int) -> Unit, column: (T) -> Field<out Number?>) =
        scalar(fn, getter, setter, 0, JsonFieldType.NUMBER, column, { it == JsonToken.NUMBER }) { it.nextInt() }

    /** Campo `Double` (DOUBLE PRECISION / NUMERIC). */
    fun addF64(fn: String, getter: (B) -> Double?, setter: (B, Double) -> Unit, column: (T) -> Field<out Number?>) =
        scalar(fn, getter, setter, 0.0, JsonFieldType.NUMBER, column, { it == JsonToken.NUMBER }) { it.nextDouble() }

    /** Campo `BigDecimal` (DECIMAL / NUMERIC). */
    fun addDec(fn: String, getter: (B) -> BigDecimal?, setter: (B, BigDecimal) -> Unit, column: (T) -> Field<out Number?>) =
        scalar(fn, getter, setter, BigDecimal.ZERO, JsonFieldType.NUMBER, column, { it == JsonToken.NUMBER }) {
            BigDecimal(it.nextString())
        }

    /** Campo `String` (VARCHAR). */
    fun addStr(fn: String, getter: (B) -> String?, setter: (B, String) -> Unit, column: (T) -> Field<out String?>) =
        scalar(fn, getter, setter, "", JsonFieldType.STRING, column, { it != JsonToken.NULL }) { it.nextString() }

    /**
     * Campo enum (VARCHAR no banco, enum na aplicação).
     *
     * @param sentinel constante qualquer do enum, usada para marcar o campo na projeção
     * @param parser   texto → constante; deve devolver `null` para valor desconhecido, nunca lançar
     */
    fun <E : Enum<E>> addEnm(
        fn: String,
        getter: (B) -> E?,
        setter: (B, E?) -> Unit,
        sentinel: E,
        parser: (String) -> E?,
        column: (T) -> Field<out String?>,
    ): JsonQueryBuilder<B, T> {
        projectionFillers.add { setter(it, sentinel) }
        register(fn, getter, JsonFieldType.STRING, column)
        fieldReaders[fn] = { bean, reader ->
            if (reader.peek() != JsonToken.NULL) setter(bean, parser(reader.nextString())) else reader.skipValue()
        }
        return this
    }

    /** Campo `Boolean`. */
    fun addBit(fn: String, getter: (B) -> Boolean?, setter: (B, Boolean) -> Unit, column: (T) -> Field<out Boolean?>) =
        scalar(fn, getter, setter, false, JsonFieldType.BOOLEAN, column, { it == JsonToken.BOOLEAN }) { it.nextBoolean() }

    /** Campo `OffsetDateTime` (TIMESTAMP WITH TIME ZONE). */
    fun addOdt(
        fn: String, getter: (B) -> OffsetDateTime?, setter: (B, OffsetDateTime) -> Unit,
        column: (T) -> Field<out OffsetDateTime?>,
    ) = scalar(fn, getter, setter, OffsetDateTime.MIN, JsonFieldType.DATETIME, column, { it == JsonToken.STRING }) {
        parseTimestamp(it.nextString())
    }

    /** Coluna TIMESTAMP **sem** fuso, lida como `OffsetDateTime` em UTC. */
    fun addLdt(
        fn: String, getter: (B) -> OffsetDateTime?, setter: (B, OffsetDateTime) -> Unit,
        column: (T) -> Field<out LocalDateTime?>,
    ) = scalar(fn, getter, setter, OffsetDateTime.MIN, JsonFieldType.DATETIME, column, { it == JsonToken.STRING }) {
        parseTimestamp(it.nextString())
    }

    /** Coluna TIMESTAMP WITH TIME ZONE mapeada para [Instant] no domínio. */
    fun addInstantFromOdt(
        fn: String, getter: (B) -> Instant?, setter: (B, Instant) -> Unit,
        column: (T) -> Field<out OffsetDateTime?>,
    ) = scalar(fn, getter, setter, INSTANT_SENTINEL, JsonFieldType.DATETIME, column, { it == JsonToken.STRING }) {
        parseInstant(it.nextString())
    }

    /**
     * Coluna TIMESTAMP **sem** fuso mapeada para [Instant] no domínio. Convenção: o valor da coluna está em
     * UTC, na leitura e na escrita.
     */
    fun addInstantFromLdt(
        fn: String, getter: (B) -> Instant?, setter: (B, Instant) -> Unit,
        column: (T) -> Field<out LocalDateTime?>,
    ) = scalar(fn, getter, setter, INSTANT_SENTINEL, JsonFieldType.DATETIME, column, { it == JsonToken.STRING }) {
        parseInstant(it.nextString())
    }

    /** Campo `ByteArray` (BINARY / BLOB), que trafega em Base64 no JSON. */
    fun addBin(fn: String, getter: (B) -> ByteArray?, setter: (B, ByteArray) -> Unit, column: (T) -> Field<out ByteArray?>) =
        scalar(fn, getter, setter, ByteArray(0), JsonFieldType.BINARY, column, { it == JsonToken.STRING }) {
            // decoder MIME: tolera as quebras de linha que o encode() do PostgreSQL insere
            Base64.getMimeDecoder().decode(it.nextString())
        }

    private fun <V : Any> scalar(
        fn: String,
        getter: (B) -> V?,
        setter: (B, V) -> Unit,
        sentinel: V,
        type: JsonFieldType,
        column: (T) -> Field<*>,
        accepts: (JsonToken) -> Boolean,
        read: (JsonReader) -> V,
    ): JsonQueryBuilder<B, T> {
        projectionFillers.add { setter(it, sentinel) }
        register(fn, getter, type, column)
        fieldReaders[fn] = { bean, reader ->
            if (accepts(reader.peek())) setter(bean, read(reader)) else reader.skipValue()
        }
        return this
    }

    private fun register(fn: String, getter: (B) -> Any?, type: JsonFieldType, column: (T) -> Field<*>) {
        fieldPresence[fn] = getter
        fieldProjections.add { fields, _, bean, table ->
            if (getter(bean) != null) {
                fields.add(JsonFieldEntry(fn, column(table), type))
            }
        }
    }

    // :: Lazy

    /**
     * Adia o registro de campos (tipicamente relações) até o primeiro uso, executado uma única vez. Use para
     * **todas** as relações: é o que permite dois mapeamentos se referirem um ao outro.
     */
    fun lazy(init: (JsonQueryBuilder<B, T>) -> Unit) = apply { lazyInits.add(init) }

    private fun runLazyInit() {
        if (!lazyDone) {
            lazyLock.withLock {
                if (!lazyDone) {
                    lazyInits.forEach { it(this) }
                    lazyDone = true
                }
            }
        }
    }

    // :: Relação 1:1

    /**
     * Registra um bean associado (relação 1:1), carregado por subconsulta correlacionada.
     *
     * @param childWhereClause escreve a correlação (e o critério do filho) na consulta filha
     * @param key declara **qual coluna desta tabela guarda a chave** do associado. Com ela, a projeção que
     *            não pede nada além da chave é montada a partir da própria linha, sem subconsulta — o caso
     *            corrente de `newProjection()`, que traz a associação só para carregar o id. Os nomes
     *            declarados são os do **JSON do filho**; as colunas, as **desta** tabela. `null` desliga o atalho.
     */
    fun <C : Any, U : Table<*>> addBeanField(
        fn: String,
        getter: (B) -> C?,
        setter: (B, C) -> Unit,
        childQuery: JsonQuery<C, U>,
        childWhereClause: (JsonChildQueryBuilder<T, U>) -> Unit,
        key: ((RelationKey<T>) -> Unit)? = null,
    ): JsonQueryBuilder<B, T> {
        fieldPresence[fn] = getter
        val relationKey = key?.let { RelationKey<T>().also(it) }
        fieldProjections.add { fields, ctx, bean, table ->
            val childPrjBean = getter(bean) ?: return@add
            // A chave já está nesta linha: se a projeção do associado não pede mais nada, montá-la aqui evita uma
            // subconsulta que só redescobriria a coluna da chave estrangeira.
            if (relationKey != null && !relationKey.isEmpty() && !childQuery.projectsBeyond(childPrjBean, relationKey.names())) {
                val dialect = JsonDialect.of(ctx.dsl().dialect())
                fields.add(JsonFieldEntry(fn, dialect.jsonObject(relationKey.entries(table)), JsonFieldType.RAW_JSON))
                return@add
            }
            val childBuilder = JsonChildQueryBuilder<T, U>(ctx, table)
            val clause: JsonWhereClause<U> = { tbChild, q ->
                childBuilder.bind(tbChild, q)
                childWhereClause(childBuilder)
            }
            fields.add(JsonFieldEntry(fn, DSL.field(childQuery.select(ctx, childPrjBean, clause, false)), JsonFieldType.RAW_JSON))
        }
        fieldReaders[fn] = { bean, reader ->
            if (reader.peek() == JsonToken.BEGIN_OBJECT) setter(bean, childQuery.parseJson(reader)) else reader.skipValue()
        }
        return this
    }

    // :: Coleção filha (1:N)

    /**
     * Registra uma lista de beans filhos (relação 1:N), carregada por subconsulta correlacionada agregada.
     *
     * A coleção de projeção deve ter ao menos um elemento — o primeiro dá a forma do item. Se ela for
     * `HasCriteria`, o critério chega à [childWhereClause] e dá a ordem (pelo `setOrdering` do filho); se for
     * `HasSlice`, dá o recorte.
     */
    fun <C : Any, U : Table<*>> addBeanListField(
        fn: String,
        getter: (B) -> List<C>?,
        setter: (B, MutableList<C>) -> Unit,
        childQuery: JsonQuery<C, U>,
        childWhereClause: (JsonChildQueryBuilder<T, U>) -> Unit,
    ): JsonQueryBuilder<B, T> {
        fieldPresence[fn] = getter
        fieldProjections.add { fields, ctx, bean, table ->
            childCollectionEntry(fn, getter(bean), ctx, table, childQuery, childWhereClause)?.let(fields::add)
        }
        fieldReaders[fn] = { bean, reader ->
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                val list = ArrayList<C>()
                reader.beginArray()
                while (reader.hasNext()) {
                    list.add(childQuery.parseJson(reader))
                }
                reader.endArray()
                setter(bean, list)
            } else {
                reader.skipValue()
            }
        }
        return this
    }

    /** A entrada JSON de uma coleção filha, com a ordem e o recorte que a própria coleção declara. */
    private fun <C : Any, U : Table<*>> childCollectionEntry(
        fn: String,
        childPrjBeans: Collection<C>?,
        ctx: QueryContext,
        table: T,
        childQuery: JsonQuery<C, U>,
        childWhereClause: (JsonChildQueryBuilder<T, U>) -> Unit,
    ): JsonFieldEntry? {
        val childPrjBean = childPrjBeans?.firstOrNull() ?: return null
        val childBuilder = JsonChildQueryBuilder<T, U>(ctx, table)
        val listCriteria = (childPrjBeans as? HasCriteria)?.criteria
        childBuilder.criteria = listCriteria
        val clause: JsonWhereClause<U> = { tbChild, q ->
            childBuilder.bind(tbChild, q)
            childWhereClause(childBuilder)
        }
        // Ordem e recorte vêm do que a própria coleção declara: o critério que ela carrega traz o OrderBy da
        // entidade filha, e HasSlice traz o limite e o deslocamento. Sem nenhum dos dois, a consulta sai como sempre.
        val order: ((U) -> List<SortField<*>>)? =
            if (listCriteria != null && childQuery.hasOrdering()) ({ tb -> childQuery.orderingOf(tb, listCriteria) }) else null
        val slice = childPrjBeans as? HasSlice
        return JsonFieldEntry(
            fn,
            DSL.field(childQuery.selectOrdered(ctx, childPrjBean, clause, order, slice?.limit, slice?.offset)),
            JsonFieldType.RAW_JSON,
        )
    }

    // :: Build

    /** Constrói o mapeamento pronto para execução. */
    fun build(): JsonQuery<B, T> = Built()

    private inner class Built : JsonQuery<B, T> {

        private fun beans() = beanFactory ?: error("JsonQueryBuilder: setBeanFactory(...) não foi configurado")
        private fun tables() = tableFactory ?: error("JsonQueryBuilder: setTableFactory(...) não foi configurado")
        private fun dsl() = (dslContextSupplier ?: error("JsonQueryBuilder: setDSLContextSupplier(...) não foi configurado"))()

        override fun newBean(): B = beans()()

        override fun newProjectionBean(adapt: ((B) -> Unit)?): B {
            val bean = beans()()
            projectionFillers.forEach { it(bean) }
            adapt?.invoke(bean)
            return bean
        }

        override fun newTable(alias: String): T = tables()(alias)

        override fun select(prjBean: B?, whereClause: JsonWhereClause<T>): SelectQuery<Record1<String>> =
            select(QueryContext(dsl()), prjBean, whereClause, false)

        override fun select(
            ctx: QueryContext, prjBean: B?, whereClause: JsonWhereClause<T>, isAgg: Boolean,
        ): SelectQuery<Record1<String>> {
            runLazyInit()
            val tbRoot = tables()(tableName + ctx.nextUniqueInt())
            val row = projection(ctx, tbRoot, prjBean)
            val field = if (isAgg) JsonDialect.of(ctx.dsl().dialect()).jsonArrayAgg(row) else row
            val joinStep = ctx.dsl().select(field.`as`(tableName + "_json")).from(tbRoot)
            whereClause(tbRoot, joinStep)
            return joinStep.query
        }

        override fun projection(ctx: QueryContext, table: T, prjBean: B?): Field<String> {
            runLazyInit()
            val entries = ArrayList<JsonFieldEntry>()
            val bean = prjBean ?: newProjectionBean()
            fieldProjections.forEach { it(entries, ctx, bean, table) }
            return JsonDialect.of(ctx.dsl().dialect()).jsonObject(entries)
        }

        override fun hasOrdering(): Boolean = ordering != null

        override fun orderingOf(table: T, criteria: Any?): List<SortField<*>> = ordering?.invoke(table, criteria) ?: emptyList()

        override fun selectOrdered(
            ctx: QueryContext,
            prjBean: B?,
            whereClause: JsonWhereClause<T>,
            order: ((T) -> List<SortField<*>>)?,
            limit: Int?,
            offset: Int?,
        ): SelectQuery<Record1<String>> {
            runLazyInit()
            if (order == null && limit == null && offset == null) {
                // Sem ordem nem recorte, a forma de sempre: quem não pede nada continua vendo o mesmo SQL e o mesmo plano.
                return select(ctx, prjBean, whereClause, true)
            }
            val tbRoot = tables()(tableName + ctx.nextUniqueInt())
            val dialect = JsonDialect.of(ctx.dsl().dialect())
            val sort = order?.invoke(tbRoot)
            if (!sort.isNullOrEmpty() && !dialect.supportsOrderedAggregation()) {
                // Recusa em vez de devolver a coleção fora da ordem pedida: um resultado ordenado errado passa por certo.
                throw UnsupportedOperationException(
                    "o dialeto ${ctx.dsl().dialect()} não sabe ordenar dentro da agregação; ordene a coleção do lado da aplicação"
                )
            }
            // A ordem entra DENTRO da agregação. Envolver esta consulta numa tabela derivada — onde caberia um
            // ORDER BY comum — poria a correlação com a linha do pai fora de alcance: derivada não enxerga o
            // escopo externo, e o banco responde "column pai.id not found".
            val agg = ctx.dsl()
                .select(dialect.jsonArrayAgg(projection(ctx, tbRoot, prjBean), sort).`as`(tableName + "_json"))
                .from(tbRoot)
            whereClause(tbRoot, agg)
            val aggQuery = agg.query

            // O recorte não cabe na agregação, e pelo mesmo motivo não cabe numa derivada. Sai como um IN sobre a
            // chave primária: uma subconsulta correlacionada — essa, sim, enxerga o pai — que repete o mesmo
            // filtro, ordena e corta, devolvendo as chaves das linhas que ficam.
            if (limit != null || offset != null) {
                val tbSlice = tables()(tableName + ctx.nextUniqueInt())
                val keep = ctx.dsl().select(primaryKeyOf(tbSlice)).from(tbSlice)
                // A cláusula é tipada na coluna da projeção porque é assim que o resto da API a usa; aqui ela só
                // acrescenta condições, e o tipo da coluna selecionada não a alcança.
                @Suppress("UNCHECKED_CAST")
                whereClause(tbSlice, keep as SelectJoinStep<Record1<String>>)
                val keepQuery = keep.query
                order?.invoke(tbSlice)?.takeIf { it.isNotEmpty() }?.let { keepQuery.addOrderBy(it) }
                limit?.let { keepQuery.addLimit(it) }
                offset?.let { keepQuery.addOffset(it) }
                aggQuery.addConditions(primaryKeyOf(tbRoot).`in`(keepQuery))
            }
            return aggQuery
        }

        override fun projectsBeyond(prjBean: B?, fields: Collection<String>): Boolean {
            runLazyInit()
            if (prjBean == null) {
                return false
            }
            return fieldPresence.any { (name, present) -> name !in fields && present(prjBean) != null }
        }

        override fun parseJson(json: String): B {
            try {
                JsonReader(StringReader(json)).use { reader ->
                    reader.setStrictness(Strictness.LENIENT)
                    return parseJson(reader)
                }
            } catch (e: IOException) {
                throw IllegalStateException("JSON parse error", e)
            }
        }

        override fun parseJson(reader: JsonReader): B {
            runLazyInit()
            val bean = beans()()
            try {
                reader.beginObject()
                while (reader.hasNext()) {
                    val read = fieldReaders[reader.nextName()]
                    if (read != null) read(bean, reader) else reader.skipValue()
                }
                reader.endObject()
            } catch (e: IOException) {
                throw IllegalStateException("JSON parse error", e)
            }
            return bean
        }

        override fun fetchOne(prjBean: B?, whereClause: JsonWhereClause<T>): B? =
            select(prjBean, whereClause).fetchOne()?.value1()?.let(::parseJson)

        override fun fetchToList(prjBean: B?, whereClause: JsonWhereClause<T>): List<B> =
            select(prjBean, whereClause).fetch().mapNotNull { rec -> rec.value1()?.let(::parseJson) }
    }

    private companion object {
        val INSTANT_SENTINEL: Instant = Instant.fromEpochMilliseconds(0L)

        /**
         * Lê o que o banco põe no JSON para uma coluna de data/hora: ISO-8601 com fuso, ou a forma sem fuso
         * (`2024-01-15T10:30:00` ou `2024-01-15 10:30:00[.n]`), assumida em UTC.
         */
        fun parseTimestamp(text: String): OffsetDateTime =
            try {
                OffsetDateTime.parse(text)
            } catch (_: DateTimeParseException) {
                LocalDateTime.parse(text.replace(' ', 'T')).atOffset(ZoneOffset.UTC)
            }

        fun parseInstant(text: String): Instant {
            val instant = parseTimestamp(text).toInstant()
            return Instant.fromEpochSeconds(instant.epochSecond, instant.nano)
        }

        /**
         * A chave primária da tabela, resolvida na instância informada (que tem alias próprio).
         *
         * Vem da tabela gerada pelo jOOQ, e não de declaração. Serve ao recorte de coleção filha, que precisa
         * de algo por onde dizer "estas linhas, e não as outras".
         *
         * @throws IllegalStateException se a tabela não declara chave primária, ou se ela é composta — o `IN`
         *         de uma coluna só não a exprime, e SQL aproximado devolveria a coleção errada em silêncio
         */
        fun primaryKeyOf(table: Table<*>): Field<Any?> {
            val pk = table.primaryKey
            if (pk == null || pk.fields.isEmpty()) {
                throw IllegalStateException("recorte de coleção exige chave primária, e ${table.name} não declara nenhuma")
            }
            if (pk.fields.size > 1) {
                throw IllegalStateException(
                    "recorte de coleção não suporta chave primária composta: ${table.name} tem ${pk.fields.size} colunas na chave"
                )
            }
            val column = pk.fields[0]
            @Suppress("UNCHECKED_CAST")
            return (table.field(column) ?: column) as Field<Any?>
        }
    }
}
