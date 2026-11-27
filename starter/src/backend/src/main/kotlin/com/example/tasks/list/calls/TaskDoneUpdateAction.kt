package com.example.tasks.list.calls

import com.example.tasks.data.Task
import com.example.tasks.data.TaskEntity
import com.example.tasks.data.TaskNotFoundException
import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskDoneUpdate], in a transaction: the update and the read after it see the same data. */
class TaskDoneUpdateAction(taskId: Long, done: Boolean) : TaskDoneUpdate(taskId, done) {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun update(): Task {
        // only the fields with a value are written: here, done
        val newDone = done // inside apply, `done` would be the entity's
        val changed = TaskEntity().apply {
            id = taskId
            done = newDone
        }
        if (!taskRepo.update(changed)) throw TaskNotFoundException(taskId)
        return taskRepo.fetchById(taskId)?.toTask() ?: throw TaskNotFoundException(taskId)
    }
}
