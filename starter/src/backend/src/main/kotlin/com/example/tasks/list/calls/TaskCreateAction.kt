package com.example.tasks.list.calls

import com.example.tasks.data.Task
import com.example.tasks.data.TaskRepository

/** The backend's answer to [TaskCreate]: a new task in the repository, in a transaction when one is registered. */
class TaskCreateAction(title: String) : TaskCreate(title) {

    private val taskRepo: TaskRepository get() = TaskRepository.BEAN.get()

    override suspend fun create(): Task = taskRepo.insert(title)
}
