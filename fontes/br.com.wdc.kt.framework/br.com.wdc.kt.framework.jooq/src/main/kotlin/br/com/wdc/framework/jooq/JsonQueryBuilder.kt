package br.com.wdc.framework.jooq

import br.com.wdc.framework.domain.projection.HasCriteria
import br.com.wdc.framework.domain.projection.HasSlice
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.jooq.InsertReturningStep
import java.util.UUID
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
 * Declarative bean ↔ jOOQ table mapping, with JSON projection.
 *
 * Generates `SELECT <JSON object> FROM <table> WHERE …` — **one text column per row**, with the complete
 * object, including nested relations, which go in as a correlated scalar subquery (no N+1).
 *
 * - **Projection:** the projection bean says what to bring — non-null field = "include this column". The id is
 *   not forced here; the repository guarantees it.
 * - **Reflection-free reading:** each field registers how it is read from JSON; an unknown name, unexpected type
 *   or `null` is skipped (the field stays `null`).
 * - **[lazy]:** defers registering relations until first use, which breaks the circular dependency between
 *   the mappings of two entities that refer to each other.
 *
 * For writing, [JsonQuery.insert] builds the `INSERT` from the same fields ([JsonQuery.fields]) and reads the
 * inserted row back through this projection. `UPDATE` and `DELETE` use jOOQ directly.
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
 * @param B domain bean type
 * @param T jOOQ table type
 */
class JsonQueryBuilder<B : Any, T : Table<*>> {

    private var tableName: String = "t"
    private var beanFactory: (() -> B)? = null
    private var tableFactory: ((String) -> T)? = null
    private var dslContextSupplier: (() -> DSLContext)? = null

    /** Marks the scalar fields on a projection bean. */
    private val projectionFillers = ArrayList<(B) -> Unit>()

    /** Adds to the JSON object the fields the projection asks for. */
    private val fieldProjections = ArrayList<(fields: MutableList<JsonFieldEntry>, ctx: QueryContext, bean: B, table: T) -> Unit>()

    /** How to read each field from JSON. */
    private val fieldReaders = HashMap<String, (bean: B, reader: JsonReader) -> Unit>()

    /** The fields the insert sees ([JsonQuery.fields]), in declaration order. */
    private val queryFields = ArrayList<JsonQueryField<B, T>>()

    /** How to ask a projection bean whether each field was requested — scalar or relation. */
    private val fieldPresence = LinkedHashMap<String, (B) -> Any?>()

    /** Translation of this entity's criterion `OrderBy`; `null` when the entity did not register one. */
    private var ordering: ((T, Any?) -> List<SortField<*>>?)? = null

    private val lazyInits = ArrayList<(JsonQueryBuilder<B, T>) -> Unit>()
    private val lazyLock = ReentrantLock()

    @Volatile
    private var lazyDone = false

    // :: Configuration

    /** Base alias of the main table (e.g. `"u"`). */
    fun setAlias(name: String) = apply { tableName = name }

    /** Domain bean factory (e.g. `::User`). */
    fun setBeanFactory(beanFactory: () -> B) = apply { this.beanFactory = beanFactory }

    /** Aliased table factory (e.g. `` EN_USER::`as` ``). */
    fun setTableFactory(tableFactory: (alias: String) -> T) = apply { this.tableFactory = tableFactory }

    /**
     * [DSLContext] supplier, resolved **on each execution** — each module injects its own, and the framework
     * does not depend on a global holder.
     */
    fun setDSLContextSupplier(dslContextSupplier: () -> DSLContext) = apply { this.dslContextSupplier = dslContextSupplier }

    /**
     * How to translate this entity's criterion `OrderBy` into `ORDER BY`.
     *
     * Registered here, the child collection can be ordered with the same vocabulary as the root query. It
     * receives the criterion as `Any?` because the child collection carries the criterion of **its** entity,
     * which the caller does not know statically: the function checks the type and returns an empty list if it
     * does not recognize it.
     */
    fun setOrdering(ordering: (table: T, criteria: Any?) -> List<SortField<*>>?) = apply { this.ordering = ordering }

    // :: Scalar fields

    /** `Long` field (BIGINT). */
    fun addI64(fn: String, getter: (B) -> Long?, setter: (B, Long) -> Unit, column: (T) -> Field<out Number?>) =
        scalar(fn, getter, setter, 0L, JsonFieldType.NUMBER, column, { it == JsonToken.NUMBER }) { it.nextLong() }

    /** `Int` field (INT). */
    fun addI32(fn: String, getter: (B) -> Int?, setter: (B, Int) -> Unit, column: (T) -> Field<out Number?>) =
        scalar(fn, getter, setter, 0, JsonFieldType.NUMBER, column, { it == JsonToken.NUMBER }) { it.nextInt() }

    /** `Double` field (DOUBLE PRECISION / NUMERIC). */
    fun addF64(fn: String, getter: (B) -> Double?, setter: (B, Double) -> Unit, column: (T) -> Field<out Number?>) =
        scalar(fn, getter, setter, 0.0, JsonFieldType.NUMBER, column, { it == JsonToken.NUMBER }, toColumn = { BigDecimal.valueOf(it) }) {
            it.nextDouble()
        }

    /** `BigDecimal` field (DECIMAL / NUMERIC). */
    fun addDec(fn: String, getter: (B) -> BigDecimal?, setter: (B, BigDecimal) -> Unit, column: (T) -> Field<out Number?>) =
        scalar(fn, getter, setter, BigDecimal.ZERO, JsonFieldType.NUMBER, column, { it == JsonToken.NUMBER }) {
            BigDecimal(it.nextString())
        }

    /** `String` field (VARCHAR). */
    fun addStr(fn: String, getter: (B) -> String?, setter: (B, String) -> Unit, column: (T) -> Field<out String?>) =
        scalar(fn, getter, setter, "", JsonFieldType.STRING, column, { it != JsonToken.NULL }) { it.nextString() }

    /**
     * Enum field (VARCHAR in the database, enum in the application).
     *
     * @param sentinel any constant of the enum, used to mark the field in the projection
     * @param parser   text → constant; must return `null` for an unknown value, never throw
     * @param toColumn constant → text written to the column (the inverse of [parser]); by default, the constant's name.
     *                 It is what [JsonQuery.insert] writes and what [JsonQueryField.toColumn] returns
     */
    fun <E : Enum<E>> addEnm(
        fn: String,
        getter: (B) -> E?,
        setter: (B, E?) -> Unit,
        sentinel: E,
        parser: (String) -> E?,
        column: (T) -> Field<out String?>,
        toColumn: (E) -> String = { it.name },
    ): JsonQueryBuilder<B, T> {
        projectionFillers.add { setter(it, sentinel) }
        register(fn, getter, JsonFieldType.STRING, column)
        fieldReaders[fn] = { bean, reader ->
            if (reader.peek() != JsonToken.NULL) setter(bean, parser(reader.nextString())) else reader.skipValue()
        }
        @Suppress("UNCHECKED_CAST")
        queryFields += JsonQueryField(
            fn, column, getter, { b, v -> if (v != null) setter(b, v as E) }, { getter(it) != null },
            { setter(it, sentinel) }, { toColumn(it as E) },
        )
        return this
    }

    /** `Boolean` field. */
    fun addBit(fn: String, getter: (B) -> Boolean?, setter: (B, Boolean) -> Unit, column: (T) -> Field<out Boolean?>) =
        scalar(fn, getter, setter, false, JsonFieldType.BOOLEAN, column, { it == JsonToken.BOOLEAN }) { it.nextBoolean() }

    /** `OffsetDateTime` field (TIMESTAMP WITH TIME ZONE). */
    fun addOdt(
        fn: String, getter: (B) -> OffsetDateTime?, setter: (B, OffsetDateTime) -> Unit,
        column: (T) -> Field<out OffsetDateTime?>,
    ) = scalar(fn, getter, setter, OffsetDateTime.MIN, JsonFieldType.DATETIME, column, { it == JsonToken.STRING }) {
        parseTimestamp(it.nextString())
    }

    /** TIMESTAMP column **without** time zone, read as `OffsetDateTime` in UTC (and written in UTC). */
    fun addLdt(
        fn: String, getter: (B) -> OffsetDateTime?, setter: (B, OffsetDateTime) -> Unit,
        column: (T) -> Field<out LocalDateTime?>,
    ) = scalar(
        fn, getter, setter, OffsetDateTime.MIN, JsonFieldType.DATETIME, column, { it == JsonToken.STRING },
        toColumn = { it.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() },
    ) {
        parseTimestamp(it.nextString())
    }

    /**
     * DATE column (day, no time) mapped to [LocalDate]. The database writes it in JSON as ISO text (`2024-01-15`);
     * whatever comes after the day (a time, if the dialect adds one) is ignored.
     */
    fun addDay(fn: String, getter: (B) -> LocalDate?, setter: (B, LocalDate) -> Unit, column: (T) -> Field<out LocalDate?>) =
        scalar(fn, getter, setter, LOCAL_DATE_SENTINEL, JsonFieldType.DATETIME, column, { it == JsonToken.STRING }) {
            LocalDate.parse(it.nextString().take(10))
        }

    /** UUID column mapped to [UUID]. The database writes it in JSON as text (`123e4567-e89b-...`). */
    fun addUid(fn: String, getter: (B) -> UUID?, setter: (B, UUID) -> Unit, column: (T) -> Field<out UUID?>) =
        scalar(fn, getter, setter, UUID_SENTINEL, JsonFieldType.STRING, column, { it == JsonToken.STRING }) {
            UUID.fromString(it.nextString())
        }

    /** TIMESTAMP WITH TIME ZONE column mapped to [Instant] in the domain. */
    fun addIzt(
        fn: String, getter: (B) -> Instant?, setter: (B, Instant) -> Unit,
        column: (T) -> Field<out OffsetDateTime?>,
    ) = scalar(
        fn, getter, setter, INSTANT_SENTINEL, JsonFieldType.DATETIME, column, { it == JsonToken.STRING },
        toColumn = { OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(it.epochSeconds, it.nanosecondsOfSecond.toLong()), ZoneOffset.UTC) },
    ) {
        parseInstant(it.nextString())
    }

    /**
     * TIMESTAMP column **without** time zone mapped to [Instant] in the domain. Convention: the column value is
     * in UTC, on read and on write.
     */
    fun addIlt(
        fn: String, getter: (B) -> Instant?, setter: (B, Instant) -> Unit,
        column: (T) -> Field<out LocalDateTime?>,
    ) = scalar(
        fn, getter, setter, INSTANT_SENTINEL, JsonFieldType.DATETIME, column, { it == JsonToken.STRING },
        toColumn = { LocalDateTime.ofEpochSecond(it.epochSeconds, it.nanosecondsOfSecond, ZoneOffset.UTC) },
    ) {
        parseInstant(it.nextString())
    }

    @Deprecated("Renamed to addDay; removed in the next version", ReplaceWith("addDay(fn, getter, setter, column)"))
    fun addLocalDate(fn: String, getter: (B) -> LocalDate?, setter: (B, LocalDate) -> Unit, column: (T) -> Field<out LocalDate?>) =
        addDay(fn, getter, setter, column)

    @Deprecated("Renamed to addUid; removed in the next version", ReplaceWith("addUid(fn, getter, setter, column)"))
    fun addUuid(fn: String, getter: (B) -> UUID?, setter: (B, UUID) -> Unit, column: (T) -> Field<out UUID?>) =
        addUid(fn, getter, setter, column)

    @Deprecated("Renamed to addIzt; removed in the next version", ReplaceWith("addIzt(fn, getter, setter, column)"))
    fun addInstantFromOdt(fn: String, getter: (B) -> Instant?, setter: (B, Instant) -> Unit, column: (T) -> Field<out OffsetDateTime?>) =
        addIzt(fn, getter, setter, column)

    @Deprecated("Renamed to addIlt; removed in the next version", ReplaceWith("addIlt(fn, getter, setter, column)"))
    fun addInstantFromLdt(fn: String, getter: (B) -> Instant?, setter: (B, Instant) -> Unit, column: (T) -> Field<out LocalDateTime?>) =
        addIlt(fn, getter, setter, column)

    /** `ByteArray` field (BINARY / BLOB), carried as Base64 in JSON. */
    fun addBin(fn: String, getter: (B) -> ByteArray?, setter: (B, ByteArray) -> Unit, column: (T) -> Field<out ByteArray?>) =
        scalar(fn, getter, setter, ByteArray(0), JsonFieldType.BINARY, column, { it == JsonToken.STRING }) {
            // MIME decoder: tolerates the line breaks PostgreSQL's encode() inserts
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
        toColumn: (V) -> Any? = { it },
        read: (JsonReader) -> V,
    ): JsonQueryBuilder<B, T> {
        projectionFillers.add { setter(it, sentinel) }
        register(fn, getter, type, column)
        fieldReaders[fn] = { bean, reader ->
            if (accepts(reader.peek())) setter(bean, read(reader)) else reader.skipValue()
        }
        @Suppress("UNCHECKED_CAST")
        queryFields += JsonQueryField(
            fn, column, getter, { b, v -> if (v != null) setter(b, v as V) }, { getter(it) != null },
            { setter(it, sentinel) }, { toColumn(it as V) },
        )
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
     * Defers registering fields (typically relations) until first use, run only once. Use it for **all**
     * relations: it is what lets two mappings refer to each other.
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

    // :: 1:1 relation

    /**
     * Registers an associated bean (1:1 relation), loaded by a correlated subquery.
     *
     * @param childWhereClause writes the correlation (and the child's criterion) into the child query
     * @param key declares **which column of this table holds the key** of the associated entity. With it, a
     *            projection that asks for nothing beyond the key is built from the row itself, without a subquery —
     *            the common case of `ownFieldsProjection()`, which brings the association only to load the id. The
     *            declared names are those of the **child's JSON**; the columns, those of **this** table. `null`
     *            turns the shortcut off.
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
        relationKey?.columns()?.forEach { (part, partColumn) -> queryFields += keyField(fn, getter, setter, childQuery, part, partColumn) }
        fieldProjections.add { fields, ctx, bean, table ->
            val childPrjBean = getter(bean) ?: return@add
            // The key is already in this row: if the associated projection asks for nothing else, building it here
            // avoids a subquery that would only rediscover the foreign key column.
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

    /**
     * A part of an association's key, as a field of this mapping: this table's foreign key column, read and
     * written on the associated entity. The associated entity's field is resolved on first use, because the
     * mappings of two entities may refer to each other.
     */
    private fun <C : Any, U : Table<*>> keyField(
        fn: String,
        getter: (B) -> C?,
        setter: (B, C) -> Unit,
        childQuery: JsonQuery<C, U>,
        part: String,
        partColumn: (T) -> Field<*>,
    ): JsonQueryField<B, T> {
        val childField by kotlin.lazy {
            childQuery.fields().firstOrNull { it.name == part }
                ?: throw IllegalStateException("the key of '$fn' declares '$part', which is not a field of the associated entity")
        }
        val childOf = { bean: B -> getter(bean) ?: childQuery.newBean().also { setter(bean, it) } }
        return JsonQueryField(
            "$fn.$part", partColumn,
            get = { bean -> getter(bean)?.let { childField.get(it) } },
            set = { bean, value -> if (value != null) childField.set(childOf(bean), value) },
            projected = { getter(it) != null },
            mark = { bean -> childField.mark(childOf(bean)) },
            toColumn = { childField.toColumn(it) },
        )
    }

    // :: Child collection (1:N)

    /**
     * Registers a list of child beans (1:N relation), loaded by an aggregated correlated subquery.
     *
     * The projection collection must have at least one element — the first one gives the item's shape. If it is
     * `HasCriteria`, the criterion reaches [childWhereClause] and gives the order (through the child's
     * `setOrdering`); if it is `HasSlice`, it gives the slice.
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

    /** The JSON entry of a child collection, with the order and slice the collection itself declares. */
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
        // Order and slice come from what the collection itself declares: the criterion it carries brings the child
        // entity's OrderBy, and HasSlice brings the limit and offset. With neither, the query comes out as always.
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

    /** Builds the mapping, ready to run. */
    fun build(): JsonQuery<B, T> = Built()

    private inner class Built : JsonQuery<B, T> {

        private fun beans() = beanFactory ?: error("JsonQueryBuilder: setBeanFactory(...) was not configured")
        private fun tables() = tableFactory ?: error("JsonQueryBuilder: setTableFactory(...) was not configured")
        private fun dsl() = (dslContextSupplier ?: error("JsonQueryBuilder: setDSLContextSupplier(...) was not configured"))()

        override fun newBean(): B = beans()()

        override fun newProjectionBean(adapt: ((B) -> Unit)?): B {
            val bean = beans()()
            projectionFillers.forEach { it(bean) }
            adapt?.invoke(bean)
            return bean
        }

        override fun newTable(alias: String): T = tables()(alias)

        private val fieldList: List<JsonQueryField<B, T>> by kotlin.lazy {
            runLazyInit()
            queryFields.toList()
        }

        override fun fields(): List<JsonQueryField<B, T>> = fieldList

        override fun insert(bean: B, projection: B?, onConflictDoNothing: Boolean): Boolean {
            val dsl = dsl()
            val inserted = tables()(INSERTED_ALIAS)
            val target = unaliased(inserted)
            val identity = target.identity?.field?.name
            val primaryKey = target.primaryKey?.fields.orEmpty().map { it.name }.toSet()
            val columns = fields().filter { it.isColumnOf(inserted) }

            // What goes into the INSERT: what the projection marks (without one, what has a value) and the given primary key.
            // Never the identity: the database generates it. Two declarations of the same column: the first with a value wins.
            val values = LinkedHashMap<Field<*>, Any?>()
            val written = HashSet<JsonQueryField<B, T>>()
            for (field in columns) {
                val name = field.column(inserted).name
                if (name == identity) continue
                val value = field.get(bean)
                val wanted = if (projection != null) field.projected(projection) else value != null
                if (!wanted && !(name in primaryKey && value != null)) continue
                val column = target.field(name) ?: continue
                if (values[column] != null) continue
                values[column] = value?.let { DSL.`val`(field.toColumn(it), column).value }
                written += field
            }

            // Back to the bean goes what was not written or was written null: column defaults, identity, triggers.
            val readBack = newBean()
            val toRead = columns.filter { it !in written || it.get(bean) == null }
            (toRead.ifEmpty { columns.filter { it.column(inserted).name in primaryKey } }).forEach { it.mark(readBack) }

            @Suppress("UNCHECKED_CAST")
            val insert = (if (values.isEmpty()) dsl.insertInto(target).defaultValues() else dsl.insertInto(target).set(values as Map<Field<*>, *>))
                .let { if (onConflictDoNothing) it.onConflictDoNothing() else it } as InsertReturningStep<*>
            val ctx = QueryContext(dsl)
            val row = projection(ctx, inserted, readBack)
            val writtenKey = values.entries.filter { it.key.name in primaryKey }.associate { it.key.name to it.value }
            val json = JsonDialect.of(dsl.dialect()).insertAndSelect(dsl, insert, inserted, INSERTED_ALIAS, row, writtenKey)
                ?: return false
            val stored = parseJson(json)
            for (field in toRead) {
                field.set(bean, field.get(stored))
            }
            return true
        }

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
                // No order and no slice, the usual shape: whoever asks for nothing keeps seeing the same SQL and the same plan.
                return select(ctx, prjBean, whereClause, true)
            }
            val tbRoot = tables()(tableName + ctx.nextUniqueInt())
            val dialect = JsonDialect.of(ctx.dsl().dialect())
            val sort = order?.invoke(tbRoot)
            if (!sort.isNullOrEmpty() && !dialect.supportsOrderedAggregation()) {
                // Refuse instead of returning the collection out of the requested order: a wrongly ordered result passes for right.
                throw UnsupportedOperationException(
                    "dialect ${ctx.dsl().dialect()} cannot order inside the aggregation; order the collection on the application side"
                )
            }
            // The order goes INSIDE the aggregation. Wrapping this query in a derived table — where a plain
            // ORDER BY would fit — would put the correlation with the parent row out of reach: a derived table does
            // not see the outer scope, and the database answers "column parent.id not found".
            val agg = ctx.dsl()
                .select(dialect.jsonArrayAgg(projection(ctx, tbRoot, prjBean), sort).`as`(tableName + "_json"))
                .from(tbRoot)
            whereClause(tbRoot, agg)
            val aggQuery = agg.query

            // The slice does not fit in the aggregation, and for the same reason not in a derived table. It comes out
            // as an IN over the primary key: a correlated subquery — this one does see the parent — that repeats the
            // same filter, orders and cuts, returning the keys of the rows that stay.
            if (limit != null || offset != null) {
                val tbSlice = tables()(tableName + ctx.nextUniqueInt())
                val keep = ctx.dsl().select(primaryKeyOf(tbSlice)).from(tbSlice)
                // The clause is typed on the projection column because that is how the rest of the API uses it; here
                // it only adds conditions, and the type of the selected column does not reach it.
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
        val LOCAL_DATE_SENTINEL: LocalDate = LocalDate.of(1970, 1, 1)
        val UUID_SENTINEL: UUID = UUID(0L, 0L)

        /**
         * Reads what the database puts in JSON for a date/time column: ISO-8601 with offset, or the form without
         * offset (`2024-01-15T10:30:00` or `2024-01-15 10:30:00[.n]`), assumed in UTC.
         */
        fun parseTimestamp(text: String): OffsetDateTime {
            // Without offset (the plain TIMESTAMP column) the attempt with offset would always fail: read directly, without
            // the exception, which in a large collection cost more than the query itself (50k dates: 164 ms via exception, 23 ms direct).
            if (!hasOffset(text)) {
                return LocalDateTime.parse(text.replace(' ', 'T')).atOffset(ZoneOffset.UTC)
            }
            return try {
                OffsetDateTime.parse(text)
            } catch (_: DateTimeParseException) {
                LocalDateTime.parse(text.replace(' ', 'T')).atOffset(ZoneOffset.UTC)
            }
        }

        /** Whether the text carries an offset: `Z`, or `+`/`-` after the date (the date has hyphens; the time does not). */
        fun hasOffset(text: String): Boolean {
            val time = text.indexOfAny(charArrayOf('T', ' '))
            if (time < 0) return false
            for (i in time + 1 until text.length) {
                val c = text[i]
                if (c == 'Z' || c == 'z' || c == '+' || c == '-') return true
            }
            return false
        }

        fun parseInstant(text: String): Instant {
            val instant = parseTimestamp(text).toInstant()
            return Instant.fromEpochSeconds(instant.epochSecond, instant.nano)
        }

        /**
         * The table's primary key, resolved on the given instance (which has its own alias).
         *
         * It comes from the jOOQ-generated table, not from a declaration. Serves the child collection slice, which
         * needs something to say "these rows, and not the others" by.
         *
         * @throws IllegalStateException if the table declares no primary key, or if it is composite — a
         *         single-column `IN` cannot express it, and approximate SQL would silently return the wrong collection
         */
        /** The alias of the inserted row, in the query that reads it back. */
        private const val INSERTED_ALIAS = "inserted"

        /** The table behind an aliased instance (the `INSERT` goes to the table, not the alias). */
        private fun unaliased(table: Table<*>): Table<*> = (table as? org.jooq.impl.TableImpl<*>)?.`$aliased`() ?: table

        fun primaryKeyOf(table: Table<*>): Field<Any?> {
            val pk = table.primaryKey
            if (pk == null || pk.fields.isEmpty()) {
                throw IllegalStateException("collection slice requires a primary key, and ${table.name} declares none")
            }
            if (pk.fields.size > 1) {
                throw IllegalStateException(
                    "collection slice does not support a composite primary key: ${table.name} has ${pk.fields.size} key columns"
                )
            }
            val column = pk.fields[0]
            @Suppress("UNCHECKED_CAST")
            return (table.field(column) ?: column) as Field<Any?>
        }
    }
}
