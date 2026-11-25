package com.example.tasks.backend

import br.com.wdc.framework.commons.log.Log
import br.com.wdc.framework.commons.log.Slf4jLogFactory
import br.com.wdc.framework.rest.RestApi
import com.example.tasks.data.InMemoryTaskRepository
import com.example.tasks.data.TaskRepository
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location

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
    // the data the calls answer with: in memory here, a database in a real application
    TaskRepository.BEAN.set(InMemoryTaskRepository(listOf("Read the README", "Open a task", "Add your own")))

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8090
    createServer().start(port)
    println("Tasks on http://localhost:$port/")
}
