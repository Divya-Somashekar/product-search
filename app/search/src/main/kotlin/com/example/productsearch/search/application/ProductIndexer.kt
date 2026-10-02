package com.example.productsearch.search.application

import com.example.productsearch.catalog.contract.ProductChangedEvent
import com.example.productsearch.catalog.contract.ProductDeleted
import com.example.productsearch.catalog.contract.ProductUpserted
import com.example.productsearch.search.config.SearchProperties
import com.example.productsearch.search.domain.ProductIndex
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
 * repairs the index. A transactional outbox would make this guaranteed; see docs/rfd.
 */
@Component
class ProductIndexer(
    private val index: ProductIndex,
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

    private fun apply(event: ProductChangedEvent) {
        when (event) {
            is ProductUpserted -> index.index(event.product)
            is ProductDeleted -> index.delete(event.productId)
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
