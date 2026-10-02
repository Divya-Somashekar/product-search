package com.example.productsearch.catalog.domain

import java.math.BigDecimal

/**
 * The writable fields of a product, already free of transport concerns. Both create and update
 * take one; the API layer validates its own request type and maps it here.
 */
data class ProductCommand(
    val name: String,
    val description: String,
    val category: String,
    val brand: String,
    val price: BigDecimal,
    val stockQuantity: Int,
)
