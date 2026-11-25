package com.example.tasks.detail.calls

import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskDelete], in a transaction when one is registered. */
class TaskDeleteAction(taskId: Long) : TaskDelete(taskId) {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun delete(): Boolean = taskRepo.delete(taskId)
}
