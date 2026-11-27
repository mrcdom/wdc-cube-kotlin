package com.example.tasks.detail.calls

import com.example.tasks.data.Task
import com.example.tasks.data.TaskEntity
import com.example.tasks.data.TaskNotFoundException
import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskTitleUpdate], in a transaction: the update and the read after it see the same data. */
class TaskTitleUpdateAction(taskId: Long, title: String) : TaskTitleUpdate(taskId, title) {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun update(): Task {
        // only the fields with a value are written: here, title
        val newTitle = title // inside apply, `title` would be the entity's
        val changed = TaskEntity().apply {
            id = taskId
            title = newTitle
        }
        if (!taskRepo.update(changed)) throw TaskNotFoundException(taskId)
        return taskRepo.fetchById(taskId)?.toTask() ?: throw TaskNotFoundException(taskId)
    }
}
