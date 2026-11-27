package com.example.tasks.persistence

import java.util.UUID
import kotlinx.coroutines.runBlocking

/** A database of the backend's own for each test: H2 in memory, under a name nobody else uses. */
object TestDatabases {

    fun memoryUrl(): String = "jdbc:h2:mem:test-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"

    fun open(url: String = memoryUrl(), seed: List<String> = emptyList()): AutoCloseable {
        val database = TaskDatabase.open(url)
        runBlocking { TaskDatabase.seedIfEmpty(seed) }
        return database
    }
}
