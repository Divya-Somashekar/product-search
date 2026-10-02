package com.example.productsearch.catalog.api

import com.example.productsearch.catalog.contract.ProductSnapshot
import com.example.productsearch.catalog.domain.ProductCommand
import com.example.productsearch.catalog.domain.ProductPage
import com.example.productsearch.shared.web.PageResponse

/** The HTTP edge of the catalog: validated requests in, snapshots out. */
fun ProductRequest.toCommand() =
    ProductCommand(
        name = name,
        description = description,
        category = category,
        brand = brand,
        price = price,
        stockQuantity = stockQuantity,
    )

fun ProductSnapshot.toResponse() =
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
        version = version,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

fun ProductPage.toResponse() = PageResponse(items.map(ProductSnapshot::toResponse), page, size, total)
