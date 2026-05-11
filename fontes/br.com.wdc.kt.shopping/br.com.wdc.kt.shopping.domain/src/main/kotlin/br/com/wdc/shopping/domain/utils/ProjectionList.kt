package br.com.wdc.shopping.domain.utils

class ProjectionList<E>(bean: E, val criteria: Any?) : ArrayList<E>(1) {
    init {
        add(bean)
    }
}
