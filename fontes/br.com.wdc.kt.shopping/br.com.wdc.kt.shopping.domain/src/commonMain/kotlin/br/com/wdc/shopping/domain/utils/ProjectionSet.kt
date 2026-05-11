package br.com.wdc.shopping.domain.utils

class ProjectionSet<E>(bean: E, val criteria: Any?) : HashSet<E>(1) {
    init {
        add(bean)
    }
}
