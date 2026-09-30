package com.example.productsearch.search

import java.math.BigDecimal

enum class SortOption(
    val param: String,
) {
    RELEVANCE("relevance"),
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc"),
    ;

    companion object {
        fun fromParam(value: String): SortOption =
            entries.firstOrNull { it.param.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("sort must be one of ${entries.joinToString { it.param }}")
    }
}

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

data class SearchResult(
    val total: Long,
    val page: Int,
    val size: Int,
    val items: List<ProductHit>,
    val facets: Map<String, List<FacetBucket>>,
)

data class ProductHit(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val brand: String,
    val price: BigDecimal,
    val currency: String,
    val inStock: Boolean,
    /** Relevance score; present on every sort so clients can show why an item ranked where it did. */
    val score: Double?,
    /** Matched fragments per field, HTML-escaped with matches wrapped in `<em>`. */
    val highlights: Map<String, List<String>>,
)

data class FacetBucket(
    val value: String,
    val count: Long,
)

class InvalidSearchRequestException(
    message: String,
) : RuntimeException(message)
