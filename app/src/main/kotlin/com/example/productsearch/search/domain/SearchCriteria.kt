package com.example.productsearch.search.domain

import java.math.BigDecimal

/** Everything a search request asks for, independent of how it arrived or how it is executed. */
data class SearchCriteria(
    val query: String? = null,
    val categories: List<String> = emptyList(),
    val brands: List<String> = emptyList(),
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
    val inStock: Boolean? = null,
    val sort: SortOption = SortOption.RELEVANCE,
    val page: Int = 0,
    val size: Int = 20,
) {
    val from: Int get() = page * size
}
