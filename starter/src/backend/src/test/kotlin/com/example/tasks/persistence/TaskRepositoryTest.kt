package com.example.tasks.persistence

import com.example.tasks.data.TaskCriteria
import com.example.tasks.data.TaskEntity
import com.example.tasks.data.TaskRepository
import com.example.tasks.data.TaskTransactions
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The repository on a real H2 database: criteria, projections, partial updates, transactions, a file that stays. */
class TaskRepositoryTest {

    private lateinit var database: AutoCloseable

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    @BeforeTest
    fun setUp() {
        database = TestDatabases.open(seed = listOf("Write", "Test", "Ship"))
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun anInsert_readsTheIdTheDatabaseMade() = runBlocking {
        val task = TaskEntity().apply {
            title = "Review"
            done = false
        }
        assertTrue(taskRepo.insert(task))
        assertEquals(4L, task.id)
        assertEquals("Review", taskRepo.fetchById(4L)?.title)
    }

    @Test
    fun criteria_filter_andAProjection_bringsOnlyItsFields() = runBlocking {
        taskRepo.update(TaskEntity().apply { id = 2L; done = true })

        val done = taskRepo.fetch(TaskCriteria().withDone(true))
        assertEquals(listOf("Test"), done.map { it.title })
        assertEquals(2, taskRepo.count(TaskCriteria().withDone(false)))
        assertEquals(listOf("Ship"), taskRepo.fetch(TaskCriteria().apply { title.like("Sh%") }).map { it.title })

        val titlesOnly = TaskEntity().apply { title = "~" }
        val first = taskRepo.fetchById(id = 1L, projection = titlesOnly)!!
        assertEquals("Write", first.title)
        assertNull(first.done)
    }

    @Test
    fun anUpdate_writesOnlyTheFieldsWithAValue() = runBlocking {
        assertTrue(taskRepo.update(TaskEntity().apply { id = 1L; done = true }))
        val task = taskRepo.fetchById(1L)!!
        assertEquals("Write", task.title)
        assertEquals(true, task.done)

        assertFalse(taskRepo.update(TaskEntity().apply { id = 99L; done = true }))
    }

    @Test
    fun aDelete_needsACriterion() = runBlocking {
        assertFailsWith<Exception> { taskRepo.delete(TaskCriteria()) }
        assertEquals(1, taskRepo.delete(TaskCriteria().withTaskId(3L)))
        assertEquals(2, taskRepo.count(TaskCriteria()))
    }

    @Test
    fun aTransactionThatFails_writesNothing() = runBlocking {
        assertFailsWith<IllegalStateException> {
            TaskTransactions.BEAN.get().required {
                taskRepo.insert(TaskEntity().apply { title = "Half done"; done = false })
                taskRepo.delete(TaskCriteria().withTaskId(1L))
                error("something went wrong after the writes")
            }
        }
        assertEquals(listOf("Write", "Test", "Ship"), taskRepo.fetch(TaskCriteria()).map { it.title })
    }

    @Test
    fun aDatabaseFile_keepsTheTasks_fromOneRunToTheNext() = runBlocking {
        val dir = Files.createTempDirectory("tasks-db")
        try {
            val url = "jdbc:h2:file:${dir.resolve("tasks")}"
            TestDatabases.open(url = url, seed = listOf("Seeded once")).use {
                taskRepo.insert(TaskEntity().apply { title = "Added"; done = false })
            }
            // the seed only goes into an empty database: the second run finds the first one's tasks
            TestDatabases.open(url = url, seed = listOf("Seeded once")).use {
                assertEquals(listOf("Seeded once", "Added"), taskRepo.fetch(TaskCriteria()).map { it.title })
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
