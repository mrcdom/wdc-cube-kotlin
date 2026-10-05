package br.com.wdc.shopping.test.repository

import br.com.wdc.framework.domain.projection.ProjectionValues
import br.com.wdc.shopping.domain.exception.BusinessException
import br.com.wdc.shopping.domain.user.User
import br.com.wdc.shopping.domain.user.UserCriteria
import br.com.wdc.shopping.domain.user.UserCriteria.OrderBy
import br.com.wdc.shopping.domain.user.UserRepository
import br.com.wdc.shopping.scripts.sgbd.DBReset
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * O contrato do repositório de usuários, válido direto na implementação (LOCAL) e atravessando o REST.
 *
 * Carga de demonstração, em ordem de id: admin "João da Silva" (ADMIN), fulano "Fulano de Tal" (CUSTOMER) e
 * beotrano "Beotrano de Alguma Coisa" (CUSTOMER).
 */
abstract class AbstractUserRepositoryTest {

    protected abstract fun repo(): UserRepository

    private suspend fun ids(criteria: UserCriteria, offset: Int = 0, limit: Int = 0): List<Long?> =
        repo().fetch(criteria.withProjection(User().apply { id = ProjectionValues.i64 }), offset, limit).map { it.id }

    private fun byIdAsc() = UserCriteria().withOrderBy(OrderBy.OLDEST_FIRST)

    private val admin get() = DBReset.ADMIN_ID
    private val fulano get() = DBReset.FULANO_ID
    private val beotrano get() = DBReset.BEOTRANO_ID

    // :: fetch

    @Test
    fun fetchAll_returnsAllSeededUsers() = runBlocking {
        assertEquals(3, repo().fetch(UserCriteria()).size)
    }

    @Test
    fun fetch_defaultProjection_bringsEverythingButThePassword() = runBlocking {
        val user = repo().fetch(UserCriteria().withUserId(admin)).single()
        assertEquals(admin, user.id)
        assertEquals("admin", user.userName)
        assertEquals("João da Silva", user.name)
        assertEquals("ADMIN", user.roles)
        assertNull(user.password)
    }

    @Test
    fun fetchById_returnsCorrectUser() = runBlocking {
        val user = repo().fetchById(admin, null)
        assertEquals("admin", user!!.userName)
        assertEquals("João da Silva", user.name)
    }

    @Test
    fun fetchById_nonExistent_returnsNull() = runBlocking {
        assertNull(repo().fetchById(Long.MAX_VALUE, null))
    }

    @Test
    fun fetchWithProjection_onlyRequestedFields() = runBlocking {
        val pv = ProjectionValues
        val user = repo().fetchById(admin, User().apply { id = pv.i64; userName = pv.str })
        assertEquals(admin, user!!.id)
        assertEquals("admin", user.userName)
        assertNull(user.name)
        assertNull(user.roles)
    }

    @Test
    fun fetchByCriteria_userName() = runBlocking {
        val users = repo().fetch(UserCriteria().withUserName("fulano"))
        assertEquals(1, users.size)
        assertEquals(fulano, users[0].id)
    }

    @Test
    fun passwordDigest_comesOnlyWhenProjected() = runBlocking {
        val projection = User().apply { id = ProjectionValues.i64; password = ProjectionValues.str }
        val stored = repo().fetch(UserCriteria().withUserName("admin").withProjection(projection)).single()
        // é o resumo, não a senha; a coluna é CHAR(32), então vem completado com espaços
        assertEquals("1ymiigxvce4vzea4zp5bsfbgj", stored.password!!.trim())
    }

    @Test
    fun fetchWithOffsetAndLimit() = runBlocking {
        assertEquals(listOf<Long?>(fulano), ids(byIdAsc(), offset = 1, limit = 1))
        assertEquals(listOf<Long?>(admin, fulano), ids(byIdAsc(), limit = 2))
        assertEquals(listOf<Long?>(beotrano), ids(byIdAsc(), offset = 2))
    }

    @Test
    fun fetchPage_bringsTheSliceAndTheTotals() = runBlocking {
        val page = repo().fetchPage(byIdAsc(), page = 1, pageSize = 2)
        assertEquals(listOf<Long?>(beotrano), page.items.map { it.id })
        assertEquals(2, page.totalPages)
        assertEquals(3, page.totalItems)
    }

    // :: ordenações

    @Test
    fun orderBy_everyConstant() = runBlocking {
        assertEquals(listOf<Long?>(admin, fulano, beotrano), ids(UserCriteria().withOrderBy(OrderBy.OLDEST_FIRST)))
        assertEquals(listOf<Long?>(beotrano, fulano, admin), ids(UserCriteria().withOrderBy(OrderBy.NEWEST_FIRST)))
        // Beotrano de…, Fulano de Tal, João da Silva
        assertEquals(listOf<Long?>(beotrano, fulano, admin), ids(UserCriteria().withOrderBy(OrderBy.NAME_A_TO_Z)))
        // admin, beotrano, fulano
        assertEquals(listOf<Long?>(admin, beotrano, fulano), ids(UserCriteria().withOrderBy(OrderBy.LOGIN_A_TO_Z)))
    }

    // :: operadores e texto

    @Test
    fun criteria_identityAndComparison() = runBlocking {
        assertEquals(listOf<Long?>(admin, beotrano), ids(byIdAsc().also { it.userId.isIn(admin, beotrano, Long.MAX_VALUE) }))
        assertEquals(listOf<Long?>(fulano, beotrano), ids(byIdAsc().also { it.userId.ne(admin) }))
        assertEquals(listOf<Long?>(fulano, beotrano), ids(byIdAsc().also { it.userId.gt(admin) }))
        assertEquals(listOf<Long?>(admin, fulano), ids(byIdAsc().also { it.userId.between(admin, fulano) }))
    }

    @Test
    fun criteria_text() = runBlocking {
        assertEquals(listOf<Long?>(fulano), ids(byIdAsc().also { it.name.startingWith("fulano") }))
        assertEquals(listOf<Long?>(fulano, beotrano), ids(byIdAsc().also { it.name.containing(" DE ") }))
        assertEquals(listOf<Long?>(admin), ids(byIdAsc().also { it.name.like("João%") }))
        assertEquals(emptyList<Long?>(), ids(byIdAsc().also { it.name.like("joão%") }))
        assertEquals(listOf<Long?>(admin), ids(byIdAsc().also { it.name.ilike("joão%") }))
        assertEquals(listOf<Long?>(admin, beotrano), ids(byIdAsc().also { it.userName.or().eq("admin"); it.userName.startingWith("beo") }))
    }

    @Test
    fun criteria_roles() = runBlocking {
        assertEquals(listOf<Long?>(admin), ids(byIdAsc().withRoles("ADMIN")))
        assertEquals(listOf<Long?>(fulano, beotrano), ids(byIdAsc().also { it.roles.containing("customer") }))
        assertEquals(emptyList<Long?>(), ids(byIdAsc().also { it.roles.isNull() }))
    }

    @Test
    fun criteria_fieldsAreAlwaysAnd_andEmptyValuesFilterNothing() = runBlocking {
        assertEquals(emptyList<Long?>(), ids(byIdAsc().withUserName("admin").withRoles("CUSTOMER")))
        assertEquals(3, ids(UserCriteria().withUserId(null).withUserName(null).also { it.name.containing("") }).size)
    }

    // :: count

    @Test
    fun countAll_returnsThree() = runBlocking {
        assertEquals(3, repo().count(UserCriteria()))
    }

    @Test
    fun countByUserId_returnsOne() = runBlocking {
        assertEquals(1, repo().count(UserCriteria().withUserId(admin)))
    }

    @Test
    fun countByNonExistentId_returnsZero() = runBlocking {
        assertEquals(0, repo().count(UserCriteria().withUserId(Long.MAX_VALUE)))
    }

    // :: insert

    @Test
    fun insert_newUser() = runBlocking {
        val user = User().apply {
            userName = "newuser"
            password = "digest-of-secret"
            name = "New User"
            roles = "CUSTOMER"
        }

        assertTrue(repo().insert(user))
        assertNotNull(user.id)

        val fetched = repo().fetchById(user.id!!, null)!!
        assertEquals("newuser", fetched.userName)
        assertEquals("New User", fetched.name)
        assertEquals("CUSTOMER", fetched.roles)
        assertNull(fetched.password)
    }

    // :: update

    @Test
    fun update_existingUser() = runBlocking {
        val pv = ProjectionValues
        val full = User().apply { id = pv.i64; userName = pv.str; password = pv.str; name = pv.str; roles = pv.str }
        val original = repo().fetchById(admin, full)!!

        val updated = User().apply {
            id = original.id
            userName = original.userName
            password = original.password
            name = "Nome Alterado"
            roles = original.roles
        }
        assertTrue(repo().update(updated, original))

        val fetched = repo().fetchById(admin, full)!!
        assertEquals("Nome Alterado", fetched.name)
        assertEquals("admin", fetched.userName)
        assertEquals(original.password, fetched.password)
    }

    @Test
    fun update_withProjection_changesOnlyTheMarkedFields_andCanSetNull() = runBlocking {
        val changes = User().apply { id = fulano; name = "Só o nome"; userName = "ignorado"; roles = null }
        assertTrue(repo().update(changes, null, User().apply { name = ProjectionValues.str; roles = ProjectionValues.str }))

        val fetched = repo().fetchById(fulano)!!
        assertEquals("Só o nome", fetched.name)
        assertEquals("fulano", fetched.userName)
        assertNull(fetched.roles)
    }

    @Test
    fun update_defaultProjection_doesNotTouchThePassword() = runBlocking {
        val projection = User().apply { id = ProjectionValues.i64; password = ProjectionValues.str }
        val before = repo().fetchById(fulano, projection)!!.password

        assertTrue(repo().update(User().apply { id = fulano; userName = "fulano"; name = "Outro Nome"; roles = "CUSTOMER"; password = "nao-deve-gravar" }))

        assertEquals(before, repo().fetchById(fulano, projection)!!.password)
    }

    @Test
    fun update_withoutChanges_returnsFalse() = runBlocking {
        val original = repo().fetchById(fulano)!!
        val same = User().apply { id = original.id; userName = original.userName; name = original.name; roles = original.roles }
        assertFalse(repo().update(same, original))
    }

    @Test
    fun update_withoutId_isRefused() = runBlocking<Unit> {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().update(User().apply { name = "sem id" }) }
        }
    }

    // :: insertOrUpdate

    @Test
    fun insertOrUpdate_insertsWhenThereIsNoOldBean() = runBlocking {
        val user = User().apply { userName = "iou_user"; password = "digest"; name = "IOU Test" }

        assertTrue(repo().insertOrUpdate(user, null))
        assertNotNull(user.id)
        assertEquals("IOU Test", repo().fetchById(user.id!!, null)!!.name)
    }

    @Test
    fun insertOrUpdate_updatesWhenThereIsAnOldBean() = runBlocking {
        val original = repo().fetchById(admin)!!
        val user = User().apply { id = admin; userName = "admin"; name = "Updated Admin"; roles = original.roles }

        assertTrue(repo().insertOrUpdate(user, original))

        assertEquals("Updated Admin", repo().fetchById(admin, null)!!.name)
        assertEquals(3, repo().count(UserCriteria()))
    }

    // :: delete

    @Test
    fun deleteByUserId_noFkDependency() = runBlocking {
        assertEquals(1, repo().delete(UserCriteria().withUserId(beotrano)))
        assertEquals(2, repo().count(UserCriteria()))
    }

    @Test
    fun deleteNonExistent_returnsZero() = runBlocking {
        assertEquals(0, repo().delete(UserCriteria().withUserId(Long.MAX_VALUE)))
    }

    @Test
    fun delete_withEmptyCriteria_isRefused_andDeletesNothing() = runBlocking {
        assertThrows(BusinessException::class.java) {
            runBlocking { repo().delete(UserCriteria()) }
        }
        assertEquals(3, repo().count(UserCriteria()))
    }
}
