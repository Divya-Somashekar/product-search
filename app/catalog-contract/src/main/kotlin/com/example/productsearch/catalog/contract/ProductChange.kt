package com.example.productsearch.catalog.contract

import java.util.UUID

/**
 * One entry of the catalog's change log, as a consumer reads it.
 *
 * [product] carries the state to apply; `null` means the product is gone and should be removed
 * from the projection. A consumer remembers the highest [seq] it has applied and asks for what
 * comes after it.
 *
 * Deliberately flat rather than a sealed hierarchy: this crosses a process boundary as JSON, and a
 * flat shape needs no type discriminator, which keeps this module free of serialization
 * annotations and therefore of any dependency.
 */
data class ProductChange(
    val seq: Long,
    val productId: UUID,
    val product: ProductSnapshot?,
)
