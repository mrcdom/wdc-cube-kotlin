package com.example.tasks.detail.calls

import com.example.tasks.data.Task
import com.example.tasks.data.TaskNotFoundException
import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskTitleUpdate], in a transaction when one is registered. */
class TaskTitleUpdateAction(taskId: Long, title: String) : TaskTitleUpdate(taskId, title) {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun update(): Task {
        val task = taskRepo.fetchById(taskId) ?: throw TaskNotFoundException(taskId)
        val changed = task.copy(title = title)
        if (!taskRepo.update(changed)) throw TaskNotFoundException(taskId)
        return changed
    }
}
