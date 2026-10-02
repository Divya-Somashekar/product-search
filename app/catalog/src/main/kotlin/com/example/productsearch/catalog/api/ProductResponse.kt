package com.example.productsearch.catalog.api

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ProductResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val category: String,
    val brand: String,
    val price: BigDecimal,
    val currency: String,
    val stockQuantity: Int,
    val inStock: Boolean,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    /**
     * A presigned link to the product image, or null if it has none. Valid for
     * `catalog.images.view-url-ttl`, so it is not worth a client caching or storing.
     */
    val imageUrl: String? = null,
)
