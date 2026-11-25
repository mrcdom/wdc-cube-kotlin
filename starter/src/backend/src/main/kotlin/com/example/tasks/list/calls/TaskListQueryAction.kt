package com.example.tasks.list.calls

import com.example.tasks.data.Task
import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskListQuery]: the repository's tasks. */
class TaskListQueryAction : TaskListQuery() {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun run(): List<Task> = taskRepo.fetchAll()
}
