package com.example.tasks.data

import br.com.wdc.framework.domain.exception.NotFoundException
import kotlinx.serialization.Serializable

/** A task to do. It travels between the frontend and the backend as JSON. */
@Serializable
data class Task(val id: Long, val title: String, val done: Boolean = false)

/** The task asked for does not exist (anymore). The backend answers it with 404. */
class TaskNotFoundException(taskId: Long) : NotFoundException("No task $taskId")
