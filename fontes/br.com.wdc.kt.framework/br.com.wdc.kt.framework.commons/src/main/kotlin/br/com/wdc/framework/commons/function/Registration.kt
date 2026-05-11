package br.com.wdc.framework.commons.function

fun interface Registration {

    fun remove()

    companion object {
        @JvmStatic
        fun noop(): Registration = Registration { }
    }
}
