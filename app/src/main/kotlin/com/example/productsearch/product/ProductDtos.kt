package com.example.productsearch.product

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ProductRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,
    @field:Size(max = 5000)
    val description: String = "",
    @field:NotBlank
    @field:Size(max = 100)
    val category: String,
    @field:NotBlank
    @field:Size(max = 100)
    val brand: String,
    @field:DecimalMin("0.00")
    @field:Digits(integer = 10, fraction = 2)
    val price: BigDecimal,
    @field:PositiveOrZero
    val stockQuantity: Int = 0,
)

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

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long,
)

fun Product.toResponse() =
    ProductResponse(
        id = id,
        name = name,
        description = description,
        category = category,
        brand = brand,
        price = price,
        currency = currency,
        stockQuantity = stockQuantity,
        inStock = inStock,
        version = checkNotNull(version) { "product $id has not been persisted" },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
