package com.example.productsearch.catalog.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** The catalog's persistence port. Postgres is the source of truth; the search index is derived. */
interface ProductRepository : JpaRepository<Product, UUID>
