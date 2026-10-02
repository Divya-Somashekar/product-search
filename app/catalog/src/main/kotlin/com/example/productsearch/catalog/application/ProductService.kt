package com.example.productsearch.catalog.application

import com.example.productsearch.catalog.config.CatalogProperties
import com.example.productsearch.catalog.contract.ProductChange
import com.example.productsearch.catalog.contract.ProductDeleted
import com.example.productsearch.catalog.contract.ProductSnapshot
import com.example.productsearch.catalog.contract.ProductUpserted
import com.example.productsearch.catalog.domain.OutboxEntry
import com.example.productsearch.catalog.domain.OutboxRepository
import com.example.productsearch.catalog.domain.Product
import com.example.productsearch.catalog.domain.ProductCommand
import com.example.productsearch.catalog.domain.ProductNotFoundException
import com.example.productsearch.catalog.domain.ProductPage
import com.example.productsearch.catalog.domain.ProductRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Limit
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** The catalog's use cases. Each write commits to Postgres, then announces itself for indexing. */
@Service
class ProductService(
    private val repository: ProductRepository,
    private val outbox: OutboxRepository,
    private val events: ApplicationEventPublisher,
    private val catalog: CatalogProperties,
    private val clock: Clock,
) {
    @Transactional
    fun create(command: ProductCommand): ProductSnapshot = publishUpsert(Product.from(command, catalog.currency))

    @Transactional(readOnly = true)
    fun get(id: UUID): ProductSnapshot = find(id).snapshot()

    @Transactional(readOnly = true)
    fun list(
        page: Int,
        size: Int,
    ): ProductPage {
        val result = repository.findAll(PageRequest.of(page, size, Sort.by("createdAt", "id")))
        return ProductPage(result.content.map(Product::snapshot), page, size, result.totalElements)
    }

    /**
     * A page of the whole catalog for consumers that rebuild a projection of it — today the search
     * index. Keyset, so a rebuild running alongside writes cannot skip rows; see
     * [ProductRepository.findAllByOrderByIdAsc].
     */
    @Transactional(readOnly = true)
    fun export(
        after: UUID?,
        size: Int,
    ): List<ProductSnapshot> {
        val limit = Limit.of(size)
        val batch =
            if (after == null) {
                repository.findAllByOrderByIdAsc(limit)
            } else {
                repository.findAllByIdGreaterThanOrderByIdAsc(after, limit)
            }
        return batch.map(Product::snapshot)
    }

    @Transactional
    fun update(
        id: UUID,
        command: ProductCommand,
    ): ProductSnapshot = publishUpsert(find(id).also { it.update(command) })

    @Transactional
    fun delete(id: UUID) {
        repository.delete(find(id))
        record(id, deleted = true)
        events.publishEvent(ProductDeleted(id))
    }

    /**
     * A page of the change log, oldest first, for a consumer keeping a projection of the catalog
     * in step. Entries too recent to have settled are held back; see
     * [com.example.productsearch.catalog.config.CatalogProperties.Outbox.visibilityLag].
     *
     * Each entry is resolved to the product's *current* state, so a run of edits collapses into
     * one apply and no entry can describe a product as it no longer is. A `null` product means it
     * has been deleted.
     */
    @Transactional(readOnly = true)
    fun changes(
        after: Long,
        size: Int,
    ): List<ProductChange> {
        val entries =
            outbox.findBySeqGreaterThanAndCreatedAtLessThanEqualOrderBySeqAsc(
                after,
                clock.instant().minus(catalog.outbox.visibilityLag),
                Limit.of(size),
            )
        val current =
            repository
                .findAllById(entries.map { it.productId }.distinct())
                .associate { it.id to it.snapshot() }
        return entries.map { ProductChange(it.seq!!, it.productId, current[it.productId]) }
    }

    private fun find(id: UUID): Product = repository.findByIdOrNull(id) ?: throw ProductNotFoundException(id)

    private fun publishUpsert(product: Product): ProductSnapshot =
        repository.saveAndFlush(product).snapshot().also {
            record(it.id, deleted = false)
            events.publishEvent(ProductUpserted(it))
        }

    /**
     * Written inside the caller's transaction on purpose: the entry and the product change commit
     * together or not at all, so the change log can never advertise a write that was rolled back.
     */
    private fun record(
        productId: UUID,
        deleted: Boolean,
    ) {
        outbox.save(OutboxEntry(productId, deleted, clock.instant()))
    }
}
