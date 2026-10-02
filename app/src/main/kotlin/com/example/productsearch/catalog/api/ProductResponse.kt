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
)
