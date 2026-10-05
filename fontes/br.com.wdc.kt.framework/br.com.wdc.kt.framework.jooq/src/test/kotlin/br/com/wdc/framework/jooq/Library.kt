package br.com.wdc.framework.jooq

import br.com.wdc.framework.domain.criteria.ComparableCriterion
import br.com.wdc.framework.domain.criteria.Criteria
import br.com.wdc.framework.domain.criteria.Criterion
import br.com.wdc.framework.domain.criteria.TextCriterion
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Name
import org.jooq.Record
import org.jooq.SortField
import org.jooq.Table
import org.jooq.TableField
import org.jooq.UniqueKey
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl

// Esquema de exemplo, escrito à mão no formato que o gerador do jOOQ produz.

class TAuthor(alias: Name = DSL.name("AUTHOR"), aliased: Table<Record>? = null) : TableImpl<Record>(alias, null, aliased) {
    val ID: TableField<Record, Long?> = createField(DSL.name("ID"), SQLDataType.BIGINT.nullable(false), this)
    val NAME: TableField<Record, String?> = createField(DSL.name("NAME"), SQLDataType.VARCHAR(100), this)

    override fun getPrimaryKey(): UniqueKey<Record> =
        Internal.createUniqueKey(this, DSL.name("PK_AUTHOR"), arrayOf<TableField<Record, *>>(ID), true)

    override fun `as`(alias: String): TAuthor = TAuthor(DSL.name(alias), this)
}

class TBook(alias: Name = DSL.name("BOOK"), aliased: Table<Record>? = null) : TableImpl<Record>(alias, null, aliased) {
    val ID: TableField<Record, Long?> = createField(DSL.name("ID"), SQLDataType.BIGINT.nullable(false), this)
    val AUTHOR_ID: TableField<Record, Long?> = createField(DSL.name("AUTHOR_ID"), SQLDataType.BIGINT, this)
    val TITLE: TableField<Record, String?> = createField(DSL.name("TITLE"), SQLDataType.VARCHAR(200), this)
    val PRICE: TableField<Record, BigDecimal?> = createField(DSL.name("PRICE"), SQLDataType.NUMERIC(20, 2), this)
    val PAGES: TableField<Record, Int?> = createField(DSL.name("PAGES"), SQLDataType.INTEGER, this)
    val ACTIVE: TableField<Record, Boolean?> = createField(DSL.name("ACTIVE"), SQLDataType.BOOLEAN, this)
    val PUBLISHED: TableField<Record, LocalDateTime?> = createField(DSL.name("PUBLISHED"), SQLDataType.LOCALDATETIME(6), this)
    val COVER: TableField<Record, ByteArray?> = createField(DSL.name("COVER"), SQLDataType.BLOB, this)
    val KIND: TableField<Record, String?> = createField(DSL.name("KIND"), SQLDataType.VARCHAR(20), this)

    override fun getPrimaryKey(): UniqueKey<Record> =
        Internal.createUniqueKey(this, DSL.name("PK_BOOK"), arrayOf<TableField<Record, *>>(ID), true)

    override fun `as`(alias: String): TBook = TBook(DSL.name(alias), this)
}

/** Tabela sem chave primária, para o caso em que o recorte de coleção é recusado. */
class TNote(alias: Name = DSL.name("NOTE"), aliased: Table<Record>? = null) : TableImpl<Record>(alias, null, aliased) {
    val AUTHOR_ID: TableField<Record, Long?> = createField(DSL.name("AUTHOR_ID"), SQLDataType.BIGINT, this)
    val TEXT: TableField<Record, String?> = createField(DSL.name("TEXT"), SQLDataType.VARCHAR(200), this)

    override fun `as`(alias: String): TNote = TNote(DSL.name(alias), this)
}

val AUTHOR = TAuthor()
val BOOK = TBook()
val NOTE = TNote()

// Domínio de exemplo

enum class Kind {
    NOVEL, ESSAY;

    companion object {
        fun parse(text: String?): Kind? = entries.firstOrNull { it.name == text }
    }
}

class Author(var id: Long? = null, var name: String? = null, var books: List<Book>? = null, var notes: List<Note>? = null)

class Book(
    var id: Long? = null,
    var title: String? = null,
    var price: Double? = null,
    var pages: Int? = null,
    var active: Boolean? = null,
    var published: Instant? = null,
    var cover: ByteArray? = null,
    var kind: Kind? = null,
    var author: Author? = null,
)

class Note(var text: String? = null)

class BookCriteria : Criteria {
    val id = ComparableCriterion<BookCriteria, Long>(this, "id")
    val title = TextCriterion(this, "title")
    val price = ComparableCriterion<BookCriteria, Double>(this, "price")
    val pages = ComparableCriterion<BookCriteria, Int>(this, "pages")
    val active = Criterion<BookCriteria, Boolean>(this, "active")
    val published = ComparableCriterion<BookCriteria, Instant>(this, "published")

    var orderBy: OrderBy? = null
    fun withOrderBy(orderBy: OrderBy?) = apply { this.orderBy = orderBy }

    override fun criterions(): List<Criterion<*, *>> = listOf(id, title, price, pages, active, published)

    enum class OrderBy { OLDEST_FIRST, NEWEST_FIRST, CHEAPEST_FIRST, MOST_EXPENSIVE_FIRST }
}

fun Instant.toUtcLocalDateTime(): LocalDateTime = LocalDateTime.ofEpochSecond(epochSeconds, nanosecondsOfSecond, ZoneOffset.UTC)

/** Os mapeamentos do domínio de exemplo, como um repositório os declararia. */
class Library(private val dsl: () -> DSLContext) {

    val bookQuery: JsonQuery<Book, TBook> = JsonQueryBuilder<Book, TBook>()
        .setAlias("b").setBeanFactory(::Book).setTableFactory(BOOK::`as`).setDSLContextSupplier(dsl)
        .setOrdering(::bookOrdering)
        .addI64("id", { it.id }, { b, v -> b.id = v }, { it.ID })
        .addStr("title", { it.title }, { b, v -> b.title = v }, { it.TITLE })
        .addF64("price", { it.price }, { b, v -> b.price = v }, { it.PRICE })
        .addI32("pages", { it.pages }, { b, v -> b.pages = v }, { it.PAGES })
        .addBit("active", { it.active }, { b, v -> b.active = v }, { it.ACTIVE })
        .addInstantFromLdt("published", { it.published }, { b, v -> b.published = v }, { it.PUBLISHED })
        .addBin("cover", { it.cover }, { b, v -> b.cover = v }, { it.COVER })
        .addEnm("kind", { it.kind }, { b, v -> b.kind = v }, Kind.NOVEL, Kind::parse, { it.KIND })
        .lazy { qb ->
            qb.addBeanField(
                "author", { it.author }, { b, v -> b.author = v }, authorQuery,
                { cq -> cq.where().and(cq.childTable.ID.eq(cq.superTable.AUTHOR_ID)) },
                { key -> key.addI64("id") { it.AUTHOR_ID } },
            )
        }
        .build()

    val noteQuery: JsonQuery<Note, TNote> = JsonQueryBuilder<Note, TNote>()
        .setAlias("n").setBeanFactory(::Note).setTableFactory(NOTE::`as`).setDSLContextSupplier(dsl)
        .addStr("text", { it.text }, { b, v -> b.text = v }, { it.TEXT })
        .build()

    val authorQuery: JsonQuery<Author, TAuthor> = JsonQueryBuilder<Author, TAuthor>()
        .setAlias("a").setBeanFactory(::Author).setTableFactory(AUTHOR::`as`).setDSLContextSupplier(dsl)
        .addI64("id", { it.id }, { b, v -> b.id = v }, { it.ID })
        .addStr("name", { it.name }, { b, v -> b.name = v }, { it.NAME })
        .lazy { qb ->
            qb.addBeanListField("books", { it.books }, { b, v -> b.books = v }, bookQuery) { cq ->
                cq.where()
                    .and(cq.childTable.AUTHOR_ID.eq(cq.superTable.ID))
                    .and(bookConditions(cq.childTable, cq.criteriaAs<BookCriteria>()))
            }
            qb.addBeanListField("notes", { it.notes }, { b, v -> b.notes = v }, noteQuery) { cq ->
                cq.where().and(cq.childTable.AUTHOR_ID.eq(cq.superTable.ID))
            }
        }
        .build()

    companion object {
        fun bookOrdering(t: TBook, criteria: Any?): List<SortField<*>> {
            val c = criteria as? BookCriteria ?: return emptyList()
            return when (c.orderBy ?: return emptyList()) {
                BookCriteria.OrderBy.OLDEST_FIRST -> listOf(t.ID.asc())
                BookCriteria.OrderBy.NEWEST_FIRST -> listOf(t.ID.desc())
                BookCriteria.OrderBy.CHEAPEST_FIRST -> listOf(t.PRICE.asc(), t.ID.asc())
                BookCriteria.OrderBy.MOST_EXPENSIVE_FIRST -> listOf(t.PRICE.desc(), t.ID.desc())
            }
        }

        fun bookConditions(t: TBook, c: BookCriteria?): Condition {
            if (c == null) return DSL.noCondition()
            return CriterionTranslator.and(
                listOf(
                    CriterionTranslator.translate(t.ID, c.id),
                    CriterionTranslator.translate(t.TITLE, c.title),
                    CriterionTranslator.translate(t.PRICE, c.price) { BigDecimal.valueOf(it) },
                    CriterionTranslator.translate(t.PAGES, c.pages),
                    CriterionTranslator.translate(t.ACTIVE, c.active),
                    CriterionTranslator.translate(t.PUBLISHED, c.published) { it.toUtcLocalDateTime() },
                )
            )
        }
    }
}
