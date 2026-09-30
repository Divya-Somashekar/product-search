package com.example.productsearch.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import com.example.productsearch.product.ProductChangedEvent
import com.example.productsearch.product.ProductDeleted
import com.example.productsearch.product.ProductUpserted
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Keeps the index in step with Postgres after each committed write.
 *
 * The database is the source of truth: if Elasticsearch stays unavailable past the retries the
 * write still succeeds, the failure is logged and counted, and `POST /api/v1/admin/reindex`
 * repairs the index. A transactional outbox would make this guaranteed; see docs/adr.
 */
@Component
class ProductIndexer(
    private val client: ElasticsearchClient,
    private val indexManager: IndexManager,
    private val properties: SearchProperties,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val failures = meterRegistry.counter("product.indexing.failures")

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: ProductChangedEvent) {
        try {
            withRetry { apply(event) }
        } catch (e: Exception) {
            failures.increment()
            log.error("Failed to index product {} after {} attempts", event.productId, properties.indexing.maxAttempts, e)
        }
    }

    /** Bulk-writes [documents] into a concrete index; used by reindexing. */
    fun bulkIndex(
        index: String,
        documents: List<ProductDocument>,
    ) {
        if (documents.isEmpty()) return
        val response =
            client.bulk { request ->
                request.index(index).operations(
                    documents.map { doc -> BulkOperation.of { op -> op.index { it.id(doc.id).document(doc.toSource()) } } },
                )
            }
        if (response.errors()) {
            val firstError = response.items().firstNotNullOfOrNull { it.error() }
            throw IllegalStateException("Bulk indexing into $index failed: ${firstError?.reason()}")
        }
    }

    private fun apply(event: ProductChangedEvent) {
        when (event) {
            is ProductUpserted -> {
                val doc = ProductDocument.of(event.product)
                client.index {
                    it
                        .index(indexManager.alias)
                        .id(doc.id)
                        .document(doc.toSource())
                        .refresh(indexManager.refreshPolicy())
                }
            }
            is ProductDeleted ->
                client.delete { it.index(indexManager.alias).id(event.productId.toString()).refresh(indexManager.refreshPolicy()) }
        }
    }

    private fun withRetry(block: () -> Unit) {
        val attempts = properties.indexing.maxAttempts
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (attempt == attempts - 1) throw e
                log.warn("Indexing attempt {} of {} failed: {}", attempt + 1, attempts, e.message)
                Thread.sleep(properties.indexing.backoff.multipliedBy(attempt + 1L))
            }
        }
    }
}
