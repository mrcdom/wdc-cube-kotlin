package com.example.tasks.data

/** The tasks in memory, for the starter and its tests: they are lost when the backend stops. */
class InMemoryTaskRepository(initial: List<String> = emptyList()) : TaskRepository {

    private val tasks = linkedMapOf<Long, Task>()
    private var nextId = 1L

    init {
        initial.forEach { title -> newTask(title) }
    }

    override suspend fun fetchAll(): List<Task> = tasks.values.toList()

    override suspend fun fetchById(id: Long): Task? = tasks[id]

    override suspend fun insert(title: String): Task = newTask(title)

    override suspend fun update(task: Task): Boolean {
        if (task.id !in tasks) return false
        tasks[task.id] = task
        return true
    }

    override suspend fun delete(id: Long): Boolean = tasks.remove(id) != null

    private fun newTask(title: String): Task = Task(id = nextId++, title = title).also { tasks[it.id] = it }
}
