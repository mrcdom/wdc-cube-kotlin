package br.com.wdc.framework.cube

import br.com.wdc.framework.commons.lang.CoerceUtils

/**
 * How an action bound with an event code reads one of its values from the form data a remote view sends
 * (`actionOf(2, FormParam.long("p.productId")) { … }`).
 */
class FormParam<T>(val name: String, private val decode: (Any?) -> T) {

    /** The value of the field in the form data, converted. */
    fun read(formData: Map<String, Any?>): T = decode(formData[name])

    companion object {
        fun long(name: String): FormParam<Long?> = FormParam(name) { CoerceUtils.asLong(it) }
        fun int(name: String): FormParam<Int?> = FormParam(name) { CoerceUtils.asInteger(it) }
        fun string(name: String): FormParam<String?> = FormParam(name) { CoerceUtils.asString(it) }
    }
}
