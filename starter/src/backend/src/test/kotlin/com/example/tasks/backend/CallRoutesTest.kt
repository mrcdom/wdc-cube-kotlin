package com.example.tasks.backend

import br.com.wdc.framework.rest.testing.RestTestServer
import com.example.tasks.persistence.TestDatabases
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The backend's side of the calls, over real HTTP: the JSON the frontend's side sends and reads. */
class CallRoutesTest {

    private lateinit var database: AutoCloseable

    @BeforeTest
    fun setUp() {
        database = TestDatabases.open(seed = listOf("Write the README"))
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun creating_andListing() {
        RestTestServer(tasksApi()).use { server ->
            val created = server.client().post("/api/calls/task-create", """{"title":"Ship it"}""")
            assertEquals(200, created.status)
            assertEquals("""{"id":2,"title":"Ship it"}""", created.body)

            val list = server.client().post("/api/calls/task-list", "{}")
            assertEquals("""[{"id":1,"title":"Write the README"},{"id":2,"title":"Ship it"}]""", list.body)
        }
    }

    @Test
    fun aTaskThatDoesNotExist_is404_orNull() {
        RestTestServer(tasksApi()).use { server ->
            assertEquals(404, server.client().post("/api/calls/task-done-update", """{"taskId":99,"done":true}""").status)
            assertEquals("null", server.client().post("/api/calls/task", """{"taskId":99}""").body)
        }
    }

    @Test
    fun aMalformedRequest_orAnEmptyTitle_is400() {
        RestTestServer(tasksApi()).use { server ->
            assertEquals(400, server.client().post("/api/calls/task-create", """{"name":"no title"}""").status)
            assertEquals(400, server.client().post("/api/calls/task-create", """{"title":"  "}""").status)
        }
    }
}
