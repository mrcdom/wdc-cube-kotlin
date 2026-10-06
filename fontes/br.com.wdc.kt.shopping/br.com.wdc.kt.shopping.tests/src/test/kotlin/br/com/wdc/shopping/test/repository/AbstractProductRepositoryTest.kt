package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.product.Product
import br.com.wdc.shopping.domain.product.ProductCriteria
import br.com.wdc.shopping.domain.product.ProductCriteria.OrderBy
import br.com.wdc.shopping.domain.product.ProductRepository
import br.com.wdc.shopping.scripts.sgbd.DBReset
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * O contrato do repositório de produtos. As subclasses dizem por onde: direto na implementação (LOCAL) ou
 * atravessando o REST — os mesmos casos têm de valer nos dois.
 *
 * Carga de demonstração: Cafeteira design italiano (199,99), Bola Wilson (45,30), Fita veda rosca (2,67) e
 * Pen Drive 2GB (16,00), com ids crescentes nessa ordem.
 */
abstract class AbstractProductRepositoryTest {

    protected abstract fun repo(): ProductRepository

    private suspend fun ids(criteria: ProductCriteria, offset: Int = 0, limit: Int = 0): List<Long?> =
        repo().fetch(criteria.withProjection(Product().apply { id = ProjectionValues.i64 }), offset, limit).map { it.id }

    private fun byIdAsc() = ProductCriteria().withOrderBy(OrderBy.OLDEST_FIRST)

    private val cafeteira get() = DBReset.CAFETEIRA_ID
    private val bola get() = DBReset.BOLA_WILSON_ID
    private val fita get() = DBReset.FITA_VEDA_ROSCA_ID
    private val penDrive get() = DBReset.PEN_DRIVE2GB_ID

    // :: fetch

    @Test
    fun fetchAll_returnsFourProducts() = runBlocking {
        val products = repo().fetch(ProductCriteria())
        assertEquals(4, products.size)
    }

    @Test
    fun fetch_defaultProjection_bringsEverythingButTheImage() = runBlocking {
        val product = repo().fetch(ProductCriteria().withProductId(cafeteira)).single()
        assertEquals(cafeteira, product.id)
        assertEquals("Cafeteira design italiano", product.name)
        assertEquals(199.99, product.price!!, 0.001)
        assertTrue(product.description!!.contains("Capacidade"))
        assertNull(product.image)
    }

    @Test
    fun fetchById_returnsCorrectProduct() = runBlocking {
        val product = repo().fetchById(cafeteira, null)
        assertNotNull(product)
        assertNotNull(product!!.name)
        assertNotNull(product.price)
    }

    @Test
    fun fetchById_nonExistent_returnsNull() = runBlocking {
        assertNull(repo().fetchById(Long.MAX_VALUE, null))
    }

    @Test
    fun fetchWithProjection_onlyRequestedFields() = runBlocking {
        val pv = ProjectionValues
        val projection = Product().apply { id = pv.i64; name = pv.str }

        val product = repo().fetchById(penDrive, projection)

        assertEquals(penDrive, product!!.id)
        assertEquals("Pen Drive 2GB", product.name)
        assertNull(product.price)
        assertNull(product.description)
    }

    @Test
    fun fetchWithProjection_withoutId_stillBringsTheId() = runBlocking {
        val product = repo().fetch(ProductCriteria().withProductId(bola).withProjection(Product().apply { name = ProjectionValues.str })).single()
        assertEquals(bola, product.id)
        assertEquals("Bola Wilson", product.name)
    }

    @Test
    fun fetchByCriteria_productId() = runBlocking {
        val products = repo().fetch(ProductCriteria().withProductId(bola))
        assertEquals(1, products.size)
        assertEquals(bola, products[0].id)
    }

    @Test
    fun fetchWithOffsetAndLimit() = runBlocking {
        assertEquals(listOf<Long?>(cafeteira, bola), ids(byIdAsc(), limit = 2))
        assertEquals(listOf<Long?>(bola, fita), ids(byIdAsc(), offset = 1, limit = 2))
        assertEquals(listOf<Long?>(fita, penDrive), ids(byIdAsc(), offset = 2))
        assertEquals(emptyList<Long?>(), ids(byIdAsc(), offset = 10, limit = 2))
    }

    @Test
    fun fetchPage_bringsTheSliceAndTheTotals() = runBlocking {
        val page = repo().fetchPage(byIdAsc(), page = 1, pageSize = 3)
        assertEquals(listOf<Long?>(penDrive), page.items.map { it.id })
        assertEquals(1, page.page)
        assertEquals(2, page.totalPages)
        assertEquals(4, page.totalItems)

        val first = repo().fetchPage(byIdAsc(), page = 0, pageSize = 3)
        assertEquals(listOf<Long?>(cafeteira, bola, fita), first.items.map { it.id })
    }

    @Test
    fun fetchPage_countsOnlyWhatTheCriteriaSelects() = runBlocking {
        val page = repo().fetchPage(ProductCriteria().also { it.price.lt(50.0) }.withOrderBy(OrderBy.OLDEST_FIRST), page = 0, pageSize = 2)
        assertEquals(listOf<Long?>(bola, fita), page.items.map { it.id })
        assertEquals(3, page.totalItems)
        assertEquals(2, page.totalPages)
    }

    // :: ordenações

    @Test
    fun orderBy_everyConstant() = runBlocking {
        assertEquals(listOf<Long?>(cafeteira, bola, fita, penDrive), ids(ProductCriteria().withOrderBy(OrderBy.OLDEST_FIRST)))
        assertEquals(listOf<Long?>(penDrive, fita, bola, cafeteira), ids(ProductCriteria().withOrderBy(OrderBy.NEWEST_FIRST)))
        assertEquals(listOf<Long?>(bola, cafeteira, fita, penDrive), ids(ProductCriteria().withOrderBy(OrderBy.NAME_A_TO_Z)))
        assertEquals(listOf<Long?>(fita, penDrive, bola, cafeteira), ids(ProductCriteria().withOrderBy(OrderBy.CHEAPEST_FIRST)))
        assertEquals(listOf<Long?>(cafeteira, bola, penDrive, fita), ids(ProductCriteria().withOrderBy(OrderBy.MOST_EXPENSIVE_FIRST)))
    }

    // :: operadores

    @Test
    fun criteria_isIn_andNe() = runBlocking {
        assertEquals(listOf<Long?>(cafeteira, fita), ids(byIdAsc().also { it.productId.isIn(cafeteira, fita, Long.MAX_VALUE) }))
        assertEquals(listOf<Long?>(bola, penDrive), ids(byIdAsc().also { it.productId.ne(cafeteira); it.productId.ne(fita) }))
    }

    @Test
    fun criteria_comparisons() = runBlocking {
        assertEquals(listOf<Long?>(cafeteira), ids(byIdAsc().also { it.price.gt(45.30) }))
        assertEquals(listOf<Long?>(cafeteira, bola), ids(byIdAsc().also { it.price.ge(45.30) }))
        assertEquals(listOf<Long?>(fita), ids(byIdAsc().also { it.price.lt(16.0) }))
        assertEquals(listOf<Long?>(fita, penDrive), ids(byIdAsc().also { it.price.le(16.0) }))
    }

    @Test
    fun criteria_between_isInclusive_andAcceptsOpenBounds() = runBlocking {
        assertEquals(listOf<Long?>(bola, penDrive), ids(byIdAsc().also { it.price.between(16.0, 45.30) }))
        assertEquals(listOf<Long?>(cafeteira, bola), ids(byIdAsc().also { it.price.between(45.30, null) }))
        assertEquals(listOf<Long?>(fita, penDrive), ids(byIdAsc().also { it.price.between(null, 16.0) }))
        assertEquals(4, ids(byIdAsc().also { it.price.between(null, null) }).size)
    }

    @Test
    fun criteria_successiveRequestsAreAnd_orIsPerField_andFieldsAreAlwaysAnd() = runBlocking {
        assertEquals(listOf<Long?>(bola, penDrive), ids(byIdAsc().also { it.price.ge(16.0); it.price.le(45.30) }))
        assertEquals(listOf<Long?>(cafeteira, fita), ids(byIdAsc().also { it.price.or().lt(16.0); it.price.gt(45.30) }))
        assertEquals(listOf<Long?>(cafeteira), ids(byIdAsc().also { it.price.or().lt(16.0); it.price.gt(45.30); it.name.startingWith("caf") }))
    }

    @Test
    fun criteria_emptyAndCleared_filterNothing() = runBlocking {
        assertEquals(4, ids(ProductCriteria()).size)
        val criteria = byIdAsc().also { it.productId.eq(cafeteira) }
        assertEquals(1, ids(criteria).size)
        criteria.productId.clear()
        assertEquals(4, ids(criteria).size)
        // valor nulo não acrescenta pedido
        assertEquals(4, ids(ProductCriteria().withProductId(null).withName(null).also { it.price.ge(null) }).size)
    }

    // :: texto

    @Test
    fun criteria_text() = runBlocking {
        assertEquals(listOf<Long?>(bola), ids(byIdAsc().also { it.name.startingWith("bola") }))
        // dois pedidos no mesmo campo valem juntos: nomes com "i" e com "l"
        assertEquals(listOf<Long?>(cafeteira, bola), ids(byIdAsc().also { it.name.containing("i"); it.name.containing("l") }))
        assertEquals(listOf<Long?>(cafeteira, fita), ids(byIdAsc().also { it.description.containing("corta-pingos"); it.description.or().containing("Tigre") }))
        assertEquals(listOf<Long?>(penDrive), ids(byIdAsc().also { it.name.ilike("pen drive%") }))
        assertEquals(listOf<Long?>(penDrive), ids(byIdAsc().also { it.name.eq("Pen Drive 2GB") }))
        // curingas fazem parte do valor: sem eles, like é igualdade
        assertEquals(emptyList<Long?>(), ids(byIdAsc().also { it.description.like("Marca Tigre") }))
        assertEquals(listOf<Long?>(fita), ids(byIdAsc().also { it.description.like("%Marca Tigre%") }))
        // like é sensível a maiúsculas (na descrição; o nome é uma coluna que ignora caixa no H2)
        assertEquals(emptyList<Long?>(), ids(byIdAsc().also { it.description.like("%marca tigre%") }))
        assertEquals(listOf<Long?>(fita), ids(byIdAsc().also { it.description.ilike("%marca tigre%") }))
    }

    @Test
    fun criteria_emptyText_filtersNothing() = runBlocking {
        assertEquals(4, ids(ProductCriteria().also { it.name.containing(""); it.name.startingWith(null); it.description.like("") }).size)
    }

    // :: count

    @Test
    fun countAll_returnsFour() = runBlocking {
        assertEquals(4, repo().count(ProductCriteria()))
    }

    @Test
    fun countByProductId_returnsOne() = runBlocking {
        assertEquals(1, repo().count(ProductCriteria().withProductId(cafeteira)))
    }

    @Test
    fun countNonExistent_returnsZero() = runBlocking {
        assertEquals(0, repo().count(ProductCriteria().withProductId(Long.MAX_VALUE)))
    }

    @Test
    fun count_honoursTheOperators() = runBlocking {
        assertEquals(3, repo().count(ProductCriteria().also { it.price.lt(100.0) }))
    }

    // :: imagem

    @Test
    fun fetchImage_returnsNonNullForSeededProduct() = runBlocking {
        val image = repo().fetchImage(cafeteira)
        assertNotNull(image)
        assertTrue(image!!.isNotEmpty())
    }

    @Test
    fun fetchImage_nonExistent_throws() = runBlocking {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().fetchImage(Long.MAX_VALUE) }
        }
    }

    @Test
    fun updateImage_replacesTheImage_withItsRealSize() = runBlocking {
        val image = ByteArray(1500) { (it % 127).toByte() }
        assertTrue(repo().updateImage(bola, image))
        assertArrayEquals(image, repo().fetchImage(bola))
        // o restante do produto não muda
        assertEquals("Bola Wilson", repo().fetchById(bola)!!.name)
    }

    @Test
    fun insertedProduct_hasNoImage() = runBlocking {
        val product = Product().apply { name = "Sem foto"; price = 1.0; description = "d" }
        repo().insert(product)
        assertNull(repo().fetchImage(product.id!!))
    }

    // :: insert

    @Test
    fun insert_newProduct() = runBlocking {
        val product = Product().apply {
            name = "Teclado USB"
            price = 89.90
            description = "Teclado mecanico"
        }

        assertTrue(repo().insert(product))
        assertNotNull(product.id)

        val fetched = repo().fetchById(product.id!!, null)
        assertEquals("Teclado USB", fetched!!.name)
        assertEquals(89.90, fetched.price!!, 0.001)
        assertTrue(fetched.description!!.contains("Teclado"))
    }

    // :: update

    @Test
    fun update_existingProduct() = runBlocking {
        val original = repo().fetchById(penDrive, null)!!

        val updated = Product().apply {
            id = original.id
            name = "Pen Drive 4GB"
            price = 35.0
            description = original.description
        }
        assertTrue(repo().update(updated, original))

        val fetched = repo().fetchById(penDrive, null)
        assertEquals("Pen Drive 4GB", fetched!!.name)
        assertEquals(35.0, fetched.price!!, 0.001)
        assertEquals(original.description, fetched.description)
    }

    @Test
    fun update_withProjection_changesOnlyTheMarkedFields() = runBlocking {
        val original = repo().fetchById(penDrive)!!

        val changes = Product().apply { id = penDrive; name = "Só o nome"; price = 999.0 }
        assertTrue(repo().update(changes, null, Product().apply { name = ProjectionValues.str }))

        val fetched = repo().fetchById(penDrive)!!
        assertEquals("Só o nome", fetched.name)
        assertEquals(original.price!!, fetched.price!!, 0.001)
        assertEquals(original.description, fetched.description)
    }

    @Test
    fun update_withoutChanges_returnsFalse() = runBlocking {
        val original = repo().fetchById(penDrive)!!
        val same = Product().apply { id = original.id; name = original.name; price = original.price; description = original.description }
        assertFalse(repo().update(same, original))
    }

    @Test
    fun update_nonExistent_returnsFalse() = runBlocking {
        val ghost = Product().apply { id = Long.MAX_VALUE; name = "x"; price = 1.0; description = "d" }
        assertFalse(repo().update(ghost))
    }

    @Test
    fun update_withoutId_isRefused() = runBlocking<Unit> {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().update(Product().apply { name = "sem id" }) }
        }
    }

    // :: insertOrUpdate

    @Test
    fun insertOrUpdate_insertsWhenThereIsNoOldBean() = runBlocking {
        val product = Product().apply {
            name = "Mouse Wireless"
            price = 49.90
            description = "Mouse sem fio"
        }

        assertTrue(repo().insertOrUpdate(product, null))
        assertNotNull(product.id)
        assertEquals(5, repo().count(ProductCriteria()))
    }

    @Test
    fun insertOrUpdate_updatesWhenThereIsAnOldBean() = runBlocking {
        val original = repo().fetchById(fita, null)!!

        val product = Product().apply {
            id = fita
            name = "Fita Veda Rosca Premium"
            price = 12.0
            description = original.description
        }
        assertTrue(repo().insertOrUpdate(product, original))

        assertEquals("Fita Veda Rosca Premium", repo().fetchById(fita, null)!!.name)
        assertEquals(4, repo().count(ProductCriteria()))
    }

    // :: delete

    @Test
    fun delete_byCriteria() = runBlocking {
        val teclado = Product().apply { name = "Teclado"; price = 80.0; description = "d" }
        val mouse = Product().apply { name = "Mouse"; price = 40.0; description = "d" }
        repo().insert(teclado)
        repo().insert(mouse)

        assertEquals(1, repo().delete(ProductCriteria().withProductId(teclado.id)))
        assertEquals(1, repo().delete(ProductCriteria().also { it.name.eq("Mouse") }))
        assertEquals(4, repo().count(ProductCriteria()))
    }

    @Test
    fun deleteNonExistent_returnsZero() = runBlocking {
        assertEquals(0, repo().delete(ProductCriteria().withProductId(Long.MAX_VALUE)))
    }

    @Test
    fun delete_withEmptyCriteria_isRefused_andDeletesNothing() = runBlocking {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().delete(ProductCriteria()) }
        }
        // critério só com ordenação continua sem filtro
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().delete(ProductCriteria().withOrderBy(OrderBy.OLDEST_FIRST)) }
        }
        assertEquals(4, repo().count(ProductCriteria()))
    }
}
