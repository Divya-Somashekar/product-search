package com.example.productsearch.product

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
}
