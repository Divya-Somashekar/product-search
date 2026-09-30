package com.example.productsearch.product

import java.util.UUID

/**
 * Published inside the writing transaction; the search indexer reacts after commit,
 * so a rolled-back write never reaches the index.
 */
sealed interface ProductChangedEvent {
    val productId: UUID
}

data class ProductUpserted(
    val product: ProductResponse,
) : ProductChangedEvent {
    override val productId: UUID get() = product.id
}

data class ProductDeleted(
    override val productId: UUID,
) : ProductChangedEvent

class ProductNotFoundException(
    id: UUID,
) : RuntimeException("Product $id not found")
