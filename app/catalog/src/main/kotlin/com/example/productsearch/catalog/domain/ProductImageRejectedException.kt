package com.example.productsearch.catalog.domain

/** An image a seller offered cannot be accepted. Always the caller's fault, so always a 400. */
class ProductImageRejectedException(
    override val message: String,
) : RuntimeException(message)
