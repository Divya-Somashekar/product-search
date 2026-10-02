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
import com.example.productsearch.catalog.domain.ProductImageRejectedException
import com.example.productsearch.catalog.domain.ProductImages
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
import java.net.URI
import java.time.Clock
import java.util.UUID

/** The catalog's use cases. Each write commits to Postgres, then announces itself for indexing. */
@Service
class ProductService(
    private val repository: ProductRepository,
    private val outbox: OutboxRepository,
    private val events: ApplicationEventPublisher,
    private val images: ProductImages,
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

    /**
     * A link a shopper can use to fetch this product's image, or null if it has none.
     *
     * Not part of the snapshot: the link expires in minutes, so it is produced at the moment of
     * answering a request rather than stored or cached anywhere.
     */
    fun viewUrl(snapshot: ProductSnapshot): URI? = snapshot.imageKey?.let(images::viewUrl)

    /**
     * Where a seller should PUT an image for [id], and under what key it will be stored.
     *
     * Read-only: nothing is recorded against the product yet, because the bytes are not there
     * yet. [attachImage] is what commits the result.
     */
    @Transactional(readOnly = true)
    fun requestImageUpload(
        id: UUID,
        contentType: String,
    ): ProductImages.UploadTarget {
        if (contentType !in catalog.images.allowedContentTypes) {
            throw ProductImageRejectedException(
                "content type $contentType is not accepted; allowed: " +
                    catalog.images.allowedContentTypes
                        .sorted()
                        .joinToString(", "),
            )
        }
        // Proves the product exists before handing out a link, so a seller cannot be told an
        // upload succeeded for something that was deleted.
        find(id)
        return images.uploadTarget(id, contentType)
    }

    /**
     * Records [key] as [id]'s image, once the bytes are confirmed to be in storage.
     *
     * Everything about the upload is re-checked here against what is actually stored, because
     * none of it can be trusted from the client: the key is supplied by the caller, and a
     * presigned PUT enforces no size limit of its own, so the only point at which the real size
     * is knowable is after the object exists.
     */
    @Transactional
    fun attachImage(
        id: UUID,
        key: String,
    ): ProductSnapshot {
        val product = find(id)

        // The key must be one this product's own upload link would have produced. Without this,
        // a caller could point a product at any object in the bucket, including another
        // seller's image.
        if (!key.startsWith("products/$id/")) {
            throw ProductImageRejectedException("key $key does not belong to product $id")
        }

        val stored =
            images.describe(key)
                ?: throw ProductImageRejectedException("nothing is stored at $key; upload the image first")

        if (stored.contentType !in catalog.images.allowedContentTypes) {
            throw ProductImageRejectedException("stored object has content type ${stored.contentType}")
        }
        if (stored.sizeBytes > catalog.images.maxUploadBytes) {
            throw ProductImageRejectedException(
                "image is ${stored.sizeBytes} bytes, limit is ${catalog.images.maxUploadBytes}",
            )
        }

        product.attachImage(key)
        return publishUpsert(product)
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
