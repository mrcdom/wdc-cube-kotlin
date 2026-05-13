package br.com.wdc.shopping.domain.utils

class ProjectionSet<E>(bean: E, val criteria: Any?) : MutableSet<E> by mutableSetOf() {
    init {
        add(bean)
    }
}
