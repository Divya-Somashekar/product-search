package com.example.productsearch.catalog.contract

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * A product as it was at one point in time: everything the catalog publishes about a product,
 * and the only shape in which it travels. Responses, change events and index documents are all
 * built from this, so no consumer ever sees the aggregate or a persistence type.
 */
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
