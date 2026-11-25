package br.com.wdc.framework.cube

/**
 * Where a presenter shows its scope: the parent hands it down through the intent (the keys' `parentSlot`) and keeps
 * what it receives in a field of its own scope, and the parent's view draws it. `null` empties it.
 */
fun interface ScopeSlot {
    fun setScope(scope: Scope?)
}
