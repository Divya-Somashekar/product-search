package com.example.productsearch.catalog.domain

import java.util.UUID

class ProductNotFoundException(
    id: UUID,
) : RuntimeException("Product $id not found")
