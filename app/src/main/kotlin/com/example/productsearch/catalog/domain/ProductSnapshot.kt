package com.example.productsearch.catalog.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** A product as it was at one point in time. See [Product.snapshot]. */
data class ProductSnapshot(
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
)
