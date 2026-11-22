package br.com.wdc.framework.domain.pagination

import kotlin.math.ceil

/**
 * Paginated result of a query.
 *
 * @param items      items of the current page
 * @param page       page number (0-based)
 * @param totalPages total number of pages
 * @param totalItems total number of items without pagination
 */
data class Page<T>(val items: List<T>, val page: Int, val totalPages: Int, val totalItems: Int) {

    companion object {
        /**
         * Builds a [Page] computing `totalPages` from [pageSize] (0 when `pageSize <= 0`).
         */
        fun <T> of(items: List<T>, page: Int, pageSize: Int, totalItems: Int): Page<T> {
            val totalPages = if (pageSize > 0) ceil(totalItems.toDouble() / pageSize).toInt() else 0
            return Page(items.toList(), page, totalPages, totalItems)
        }
    }
}
