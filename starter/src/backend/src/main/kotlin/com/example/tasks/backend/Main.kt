package com.example.tasks.backend

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.Slf4jLogFactory
import br.com.wdc.framework.rest.RestApi
import com.example.tasks.persistence.TaskDatabase
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import kotlinx.coroutines.runBlocking

/** The backend's API: the calls' routes. */
fun tasksApi(): RestApi = RestApi {
    callRoutes()
}

/** The API, and the frontend's bundle (packed in the resources, under static/) at the root. */
fun createServer(): Javalin =
    tasksApi().createServer { config ->
        config.staticFiles.add { files ->
            files.directory = "/static"
            files.location = Location.CLASSPATH
        }
    }

fun main() {
    Log.setFactory(Slf4jLogFactory())
    // the tasks: an H2 database, in memory unless TASKS_DB_URL names another (a file keeps them between runs)
    val dbUrl = System.getenv("TASKS_DB_URL") ?: TaskDatabase.MEMORY_URL
    TaskDatabase.open(dbUrl)
    runBlocking { TaskDatabase.seedIfEmpty(listOf("Read the README", "Open a task", "Add your own")) }

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8090
    createServer().start(port)
    println("Tasks on http://localhost:$port/")
}
