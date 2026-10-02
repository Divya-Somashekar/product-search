package com.example.productsearch.shared.web

/** A page of results as it goes over the wire; shared by every paged endpoint. */
data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
)
