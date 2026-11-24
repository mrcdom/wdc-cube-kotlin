package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.serialization.ExtensibleObjectOutput

/**
 * A value a [Scope] field can hold and send to a remote view: it writes itself as one JSON value (usually an
 * object). Plain strings, numbers, booleans, enums, lists and maps need nothing; a struct of the presentation
 * implements this.
 */
fun interface ScopeValue {
    fun writeJson(json: ExtensibleObjectOutput)
}
