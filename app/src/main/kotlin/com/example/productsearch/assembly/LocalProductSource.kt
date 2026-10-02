package com.example.productsearch.assembly

import com.example.productsearch.catalog.application.ProductService
import com.example.productsearch.catalog.contract.ProductSnapshot
import com.example.productsearch.search.domain.ProductSource
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Serves search's [ProductSource] straight from the catalog's use cases, with no HTTP in between.
 *
 * It lives in the composition root because that is the only place allowed to know both contexts:
 * `search` must not import `catalog` beyond the contract, and `catalog` must never depend on
 * `search` at all. Wiring the two together is the assembly's job, not either module's.
 *
 * Splitting the services replaces this with an adapter inside the search service that calls
 * `GET /internal/products` — the same port and the same keyset contract, so nothing in
 * `ReindexService` changes.
 */
@Component
class LocalProductSource(
    private val catalog: ProductService,
) : ProductSource {
    override fun page(
        after: UUID?,
        size: Int,
    ): List<ProductSnapshot> = catalog.export(after, size)
}
