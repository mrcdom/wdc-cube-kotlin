package com.example.tasks.list.calls

import com.example.tasks.data.Task
import com.example.tasks.data.TaskCriteria
import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskListQuery]: every task in the repository, in the order they were created. */
class TaskListQueryAction : TaskListQuery() {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun run(): List<Task> = taskRepo.fetch(TaskCriteria()).map { it.toTask() }
}
