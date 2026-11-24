package br.com.wdc.framework.cube

/**
 * An entry point of a presenter, kept in a field of its [Scope] (`scope.onBuy`): the view calls it, inside its own
 * launch (`safeCall { scope.onBuy() }`); a test or a remote view calls it directly and sees the result on the next
 * line. The presenter binds it in its constructor with `action(...)` ([AbstractPresenter]), which guards it.
 */
fun interface Action {
    suspend operator fun invoke()

    companion object {
        /** Does nothing: the value of an action field before the presenter binds it. */
        val NOOP: Action = Action {}
    }
}

/** An [Action] that takes a value from the view. */
fun interface ActionOf<T> {
    suspend operator fun invoke(value: T)

    companion object {
        fun <T> noop(): ActionOf<T> = ActionOf {}
    }
}

/** An [Action] that takes two values from the view. */
fun interface ActionOf2<T, U> {
    suspend operator fun invoke(first: T, second: U)

    companion object {
        fun <T, U> noop(): ActionOf2<T, U> = ActionOf2 { _, _ -> }
    }
}
