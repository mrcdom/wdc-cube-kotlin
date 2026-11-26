package com.example.tasks.web

import com.example.tasks.RootScope
import com.example.tasks.detail.TaskScope
import com.example.tasks.list.TaskListScope
import com.example.tasks.list.TaskRowScope
import react.FC
import react.Key
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.form
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.label
import react.dom.html.ReactHTML.li
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.ul
import react.useState
import web.cssom.ClassName
import web.html.InputType
import web.html.checkbox

// Views only read their scope and call its actions (inside safeCall); they hold no logic.

val RootView = FC<ScopeProps<RootScope>> { props ->
    val scope = useScope(props.scope)
    div {
        className = ClassName("page")
        h1 { +"Tasks" }
        scope.errorMessage?.let { message -> p { className = ClassName("error"); +message } }
        Slot { this.scope = scope.content }
    }
}

val TaskListView = FC<ScopeProps<TaskListScope>> { props ->
    val scope = useScope(props.scope)
    val (title, setTitle) = useState("")

    div {
        className = ClassName("columns")
        div {
            form {
                onSubmit = { event ->
                    event.preventDefault()
                    val typed = title
                    setTitle("")
                    safeCall { scope.onAdd(typed) }
                }
                input {
                    placeholder = "What is there to do?"
                    value = title
                    onChange = { setTitle(it.target.value) }
                }
                button { +"Add" }
            }
            scope.errorMessage?.let { message -> p { className = ClassName("error"); +message } }
            ul {
                for (row in scope.rows) {
                    Slot {
                        key = Key(keyOf(row))
                        this.scope = row
                    }
                }
            }
            p { className = ClassName("muted"); +"${scope.remaining} left" }
        }
        Slot { this.scope = scope.detail }
    }
}

val TaskRowView = FC<ScopeProps<TaskRowScope>> { props ->
    val row = useScope(props.scope)
    li {
        label {
            input {
                type = InputType.checkbox
                checked = row.done
                onChange = { safeCall { row.onToggle() } }
            }
            span {
                className = ClassName(if (row.done) "done" else "")
                +row.title
            }
        }
        button {
            className = ClassName("link")
            onClick = { safeCall { row.onOpen() } }
            +"Open"
        }
    }
}

val TaskView = FC<ScopeProps<TaskScope>> { props ->
    val scope = useScope(props.scope)

    div {
        className = ClassName("detail")
        h2 { +"Task ${scope.id}" }
        // keyed by the task: opening another task reuses this presenter, its scope and this view, but the form is a
        // new component, whose text starts from the new title instead of keeping the one being typed for the last
        TaskTitleForm {
            key = Key(scope.id.toString())
            this.scope = scope
        }
        scope.errorMessage?.let { message -> p { className = ClassName("error"); +message } }
        p { className = ClassName("muted"); +(if (scope.done) "Done." else "Not done yet.") }
        div {
            button { onClick = { safeCall { scope.onRemove() } }; +"Remove" }
            button { onClick = { safeCall { scope.onClose() } }; +"Close" }
        }
    }
}

/** The title being typed is the view's own state, started from the scope's. */
private val TaskTitleForm = FC<ScopeProps<TaskScope>> { props ->
    val scope = props.scope
    val (title, setTitle) = useState(scope.title)

    form {
        onSubmit = { event ->
            event.preventDefault()
            safeCall { scope.onRename(title) }
        }
        input {
            value = title
            onChange = { setTitle(it.target.value) }
        }
        button { +"Save" }
    }
}

/** The catalog: which component draws each scope. Declared after the views, which it refers to. */
val catalog: ScopeViews = ScopeViews()
    .register(RootScope::class, RootView)
    .register(TaskListScope::class, TaskListView)
    .register(TaskRowScope::class, TaskRowView)
    .register(TaskScope::class, TaskView)
