package com.example.tasks.list.calls

import com.example.tasks.data.Task
import com.example.tasks.data.TaskNotFoundException
import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskDoneUpdate], in a transaction when one is registered. */
class TaskDoneUpdateAction(taskId: Long, done: Boolean) : TaskDoneUpdate(taskId, done) {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun update(): Task {
        val task = taskRepo.fetchById(taskId) ?: throw TaskNotFoundException(taskId)
        val changed = task.copy(done = done)
        if (!taskRepo.update(changed)) throw TaskNotFoundException(taskId)
        return changed
    }
}
