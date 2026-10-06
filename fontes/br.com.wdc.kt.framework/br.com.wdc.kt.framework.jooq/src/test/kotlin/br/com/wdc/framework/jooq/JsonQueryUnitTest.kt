package br.com.wdc.framework.jooq

import br.com.wdc.framework.domain.criteria.Operator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jooq.SQLDialect
import org.jooq.impl.DSL

/** O que não precisa de banco: leitura do JSON, perguntas sobre a projeção e as recusas. */
class JsonQueryUnitTest {

    private val dsl = DSL.using(SQLDialect.H2)
    private val library = Library { dsl }

    // :: Leitura do JSON

    @Test
    fun parseJson_skipsUnknownNames_andUnexpectedTokens() {
        val book = library.bookQuery.parseJson(
            """{"id":"not a number","title":"T","future":{"a":[1,2]},"pages":true,"active":"yes","price":null,"author":[1]}"""
        )
        assertNull(book.id)
        assertEquals("T", book.title)
        assertNull(book.pages)
        assertNull(book.active)
        assertNull(book.price)
        assertNull(book.author)
    }

    @Test
    fun parseJson_readsNestedObjectsAndArrays() {
        val author = library.authorQuery.parseJson(
            """{"id":1,"name":"A","books":[{"id":10,"author":{"id":1}},{"id":11}]}"""
        )
        assertEquals(listOf<Long?>(10L, 11L), author.books!!.map { it.id })
        assertEquals(1L, author.books!![0].author!!.id)
        assertNull(author.books!![1].author)
    }

    @Test
    fun parseJson_acceptsTimestampsWithAndWithoutZone() {
        fun published(text: String) = library.bookQuery.parseJson("""{"published":"$text"}""").published.toString()
        assertEquals("2024-01-15T10:30:00Z", published("2024-01-15T10:30:00"))
        assertEquals("2024-01-15T10:30:00Z", published("2024-01-15 10:30:00"))
        assertEquals("2024-01-15T10:30:00.500Z", published("2024-01-15 10:30:00.5"))
        assertEquals("2024-01-15T10:30:00Z", published("2024-01-15T07:30:00-03:00"))
    }

    // :: Projeção

    @Test
    fun projectsBeyond_asksAboutScalarsAndRelations() {
        val query = library.bookQuery
        assertFalse(query.projectsBeyond(null, setOf("id")))
        assertFalse(query.projectsBeyond(Book(id = 1L), setOf("id")))
        assertTrue(query.projectsBeyond(Book(id = 1L, title = "~"), setOf("id")))
        assertTrue(query.projectsBeyond(Book(id = 1L, author = Author()), setOf("id")))
        assertFalse(query.projectsBeyond(Book(id = 1L, title = "~"), setOf("id", "title")))
    }

    @Test
    fun emptyProjection_isAnEmptyObject() {
        val sql = library.bookQuery.select(Book()) { _, _ -> }.sql
        assertTrue("'{}'" in sql, sql)
    }

    @Test
    fun aliases_areUniqueWithinOneQuery() {
        val shape = Book(id = 1L, author = Author(id = 1L, name = "~"))
        val sql = library.authorQuery.select(Author(id = 1L, books = listOf(shape))) { _, _ -> }.sql
        val aliases = Regex("\"([abn]\\d+)\"\\.").findAll(sql).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("a1", "b2", "a3"), aliases, sql)
    }

    @Test
    fun lazyRegistration_runsOnce_onFirstUse() {
        val runs = AtomicInteger()
        val query = JsonQueryBuilder<Note, TNote>()
            .setAlias("n").setBeanFactory(::Note).setTableFactory(NOTE::`as`).setDSLContextSupplier { dsl }
            .lazy { qb ->
                runs.incrementAndGet()
                qb.addStr("text", { it.text }, { b, v -> b.text = v }, { it.TEXT })
            }
            .build()
        assertEquals(0, runs.get())
        assertEquals("x", query.parseJson("""{"text":"x"}""").text)
        query.newProjectionBean()
        query.select(null) { _, _ -> }
        assertEquals(1, runs.get())
    }

    // :: Recusas

    @Test
    fun unconfiguredBuilder_failsNamingWhatIsMissing() {
        val query = JsonQueryBuilder<Note, TNote>().build()
        assertTrue("setBeanFactory" in assertFailsWith<IllegalStateException> { query.newBean() }.message!!)
        assertTrue("setTableFactory" in assertFailsWith<IllegalStateException> { query.newTable("n") }.message!!)

        val noDsl = JsonQueryBuilder<Note, TNote>().setBeanFactory(::Note).setTableFactory(NOTE::`as`).build()
        assertTrue("setDSLContextSupplier" in assertFailsWith<IllegalStateException> { noDsl.select(null) { _, _ -> } }.message!!)
    }

    @Test
    fun dialect_withoutJsonSupport_isRefused() {
        assertEquals(br.com.wdc.framework.jooq.dialect.H2JsonDialect, JsonDialect.of(SQLDialect.H2))
        assertEquals(br.com.wdc.framework.jooq.dialect.PostgresJsonDialect, JsonDialect.of(SQLDialect.POSTGRES))
        assertFailsWith<UnsupportedOperationException> { JsonDialect.of(SQLDialect.MYSQL) }
        assertFailsWith<UnsupportedOperationException> { JsonDialect.of(SQLDialect.SQLITE) }
    }

    @Test
    fun dialectDefaults_ignoreTheOrder_andSayTheyDoNotSupportIt() {
        // o padrão da interface ignora a ordem — e é por isso que quem monta a consulta pergunta antes
        val element = DSL.inline("{}")
        val defaults = object : JsonDialect {
            override fun jsonObject(entries: List<JsonFieldEntry>) = element
            override fun jsonArrayAgg(element: org.jooq.Field<String>) = element
        }
        assertEquals(element, defaults.jsonArrayAgg(element, listOf(BOOK.ID.asc())))
        assertFalse(defaults.supportsOrderedAggregation())
    }

    // :: CriterionTranslator

    @Test
    fun translator_unsetOrNullCriterion_isNull_andEmptyAndIsNoCondition() {
        val c = BookCriteria()
        assertNull(CriterionTranslator.translate(BOOK.ID, c.id))
        assertNull(CriterionTranslator.translate(BOOK.ID, null))
        assertNull(CriterionTranslator.translate(BOOK, BOOK.ID, c.id))
        assertEquals(DSL.noCondition(), CriterionTranslator.and(emptyList()))
        assertEquals(DSL.noCondition(), CriterionTranslator.and(listOf(null, null)))
    }

    @Test
    fun translator_discardsPredicatesWithWrongArity() {
        val c = BookCriteria()
        c.id.restore(Operator.BETWEEN, listOf(1L))      // faltou um valor
        c.id.restore(Operator.EQ, emptyList())          // faltou o valor
        assertNull(CriterionTranslator.translate(BOOK.ID, c.id))

        c.id.restore(Operator.GT, listOf(5L))
        val sql = dsl.renderInlined(CriterionTranslator.translate(BOOK.ID, c.id)!!)
        assertEquals("\"BOOK\".\"ID\" > 5", sql)
    }

    @Test
    fun translator_rendersConjunctionAndDisjunction() {
        val and = BookCriteria().also { it.pages.ge(100); it.pages.le(300) }
        assertEquals(
            "(\"BOOK\".\"PAGES\" >= 100 and \"BOOK\".\"PAGES\" <= 300)",
            dsl.renderInlined(CriterionTranslator.translate(BOOK.PAGES, and.pages)!!),
        )
        val or = BookCriteria().also { it.pages.or().lt(100); it.pages.isNull() }
        assertEquals(
            "(\"BOOK\".\"PAGES\" < 100 or \"BOOK\".\"PAGES\" is null)",
            dsl.renderInlined(CriterionTranslator.translate(BOOK.PAGES, or.pages)!!),
        )
    }

    @Test
    fun translator_resolvesTheColumnOnTheGivenTableInstance() {
        val aliased = BOOK.`as`("x9")
        val c = BookCriteria().also { it.id.eq(7L) }
        assertEquals("\"x9\".\"ID\" = 7", dsl.renderInlined(CriterionTranslator.translate(aliased, BOOK.ID, c.id)!!))
    }

    @Test
    fun translator_refusesAColumnThatIsNotInTheTable() {
        val e = assertFailsWith<IllegalArgumentException> { CriterionTranslator.column(AUTHOR, BOOK.TITLE) }
        assertEquals("a coluna TITLE não existe em AUTHOR", e.message)
    }
}
