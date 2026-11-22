package br.com.wdc.framework.domain.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ReadOnlyRepositoryTest {

    /** A dashboard row: not an entity that gets written, it is the result of a query. */
    private data class SalesRow(var productId: Long? = null, var total: Double? = null)

    private class SalesCriteria(var minTotal: Double? = null)

    /** Read-only repository: implements the queries, and there is no write to implement. */
    private class SalesDashboard(private val rows: List<SalesRow>) : ReadOnlyRepository<SalesRow, SalesCriteria, Long> {
        private fun matching(criteria: SalesCriteria) = rows.filter { criteria.minTotal == null || it.total!! >= criteria.minTotal!! }

        override suspend fun count(criteria: SalesCriteria): Int = matching(criteria).size

        override suspend fun fetch(criteria: SalesCriteria, offset: Int, limit: Int): List<SalesRow> =
            matching(criteria).drop(offset).let { if (limit > 0) it.take(limit) else it }

        override suspend fun fetchById(id: Long, projection: SalesRow?): SalesRow? = rows.firstOrNull { it.productId == id }

        override fun ownFieldsProjection() = SalesRow(0L, 0.0)
    }

    /** A plain entity, with a full repository. */
    private data class Note(var id: Long? = null)

    private class NoteRepository(private val rows: MutableList<Note>) : Repository<Note, Unit, Long> {
        override suspend fun insert(bean: Note, projection: Note?): Boolean = rows.add(bean)
        override suspend fun update(newBean: Note, oldBean: Note?, projection: Note?): Boolean = true
        override suspend fun delete(criteria: Unit): Int = rows.size.also { rows.clear() }
        override suspend fun count(criteria: Unit): Int = rows.size
        override suspend fun fetch(criteria: Unit, offset: Int, limit: Int): List<Note> = rows.drop(offset)
        override suspend fun fetchById(id: Long, projection: Note?): Note? = rows.firstOrNull { it.id == id }
        override fun ownFieldsProjection() = Note(0L)
    }

    /** Code that only reads declares so through the type — and works with any repository. */
    private suspend fun <E, C> howMany(repository: ReadOnlyRepository<E, C, *>, criteria: C): Int = repository.count(criteria)

    private val dashboard = SalesDashboard(listOf(SalesRow(1, 10.5), SalesRow(2, 99.5), SalesRow(3, 45.5), SalesRow(4, 70.5)))

    @Test
    fun readOnlyRepository_answersTheQueries() = runTest {
        assertEquals(4, dashboard.count(SalesCriteria()))
        assertEquals(listOf<Long?>(2, 3, 4), dashboard.fetch(SalesCriteria(minTotal = 40.5)).map { it.productId })
        assertEquals(listOf<Long?>(3), dashboard.fetch(SalesCriteria(minTotal = 40.5), offset = 1, limit = 1).map { it.productId })
        assertEquals(99.5, dashboard.fetchById(2)!!.total)
        assertNull(dashboard.fetchById(9))
    }

    @Test
    fun fetchPage_comesFromCountAndFetch() = runTest {
        val page = dashboard.fetchPage(SalesCriteria(), 1, 3)
        assertEquals(listOf<Long?>(4), page.items.map { it.productId })
        assertEquals(4, page.totalItems)
        assertEquals(2, page.totalPages)
        assertEquals(1, page.page)
    }

    @Test
    fun aFullRepository_isAlsoAReadOnlyOne() = runTest {
        val notes = NoteRepository(mutableListOf(Note(1), Note(2)))
        val asReader: ReadOnlyRepository<Note, Unit, Long> = notes

        assertEquals(2, howMany(asReader, Unit))
        assertEquals(4, howMany(dashboard, SalesCriteria()))
        assertEquals(listOf<Long?>(2), asReader.fetch(Unit, offset = 1).map { it.id })
    }
}
