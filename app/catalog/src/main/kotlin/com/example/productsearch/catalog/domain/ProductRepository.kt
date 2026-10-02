package com.example.productsearch.catalog.domain

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** The catalog's persistence port. Postgres is the source of truth; the search index is derived. */
interface ProductRepository : JpaRepository<Product, UUID> {
    /**
     * Keyset pagination for a full export, ordered by id.
     *
     * Deliberately not offset paging: a rebuild runs while writes continue, and an offset shifts
     * under inserts and deletes, so pages would skip rows and the index would come out silently
     * short. Reading strictly after the last id seen cannot skip anything that was there when the
     * page was taken.
     */
    fun findAllByOrderByIdAsc(limit: Limit): List<Product>

    fun findAllByIdGreaterThanOrderByIdAsc(
        after: UUID,
        limit: Limit,
    ): List<Product>
}
