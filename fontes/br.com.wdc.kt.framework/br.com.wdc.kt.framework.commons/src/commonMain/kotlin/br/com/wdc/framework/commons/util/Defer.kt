package br.com.wdc.framework.commons.util

class Defer : AutoCloseable {

    @Volatile
    private var actionStack: () -> Unit = {}

    fun push(action: () -> Unit) {
        actionStack = join(action, actionStack)
    }

    override fun close() {
        run()
    }

    fun run() {
        try {
            actionStack()
        } finally {
            actionStack = {}
        }
    }

    fun absorb(other: Defer) {
        actionStack = join(other.actionStack, actionStack)
        other.actionStack = {}
    }

    private companion object {
        fun join(a: () -> Unit, b: () -> Unit): () -> Unit = {
            a()
            b()
        }
    }
}
