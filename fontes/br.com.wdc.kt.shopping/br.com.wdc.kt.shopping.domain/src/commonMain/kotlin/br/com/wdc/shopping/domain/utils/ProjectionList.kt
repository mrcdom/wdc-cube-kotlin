package br.com.wdc.shopping.domain.utils

class ProjectionList<E>(bean: E, val criteria: Any?) : MutableList<E> by mutableListOf() {
    init {
        add(bean)
    }
}
