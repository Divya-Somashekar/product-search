package com.example.productsearch.search.infrastructure

import com.example.productsearch.catalog.contract.ProductSnapshot
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The shape of a product in Elasticsearch. Converted to and from plain maps explicitly so the
 * index contract does not depend on how the client's JSON mapper treats Kotlin classes.
 */
data class ProductDocument(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val brand: String,
    val price: BigDecimal,
    val currency: String,
    val stockQuantity: Int,
    val inStock: Boolean,
    val createdAt: String,
    val updatedAt: String,
) {
    fun toSource(): Map<String, Any> =
        mapOf(
            "id" to id,
            "name" to name,
            "description" to description,
            "category" to category,
            "brand" to brand,
            "price" to price,
            "currency" to currency,
            "stockQuantity" to stockQuantity,
            "inStock" to inStock,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt,
        )

    companion object {
        fun of(product: ProductSnapshot) =
            ProductDocument(
                id = product.id.toString(),
                name = product.name,
                description = product.description,
                category = product.category,
                brand = product.brand,
                price = product.price,
                currency = product.currency,
                stockQuantity = product.stockQuantity,
                inStock = product.inStock,
                createdAt = product.createdAt.toString(),
                updatedAt = product.updatedAt.toString(),
            )

        fun fromSource(source: Map<*, *>) =
            ProductDocument(
                id = source["id"].toString(),
                name = source["name"].toString(),
                description = source["description"]?.toString().orEmpty(),
                category = source["category"].toString(),
                brand = source["brand"].toString(),
                // scaled_float returns the value as sent, but JSON parsing may hand back a double.
                price = BigDecimal(source["price"].toString()).setScale(2, RoundingMode.HALF_UP),
                currency = source["currency"].toString(),
                stockQuantity = (source["stockQuantity"] as Number).toInt(),
                inStock = source["inStock"] as Boolean,
                createdAt = source["createdAt"].toString(),
                updatedAt = source["updatedAt"].toString(),
            )
    }
}
