package com.example.productsearch.catalog.domain

import java.util.UUID

/**
 * Published inside the writing transaction; the search side reacts after commit,
 * so a rolled-back write never reaches the index.
 */
sealed interface ProductChangedEvent {
    val productId: UUID
}

data class ProductUpserted(
    val product: ProductSnapshot,
) : ProductChangedEvent {
    override val productId: UUID get() = product.id
}

data class ProductDeleted(
    override val productId: UUID,
) : ProductChangedEvent
