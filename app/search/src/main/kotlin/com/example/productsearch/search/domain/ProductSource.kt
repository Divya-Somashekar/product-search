package com.example.productsearch.search.domain

import com.example.productsearch.catalog.contract.ProductSnapshot
import java.util.UUID

/**
 * Where a rebuild reads the catalog. The index is a projection, and a rebuild is the one thing in
 * search that needs the source of truth rather than the index.
 *
 * This port is what keeps `search` free of any import from `catalog` beyond the contract: in one
 * process the composition root implements it straight from the catalog's use cases, and in a
 * split deployment the search service implements it over `GET /internal/products`. Both are the
 * same keyset contract — pass the last id seen as [after], and a page shorter than [size] is the
 * last one.
 */
fun interface ProductSource {
    fun page(
        after: UUID?,
        size: Int,
    ): List<ProductSnapshot>
}
