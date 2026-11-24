package br.com.wdc.framework.cube

/**
 * Where a presenter shows its scope: the parent hands it down through the intent (the keys' `parentSlot`) and keeps
 * what it receives in a field of its own scope; the parent's view asks the application for the view of that scope
 * ([CubeApplication.viewOf]). `null` empties it.
 */
fun interface ScopeSlot {
    fun setScope(scope: Scope?)
}
