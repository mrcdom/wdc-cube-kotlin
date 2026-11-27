package com.example.tasks.detail.calls

import com.example.tasks.data.TaskCriteria
import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskDelete], in a transaction: `true` when the task existed. */
class TaskDeleteAction(taskId: Long) : TaskDelete(taskId) {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun delete(): Boolean = taskRepo.delete(TaskCriteria().withTaskId(taskId)) > 0
}
