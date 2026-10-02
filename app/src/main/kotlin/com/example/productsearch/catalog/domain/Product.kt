package com.example.productsearch.catalog.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * The catalog aggregate and the source of truth for a product. Mapped straight onto the Flyway
 * schema: `spring.jpa.hibernate.ddl-auto=validate` fails startup if the two drift apart.
 */
@Entity
@Table(name = "product")
class Product(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(length = 200)
    var name: String,
    @Column(columnDefinition = "text")
    var description: String,
    @Column(length = 100)
    var category: String,
    @Column(length = 100)
    var brand: String,
    @Column(precision = 12, scale = 2)
    var price: BigDecimal,
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    var currency: String,
    var stockQuantity: Int,
) {
    // Null until first persisted; Spring Data uses it to tell new entities from existing ones.
    @Version
    var version: Long? = null

    @Column(updatable = false)
    var createdAt: Instant = Instant.now()

    var updatedAt: Instant = Instant.now()

    val inStock: Boolean
        get() = stockQuantity > 0

    @PreUpdate
    fun onUpdate() {
        updatedAt = Instant.now()
    }

    /** Applies the writable fields of [command], trimming what callers may have padded. */
    fun update(command: ProductCommand) {
        name = command.name.trim()
        description = command.description.trim()
        category = command.category.trim()
        brand = command.brand.trim()
        price = command.price
        stockQuantity = command.stockQuantity
    }

    /**
     * An immutable view of the product, detached from the persistence context. Everything that
     * leaves the catalog — API responses, change events, the search index — travels as a
     * snapshot, so no other layer holds a live entity.
     */
    fun snapshot() =
        ProductSnapshot(
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

    companion object {
        fun from(
            command: ProductCommand,
            currency: String,
        ) = Product(
            name = command.name.trim(),
            description = command.description.trim(),
            category = command.category.trim(),
            brand = command.brand.trim(),
            price = command.price,
            currency = currency,
            stockQuantity = command.stockQuantity,
        )
    }
}
