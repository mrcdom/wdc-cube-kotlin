package br.com.wdc.framework.jooq

import br.com.wdc.framework.domain.projection.ProjectionList
import br.com.wdc.framework.persistence.transaction.TransactionScope
import br.com.wdc.framework.persistence.transaction.TransactionServiceImpl
import java.math.BigDecimal
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.impl.DSL

/**
 * O contrato do mapeamento JSON, verificado contra um banco real. As subclasses escolhem o banco: o mesmo
 * conjunto roda em H2 e em PostgreSQL.
 */
abstract class JsonQueryContractTest(private val database: TestDatabase) {

    protected lateinit var dsl: DSLContext
    protected lateinit var library: Library

    private val published1 = Instant.parse("2024-01-15T10:30:00Z")
    private val published2 = Instant.parse("2024-12-31T23:59:59Z")
    private val bigCover = ByteArray(300) { (it % 251).toByte() }

    @BeforeTest
    fun setUp() {
        dsl = DSL.using(TransactionAwareConnectionProvider(database.dataSource), database.dialect, database.settings)
        database.dataSource.connection.use { JsonDialect.of(database.dialect).initialize(it) }
        database.ddl.forEach { dsl.execute(it) }
        library = Library { dsl }

        author(1, "Machado")
        author(2, "Clarice")
        author(3, "Sem Livros")
        book(10, 1, "Dom Casmurro", "39.90", 256, true, published1, byteArrayOf(1, 2, 3), Kind.NOVEL)
        book(11, 1, "Quincas \"Borba\"\n— edição", "19.90", 300, false, published2, bigCover, Kind.NOVEL)
        book(12, 1, "Esaú e Jacó", "59.90", 320, true, null, null, null)
        book(20, 2, "A Hora da Estrela", "29.90", 88, true, published2, null, Kind.NOVEL)
        book(21, 2, "A Descoberta do Mundo", "89.90", 500, true, published1, null, Kind.ESSAY)
        book(30, null, "Órfão", null, null, null, null, null, null)
        dsl.insertInto(NOTE).set(NOTE.AUTHOR_ID, 1L).set(NOTE.TEXT, "nota").execute()
    }

    private fun author(id: Long, name: String) {
        dsl.insertInto(AUTHOR).set(AUTHOR.ID, id).set(AUTHOR.NAME, name).execute()
    }

    private fun book(
        id: Long, authorId: Long?, title: String, price: String?, pages: Int?, active: Boolean?,
        published: Instant?, cover: ByteArray?, kind: Kind?,
    ) {
        dsl.insertInto(BOOK)
            .set(BOOK.ID, id).set(BOOK.AUTHOR_ID, authorId).set(BOOK.TITLE, title)
            .set(BOOK.PRICE, price?.let(::BigDecimal)).set(BOOK.PAGES, pages).set(BOOK.ACTIVE, active)
            .set(BOOK.PUBLISHED, published?.toUtcLocalDateTime()).set(BOOK.COVER, cover).set(BOOK.KIND, kind?.name)
            .execute()
    }

    private fun bookById(id: Long, projection: Book? = null): Book? =
        library.bookQuery.fetchOne(projection) { t, q -> q.where(t.ID.eq(id)) }

    private fun authorById(id: Long, projection: Author): Author? =
        library.authorQuery.fetchOne(projection) { t, q -> q.where(t.ID.eq(id)) }

    private fun booksProjection(shape: Book = Book(id = 1L), criteria: BookCriteria? = null) = ProjectionList(shape, criteria)

    private fun booksWhere(criteria: BookCriteria): List<Long?> =
        library.bookQuery.fetchToList(Book(id = 1L)) { t, q ->
            q.where(Library.bookConditions(t, criteria)).orderBy(t.ID.asc())
        }.map { it.id }

    // :: Escalares e projeção

    @Test
    fun fullProjection_readsEveryScalarType() {
        val book = bookById(10)!!
        assertEquals(10L, book.id)
        assertEquals("Dom Casmurro", book.title)
        assertEquals(39.90, book.price)
        assertEquals(256, book.pages)
        assertEquals(true, book.active)
        assertEquals(published1, book.published)
        assertContentEquals(byteArrayOf(1, 2, 3), book.cover)
        assertEquals(Kind.NOVEL, book.kind)
        // relações não entram na projeção total: ela só marca escalares
        assertNull(book.author)
    }

    @Test
    fun nullColumns_becomeNullFields() {
        val book = bookById(30)!!
        assertEquals(30L, book.id)
        assertEquals("Órfão", book.title)
        assertNull(book.price)
        assertNull(book.pages)
        assertNull(book.active)
        assertNull(book.published)
        assertNull(book.cover)
        assertNull(book.kind)
    }

    @Test
    fun partialProjection_selectsOnlyTheMarkedColumns() {
        val projection = Book(id = 1L, title = "~")
        val sql = library.bookQuery.select(projection) { t, q -> q.where(t.ID.eq(10L)) }.sql.uppercase()
        assertTrue("TITLE" in sql)
        assertFalse("PRICE" in sql)
        assertFalse("COVER" in sql)

        val book = bookById(10, projection)!!
        assertEquals("Dom Casmurro", book.title)
        assertNull(book.price)
        assertNull(book.cover)
    }

    @Test
    fun framework_doesNotForceTheId() {
        val book = bookById(10, Book(title = "~"))!!
        assertNull(book.id)
        assertEquals("Dom Casmurro", book.title)
    }

    @Test
    fun newProjectionBean_marksScalars_andCanBeAdapted() {
        val full = library.bookQuery.newProjectionBean()
        assertNotNull(full.id); assertNotNull(full.title); assertNotNull(full.cover); assertNotNull(full.kind)
        assertNull(full.author)

        val adapted = library.bookQuery.newProjectionBean { it.cover = null; it.author = Author(id = 1L) }
        assertNull(adapted.cover)
        assertNotNull(adapted.author)
    }

    @Test
    fun text_withQuotesNewlinesAndAccents_survivesTheJsonRoundTrip() {
        assertEquals("Quincas \"Borba\"\n— edição", bookById(11)!!.title)
    }

    @Test
    fun binary_longerThanOneBase64Line_survives() {
        assertContentEquals(bigCover, bookById(11)!!.cover)
    }

    @Test
    fun unknownEnumValue_becomesNull() {
        dsl.update(BOOK).set(BOOK.KIND, "POEM").where(BOOK.ID.eq(10L)).execute()
        assertNull(bookById(10)!!.kind)
    }

    @Test
    fun timestamp_withFractionalSeconds_isReadInUtc() {
        val precise = Instant.parse("2025-03-09T02:30:15.123456Z")
        dsl.update(BOOK).set(BOOK.PUBLISHED, precise.toUtcLocalDateTime()).where(BOOK.ID.eq(10L)).execute()
        assertEquals(precise, bookById(10)!!.published)
    }

    @Test
    fun fetchOne_withoutMatch_isNull_andFetchToListIsEmpty() {
        assertNull(bookById(999))
        assertEquals(emptyList(), library.bookQuery.fetchToList(null) { t, q -> q.where(t.ID.eq(999L)) })
    }

    @Test
    fun rootQuery_honoursOrderLimitAndOffset() {
        val ids = library.bookQuery.fetchToList(Book(id = 1L)) { t, q ->
            q.where(t.AUTHOR_ID.isNotNull).orderBy(t.PRICE.desc()).limit(2).offset(1)
        }.map { it.id }
        assertEquals(listOf<Long?>(12L, 10L), ids)
    }

    // :: Relação 1:1

    @Test
    fun association_projectingOnlyTheKey_usesTheForeignKeyColumn_withoutSubselect() {
        val projection = Book(id = 1L, author = Author(id = 1L))
        val sql = library.bookQuery.select(projection) { t, q -> q.where(t.ID.eq(10L)) }.sql
        assertEquals(1, Regex("(?i)\\bselect\\b").findAll(sql).count(), sql)

        val book = bookById(10, projection)!!
        assertEquals(1L, book.author!!.id)
        assertNull(book.author!!.name)
    }

    @Test
    fun association_projectingBeyondTheKey_usesASubselect() {
        val projection = Book(id = 1L, author = Author(id = 1L, name = "~"))
        val sql = library.bookQuery.select(projection) { t, q -> q.where(t.ID.eq(10L)) }.sql
        assertEquals(2, Regex("(?i)\\bselect\\b").findAll(sql).count(), sql)

        val book = bookById(10, projection)!!
        assertEquals(1L, book.author!!.id)
        assertEquals("Machado", book.author!!.name)
    }

    @Test
    fun association_withoutRow_isNull_whenResolvedBySubselect() {
        val book = bookById(30, Book(id = 1L, author = Author(id = 1L, name = "~")))!!
        assertNull(book.author)
    }

    @Test
    fun association_withNullForeignKey_carriesNoId_whenResolvedByTheKey() {
        // pelo atalho não há subconsulta que possa "não achar": o objeto vem, sem id
        val book = bookById(30, Book(id = 1L, author = Author(id = 1L)))!!
        assertNull(book.author?.id)
    }

    // :: Coleção filha (1:N)

    @Test
    fun collection_withoutOrderNorSlice_bringsEveryChild() {
        val author = authorById(1, Author(id = 1L, name = "~", books = booksProjection(Book(id = 1L, title = "~"))))!!
        assertEquals("Machado", author.name)
        assertEquals(setOf<Long?>(10L, 11L, 12L), author.books!!.map { it.id }.toSet())
        assertTrue(author.books!!.all { it.title != null && it.price == null })
    }

    @Test
    fun collection_ofParentWithoutChildren_isEmpty_notNull() {
        val author = authorById(3, Author(id = 1L, books = booksProjection()))!!
        assertEquals(emptyList(), author.books)
    }

    @Test
    fun collection_notProjected_staysNull() {
        assertNull(authorById(1, Author(id = 1L))!!.books)
    }

    @Test
    fun collection_criteria_filtersTheChildren_keepingBindValues() {
        // o valor do filtro é bind value dentro de uma subconsulta: se o dialeto concatenasse texto, ele se perderia
        val criteria = BookCriteria().also { it.price.ge(39.90) }
        val author = authorById(1, Author(id = 1L, books = booksProjection(criteria = criteria)))!!
        assertEquals(setOf<Long?>(10L, 12L), author.books!!.map { it.id }.toSet())
    }

    @Test
    fun collection_criteria_combinesFields_andWorksForEveryParentRow() {
        val criteria = BookCriteria().also { it.active.eq(true); it.title.containing("a") }
        val authors = library.authorQuery.fetchToList(Author(id = 1L, books = booksProjection(criteria = criteria))) { t, q ->
            q.orderBy(t.ID.asc())
        }
        assertEquals(listOf<Long?>(1L, 2L, 3L), authors.map { it.id })
        assertEquals(setOf<Long?>(10L, 12L), authors[0].books!!.map { it.id }.toSet())
        assertEquals(setOf<Long?>(20L, 21L), authors[1].books!!.map { it.id }.toSet())
        assertEquals(emptyList(), authors[2].books)
    }

    @Test
    fun collection_isOrderedByTheChildCriteria() {
        fun ids(orderBy: BookCriteria.OrderBy) =
            authorById(1, Author(id = 1L, books = booksProjection(criteria = BookCriteria().withOrderBy(orderBy))))!!
                .books!!.map { it.id }

        assertEquals(listOf<Long?>(10L, 11L, 12L), ids(BookCriteria.OrderBy.OLDEST_FIRST))
        assertEquals(listOf<Long?>(12L, 11L, 10L), ids(BookCriteria.OrderBy.NEWEST_FIRST))
        assertEquals(listOf<Long?>(11L, 10L, 12L), ids(BookCriteria.OrderBy.CHEAPEST_FIRST))
        assertEquals(listOf<Long?>(12L, 10L, 11L), ids(BookCriteria.OrderBy.MOST_EXPENSIVE_FIRST))
    }

    @Test
    fun collection_isSliced_afterOrdering() {
        fun ids(limit: Int?, offset: Int?) = authorById(
            1,
            Author(
                id = 1L,
                books = booksProjection(criteria = BookCriteria().withOrderBy(BookCriteria.OrderBy.MOST_EXPENSIVE_FIRST))
                    .withLimit(limit).withOffset(offset),
            ),
        )!!.books!!.map { it.id }

        assertEquals(listOf<Long?>(12L, 10L), ids(limit = 2, offset = null))
        assertEquals(listOf<Long?>(10L, 11L), ids(limit = 2, offset = 1))
        assertEquals(listOf<Long?>(11L), ids(limit = null, offset = 2))
        assertEquals(emptyList(), ids(limit = 2, offset = 5))
    }

    @Test
    fun collection_sliceAndFilter_areAppliedPerParent() {
        val criteria = BookCriteria().withOrderBy(BookCriteria.OrderBy.CHEAPEST_FIRST).also { it.active.eq(true) }
        val authors = library.authorQuery.fetchToList(
            Author(id = 1L, books = booksProjection(criteria = criteria).withLimit(1)),
        ) { t, q -> q.orderBy(t.ID.asc()) }

        // o mais barato entre os ativos de cada autor — o recorte enxerga a linha do pai
        assertEquals(listOf<Long?>(10L), authors[0].books!!.map { it.id })
        assertEquals(listOf<Long?>(20L), authors[1].books!!.map { it.id })
        assertEquals(emptyList(), authors[2].books)
    }

    @Test
    fun collection_sliceWithoutOrder_stillLimitsTheCount() {
        val author = authorById(1, Author(id = 1L, books = booksProjection().withLimit(2)))!!
        assertEquals(2, author.books!!.size)
    }

    @Test
    fun collection_slice_requiresAPrimaryKey() {
        val projection = Author(id = 1L, notes = ProjectionList(Note(text = "~"), null).withLimit(1))
        val e = assertFailsWith<IllegalStateException> { authorById(1, projection) }
        assertTrue("chave primária" in e.message!!, e.message)

        // sem recorte, a mesma coleção funciona
        assertEquals(listOf<String?>("nota"), authorById(1, Author(id = 1L, notes = ProjectionList(Note(text = "~"), null)))!!.notes!!.map { it.text })
    }

    @Test
    fun nestedRelations_goBothWays() {
        val shape = Book(id = 1L, author = Author(id = 1L, name = "~"))
        val author = authorById(2, Author(id = 1L, books = booksProjection(shape, BookCriteria().withOrderBy(BookCriteria.OrderBy.OLDEST_FIRST))))!!
        assertEquals(listOf<Long?>(20L, 21L), author.books!!.map { it.id })
        assertTrue(author.books!!.all { it.author!!.name == "Clarice" })
    }

    // :: Critérios na raiz

    @Test
    fun criteria_empty_filtersNothing() {
        assertEquals(6, booksWhere(BookCriteria()).size)
    }

    @Test
    fun criteria_identityOperators() {
        assertEquals(listOf<Long?>(10L), booksWhere(BookCriteria().also { it.id.eq(10L) }))
        assertEquals(listOf<Long?>(10L, 12L), booksWhere(BookCriteria().also { it.id.isIn(10L, 12L, 999L) }))
        assertEquals(listOf<Long?>(20L, 21L, 30L), booksWhere(BookCriteria().also { it.id.ne(10L); it.id.ne(11L); it.id.ne(12L) }))
        assertEquals(listOf<Long?>(11L), booksWhere(BookCriteria().also { it.active.eq(false) }))
        assertEquals(listOf<Long?>(30L), booksWhere(BookCriteria().also { it.active.isNull() }))
        assertEquals(5, booksWhere(BookCriteria().also { it.active.isNotNull() }).size)
    }

    @Test
    fun criteria_comparisons_withValueConversion() {
        assertEquals(listOf<Long?>(12L, 21L), booksWhere(BookCriteria().also { it.price.gt(39.90) }))
        assertEquals(listOf<Long?>(10L, 12L, 21L), booksWhere(BookCriteria().also { it.price.ge(39.90) }))
        assertEquals(listOf<Long?>(11L), booksWhere(BookCriteria().also { it.price.lt(29.90) }))
        assertEquals(listOf<Long?>(10L, 20L), booksWhere(BookCriteria().also { it.price.between(29.90, 39.90) }))
        assertEquals(listOf<Long?>(11L, 20L), booksWhere(BookCriteria().also { it.price.between(null, 29.90) }))
        assertEquals(listOf<Long?>(20L, 21L), booksWhere(BookCriteria().also { it.price.isIn(29.90, 89.90) }))
    }

    @Test
    fun criteria_successiveRequests_areAnd_andOrIsPerField() {
        assertEquals(listOf<Long?>(10L, 20L), booksWhere(BookCriteria().also { it.price.ge(29.90); it.price.le(39.90) }))
        assertEquals(listOf<Long?>(11L, 21L), booksWhere(BookCriteria().also { it.price.or().lt(29.90); it.price.gt(59.90) }))
        // entre campos é sempre AND, mesmo com um deles disjuntivo
        assertEquals(listOf<Long?>(21L), booksWhere(BookCriteria().also { it.price.or().lt(29.90); it.price.gt(59.90); it.active.eq(true) }))
    }

    @Test
    fun criteria_text() {
        assertEquals(listOf<Long?>(20L, 21L), booksWhere(BookCriteria().also { it.title.startingWith("a ") }))
        assertEquals(listOf<Long?>(10L), booksWhere(BookCriteria().also { it.title.containing("CASMURRO") }))
        assertEquals(listOf<Long?>(10L), booksWhere(BookCriteria().also { it.title.like("Dom%") }))
        assertEquals(emptyList(), booksWhere(BookCriteria().also { it.title.like("dom%") }))
        assertEquals(listOf<Long?>(10L), booksWhere(BookCriteria().also { it.title.ilike("dom%") }))
        assertEquals(listOf<Long?>(10L), booksWhere(BookCriteria().also { it.title.eq("Dom Casmurro") }))
        // curingas fazem parte do valor: sem eles, like é igualdade
        assertEquals(emptyList(), booksWhere(BookCriteria().also { it.title.like("Dom") }))
    }

    @Test
    fun criteria_instantRange_isComparedInUtc() {
        val start = Instant.parse("2024-12-31T23:59:59Z")
        assertEquals(listOf<Long?>(11L, 20L), booksWhere(BookCriteria().also { it.published.ge(start) }))
        assertEquals(listOf<Long?>(10L, 21L), booksWhere(BookCriteria().also { it.published.lt(start) }))
        assertEquals(
            listOf<Long?>(10L, 21L),
            booksWhere(BookCriteria().also { it.published.between(Instant.parse("2024-01-15T10:30:00Z"), Instant.parse("2024-01-15T10:30:00Z")) }),
        )
        // o mesmo instante escrito com outro offset filtra igual
        assertEquals(listOf<Long?>(11L, 20L), booksWhere(BookCriteria().also { it.published.ge(Instant.parse("2024-12-31T20:59:59-03:00")) }))
    }

    // :: Transação

    @Test
    fun queriesInsideATransaction_shareItsConnection_andSeeItsWrites() = runBlocking {
        val tx = TransactionServiceImpl { database.dataSource }
        assertFailsWith<IllegalStateException> {
            tx.required {
                author(9, "Provisório")
                // a leitura enxerga a escrita não comitada: mesma conexão
                assertEquals("Provisório", authorById(9, Author(id = 1L, name = "~"))!!.name)
                throw IllegalStateException("desiste")
            }
        }
        assertNull(TransactionScope.current())
        assertNull(authorById(9, Author(id = 1L)))
    }

    @Test
    fun writesOutsideATransaction_areAutoCommitted() {
        author(8, "Avulso")
        database.dataSource.connection.use { c ->
            c.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM AUTHOR WHERE ID = 8").use { rs -> rs.next(); assertEquals(1, rs.getInt(1)) }
            }
        }
    }
}

class H2JsonQueryTest : JsonQueryContractTest(TestDatabase.h2())

class PostgresJsonQueryTest : JsonQueryContractTest(TestDatabase.postgres()) {

    @Test
    fun runsAgainstARealPostgres() {
        val version = dsl.fetchOne("select version()")!!.get(0, String::class.java)
        assertTrue(version.startsWith("PostgreSQL"), version)
    }
}
