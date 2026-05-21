package br.com.wdc.shopping.test.repository

import br.com.wdc.shopping.domain.criteria.UserCriteria
import kotlinx.coroutines.runBlocking
import br.com.wdc.shopping.domain.model.User
import br.com.wdc.shopping.domain.repositories.UserRepository
import br.com.wdc.shopping.domain.utils.ProjectionValues
import br.com.wdc.shopping.scripts.sgbd.DBReset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

abstract class AbstractUserRepositoryTest {

    protected abstract fun repo(): UserRepository

    // :: fetch

    @Test
    fun fetchAll_returnsAllSeededUsers() = runBlocking {
        val users = repo().fetch(UserCriteria())
        assertEquals(3, users.size)
    }

    @Test
    fun fetchById_returnsCorrectUser() = runBlocking {
        val user = repo().fetchById(DBReset.ADMIN_ID, null)
        assertNotNull(user)
        assertEquals("admin", user!!.userName)
        assertEquals("João da Silva", user.name)
    }

    @Test
    fun fetchById_nonExistent_returnsNull() = runBlocking {
        val user = repo().fetchById(Long.MAX_VALUE, null)
        assertNull(user)
    }

    @Test
    fun fetchWithProjection_onlyRequestedFields() = runBlocking {
        val pv = ProjectionValues
        val projection = User()
        projection.id = pv.i64
        projection.userName = pv.str

        val user = repo().fetchById(DBReset.ADMIN_ID, projection)
        assertNotNull(user)
        assertEquals(DBReset.ADMIN_ID, user!!.id)
        assertEquals("admin", user.userName)
    }

    @Test
    fun fetchByCriteria_userName() = runBlocking {
        val users = repo().fetch(UserCriteria().withUserName("fulano"))
        assertEquals(1, users.size)
        assertEquals(DBReset.FULANO_ID, users[0].id)
    }

    @Test
    fun fetchByCriteria_userNameAndPassword() = runBlocking {
        val users = repo().fetch(
            UserCriteria()
                .withUserName("admin")
                .withPassword("admin")
        )
        assertEquals(1, users.size)
        assertEquals(DBReset.ADMIN_ID, users[0].id)
    }

    @Test
    fun fetchByCriteria_wrongPassword_returnsEmpty() = runBlocking {
        val users = repo().fetch(
            UserCriteria()
                .withUserName("admin")
                .withPassword("wrong")
        )
        assertTrue(users.isEmpty())
    }

    @Test
    fun fetchWithOffsetAndLimit() = runBlocking {
        val users = repo().fetch(
            UserCriteria()
                .withOrderBy(UserCriteria.OrderBy.ASCENDING)
                .withOffset(1)
                .withLimit(1)
        )
        assertEquals(1, users.size)
    }

    // :: count

    @Test
    fun countAll_returnsThree() = runBlocking {
        val count = repo().count(UserCriteria())
        assertEquals(3, count)
    }

    @Test
    fun countByUserId_returnsOne() = runBlocking {
        val count = repo().count(UserCriteria().withUserId(DBReset.ADMIN_ID))
        assertEquals(1, count)
    }

    @Test
    fun countByNonExistentId_returnsZero() = runBlocking {
        val count = repo().count(UserCriteria().withUserId(Long.MAX_VALUE))
        assertEquals(0, count)
    }

    // :: insert

    @Test
    fun insert_newUser() = runBlocking {
        val user = User()
        user.userName = "newuser"
        user.password = "secret"
        user.name = "New User"

        val inserted = repo().insert(user)
        assertTrue(inserted)
        assertNotNull(user.id)

        val fetched = repo().fetchById(user.id!!, null)
        assertNotNull(fetched)
        assertEquals("newuser", fetched!!.userName)
        assertEquals("New User", fetched.name)
    }

    // :: update

    @Test
    fun update_existingUser() = runBlocking {
        val pv = ProjectionValues
        val fullProjection = User()
        fullProjection.id = pv.i64
        fullProjection.userName = pv.str
        fullProjection.password = pv.str
        fullProjection.name = pv.str

        val original = repo().fetchById(DBReset.ADMIN_ID, fullProjection)
        assertNotNull(original)

        val updated = User()
        updated.id = original!!.id
        updated.userName = original.userName
        updated.password = original.password
        updated.name = "Nome Alterado"

        val result = repo().update(updated, original)
        assertTrue(result)

        val fetched = repo().fetchById(DBReset.ADMIN_ID, null)
        assertEquals("Nome Alterado", fetched!!.name)
    }

    // :: insertOrUpdate

    @Test
    fun insertOrUpdate_insertsWhenNew() = runBlocking {
        val user = User()
        user.userName = "iou_user"
        user.password = "pass"
        user.name = "IOU Test"

        val result = repo().insertOrUpdate(user)
        assertTrue(result)
        assertNotNull(user.id)

        val fetched = repo().fetchById(user.id!!, null)
        assertEquals("IOU Test", fetched!!.name)
    }

    @Test
    fun insertOrUpdate_updatesWhenExisting() = runBlocking {
        val user = User()
        user.id = DBReset.ADMIN_ID
        user.userName = "admin"
        user.password = "admin"
        user.name = "Updated Admin"

        val result = repo().insertOrUpdate(user)
        assertTrue(result)

        val fetched = repo().fetchById(DBReset.ADMIN_ID, null)
        assertEquals("Updated Admin", fetched!!.name)
    }

    // :: delete

    @Test
    fun deleteByUserId_noFkDependency() = runBlocking {
        val deleted = repo().delete(UserCriteria().withUserId(DBReset.BEOTRANO_ID))
        assertEquals(1, deleted)
        assertEquals(2, repo().count(UserCriteria()))
    }

    @Test
    fun deleteNonExistent_returnsZero() = runBlocking {
        val deleted = repo().delete(UserCriteria().withUserId(Long.MAX_VALUE))
        assertEquals(0, deleted)
    }
}
