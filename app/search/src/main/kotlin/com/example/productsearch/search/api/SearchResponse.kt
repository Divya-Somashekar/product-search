package com.example.productsearch.search.api

import java.math.BigDecimal

data class SearchResponse(
    val total: Long,
    val page: Int,
    val size: Int,
    val items: List<ProductHitResponse>,
    val facets: Map<String, List<FacetBucketResponse>>,
)

data class ProductHitResponse(
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

data class FacetBucketResponse(
    val value: String,
    val count: Long,
)
