package com.example.productsearch.catalog.api

import com.example.productsearch.catalog.contract.ProductSnapshot
import com.example.productsearch.catalog.domain.ProductCommand
import com.example.productsearch.catalog.domain.ProductPage
import com.example.productsearch.shared.web.PageResponse
import java.net.URI

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

/**
 * [imageUrl] is passed in rather than resolved here: presigning needs the storage port, and these
 * stay pure functions of a snapshot so the caller decides when a link is worth minting.
 */
fun ProductSnapshot.toResponse(imageUrl: URI? = null) =
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
        imageUrl = imageUrl?.toString(),
    )

fun ProductPage.toResponse(imageUrl: (ProductSnapshot) -> URI?) = PageResponse(items.map { it.toResponse(imageUrl(it)) }, page, size, total)
