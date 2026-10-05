package br.com.wdc.framework.domain.pagination

import kotlin.math.ceil

/**
 * Resultado paginado de uma consulta.
 *
 * @param items      itens da página atual
 * @param page       número da página (base 0)
 * @param totalPages total de páginas
 * @param totalItems total de itens sem paginação
 */
data class Page<T>(val items: List<T>, val page: Int, val totalPages: Int, val totalItems: Int) {

    companion object {
        /**
         * Constrói uma [Page] calculando `totalPages` a partir de [pageSize] (0 quando `pageSize <= 0`).
         */
        fun <T> of(items: List<T>, page: Int, pageSize: Int, totalItems: Int): Page<T> {
            val totalPages = if (pageSize > 0) ceil(totalItems.toDouble() / pageSize).toInt() else 0
            return Page(items.toList(), page, totalPages, totalItems)
        }
    }
}
