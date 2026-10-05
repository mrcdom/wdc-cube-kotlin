package br.com.wdc.framework.domain.repository

import br.com.wdc.framework.domain.exception.BusinessException
import br.com.wdc.framework.domain.exception.InvalidRequestException
import br.com.wdc.framework.domain.pagination.Page
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RepositoryContractTest {

    private data class Thing(var id: Long? = null, var name: String? = null)

    private class ThingCriteria(var id: Long? = null)

    /** Repositório em memória: só o necessário para exercitar os defaults da interface. */
    private class MemoryRepository(private val rows: MutableList<Thing>) : Repository<Thing, ThingCriteria, Long> {
        val calls = ArrayList<String>()

        override suspend fun insert(bean: Thing): Boolean {
            calls.add("insert"); return rows.add(bean)
        }

        override suspend fun update(newBean: Thing, oldBean: Thing?, projection: Thing?): Boolean {
            calls.add("update(old=${oldBean != null},prj=${projection != null})"); return true
        }

        override suspend fun delete(criteria: ThingCriteria): Int = 0

        override suspend fun count(criteria: ThingCriteria): Int = select(criteria).size

        override suspend fun fetch(criteria: ThingCriteria, offset: Int, limit: Int): List<Thing> {
            calls.add("fetch(offset=$offset,limit=$limit)")
            val skipped = select(criteria).drop(offset)
            return if (limit > 0) skipped.take(limit) else skipped
        }

        override suspend fun fetchById(id: Long, projection: Thing?): Thing? =
            fetch(ThingCriteria(id), 0, 1).firstOrNull()

        override fun newProjection(): Thing = Thing(1L, "~")

        private fun select(criteria: ThingCriteria) = rows.filter { criteria.id == null || it.id == criteria.id }
    }

    private fun repo(count: Int) = MemoryRepository((1..count).map { Thing(it.toLong(), "n$it") }.toMutableList())

    @Test
    fun pageOf_computesTotalPages() {
        assertEquals(Page(listOf(1, 2), 0, 3, 5), Page.of(listOf(1, 2), 0, 2, 5))
        assertEquals(2, Page.of(emptyList<Int>(), 0, 5, 10).totalPages)
        assertEquals(1, Page.of(emptyList<Int>(), 0, 5, 1).totalPages)
        assertEquals(0, Page.of(emptyList<Int>(), 0, 5, 0).totalPages)
        assertEquals(0, Page.of(emptyList<Int>(), 0, 0, 10).totalPages)
    }

    @Test
    fun pageOf_copiesTheItems() {
        val source = mutableListOf(1, 2)
        val page = Page.of(source, 0, 2, 2)
        source.add(3)
        assertEquals(listOf(1, 2), page.items)
    }

    @Test
    fun fetch_defaultsToNoOffsetAndNoLimit() = runTest {
        val repo = repo(5)
        assertEquals(5, repo.fetch(ThingCriteria()).size)
        assertEquals(2, repo.fetch(ThingCriteria(), limit = 2).size)
        assertEquals(listOf("fetch(offset=0,limit=0)", "fetch(offset=0,limit=2)"), repo.calls)
    }

    @Test
    fun fetchPage_countsThenFetchesTheSlice() = runTest {
        val repo = repo(5)
        val page = repo.fetchPage(ThingCriteria(), page = 1, pageSize = 2)
        assertEquals(listOf<Long?>(3L, 4L), page.items.map { it.id })
        assertEquals(1, page.page)
        assertEquals(3, page.totalPages)
        assertEquals(5, page.totalItems)
        assertEquals(listOf("fetch(offset=2,limit=2)"), repo.calls)
    }

    @Test
    fun fetchById_goesThroughFetch() = runTest {
        val repo = repo(3)
        assertEquals("n2", repo.fetchById(2L)!!.name)
        assertNull(repo.fetchById(99L))
    }

    @Test
    fun insertOrUpdate_decidesByOldBean() = runTest {
        val repo = repo(0)
        repo.insertOrUpdate(Thing(1L), null)
        repo.insertOrUpdate(Thing(1L, "b"), Thing(1L, "a"))
        assertEquals(listOf("insert", "update(old=true,prj=false)"), repo.calls)
    }

    @Test
    fun update_defaultsOldBeanAndProjectionToNull() = runTest {
        val repo = repo(0)
        repo.update(Thing(1L))
        assertEquals(listOf("update(old=false,prj=false)"), repo.calls)
    }

    @Test
    fun changed_requiresProjectionMark_andDifference() {
        val projection = Thing(name = "~")
        // projeção marca o campo e não há oldBean → grava (inclusive null, que limpa o valor)
        assertTrue(Repository.changed(Thing(1L, "a"), null, projection) { it.name })
        assertTrue(Repository.changed(Thing(1L, null), null, projection) { it.name })
        // marca e mudou
        assertTrue(Repository.changed(Thing(1L, "a"), Thing(1L, "b"), projection) { it.name })
        assertTrue(Repository.changed(Thing(1L, null), Thing(1L, "b"), projection) { it.name })
        // marca mas não mudou
        assertFalse(Repository.changed(Thing(1L, "a"), Thing(1L, "a"), projection) { it.name })
        // mudou mas a projeção não marca
        assertFalse(Repository.changed(Thing(1L, "a"), Thing(1L, "b"), Thing()) { it.name })
    }

    @Test
    fun businessException_wrap_keepsBusinessExceptions() {
        val business = InvalidRequestException("recusado")
        assertSame(business, BusinessException.wrap("contexto", business))

        val other = IllegalStateException("boom")
        val wrapped = BusinessException.wrap("contexto", other)
        assertEquals("contexto", wrapped.message)
        assertSame(other, wrapped.suppressedExceptions.single())
    }
}
