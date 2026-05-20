package br.com.wdc.shopping.domain.repositories

data class Page<T>(
    val items: List<T>,
    val totalCount: Int
)
