package com.example.productsearch.catalog.api

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal

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
