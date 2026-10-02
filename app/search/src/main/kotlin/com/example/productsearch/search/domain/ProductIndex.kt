package com.example.productsearch.search.domain

import com.example.productsearch.catalog.contract.ProductSnapshot
import java.util.UUID

/**
 * The search index as the application uses it: query it, and keep it in step with the catalog.
 * Implemented in `search.infrastructure`; no layer above it names a search engine.
 */
interface ProductIndex {
    fun search(criteria: SearchCriteria): SearchResult

    fun index(product: ProductSnapshot)

    fun delete(productId: UUID)
}
