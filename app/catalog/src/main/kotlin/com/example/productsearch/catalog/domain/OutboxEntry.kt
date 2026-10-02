package com.example.productsearch.catalog.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One entry in the catalog's change log. Written inside the same transaction as the product
 * change, which is what makes it safe: a rollback takes the entry with it.
 *
 * There is no payload. A consumer resolves `productId` to its current snapshot when it reads the
 * entry, so repeated edits collapse into the current state and an entry can never describe a
 * product as it no longer is.
 */
@Entity
@Table(name = "product_outbox")
class OutboxEntry(
    val productId: UUID,
    val deleted: Boolean,
    @Column(updatable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var seq: Long? = null
}
