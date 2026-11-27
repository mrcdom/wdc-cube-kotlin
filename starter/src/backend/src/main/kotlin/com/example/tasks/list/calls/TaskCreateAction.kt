package com.example.tasks.list.calls

import com.example.tasks.data.Task
import com.example.tasks.data.TaskEntity
import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskCreate]: a new task in the repository, in a transaction. */
class TaskCreateAction(title: String) : TaskCreate(title) {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun create(): Task {
        val newTitle = title // inside apply, `title` would be the entity's
        val task = TaskEntity().apply {
            title = newTitle
            done = false
        }
        check(taskRepo.insert(task)) { "The task was not inserted" }
        // the insert read the id the database made back into the entity
        return task.toTask()
    }
}
