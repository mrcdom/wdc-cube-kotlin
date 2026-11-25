package com.example.tasks.detail.calls

import com.example.tasks.data.Task
import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskQuery]: the repository's task, or `null`. */
class TaskQueryAction(taskId: Long) : TaskQuery(taskId) {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun run(): Task? = taskRepo.fetchById(taskId)
}
