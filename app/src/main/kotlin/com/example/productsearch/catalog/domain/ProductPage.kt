package com.example.productsearch.catalog.domain

/** One page of products, with the total so callers can size their paging controls. */
data class ProductPage(
    val items: List<ProductSnapshot>,
    val page: Int,
    val size: Int,
    val total: Long,
)
