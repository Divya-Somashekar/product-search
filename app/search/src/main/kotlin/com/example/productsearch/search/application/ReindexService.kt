package com.example.productsearch.search.application

import com.example.productsearch.search.config.SearchProperties
import com.example.productsearch.search.domain.IndexLifecycle
import com.example.productsearch.search.domain.ProductSource
import com.example.productsearch.search.domain.ReindexInProgressException
import com.example.productsearch.search.domain.ReindexResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rebuilds the search index from the catalog without downtime: build a fresh index, fill it, then
 * swap the alias in one atomic step. Writes that land during the rebuild go to the old index and
 * are not copied; run it again, or add an outbox, if that window matters.
 *
 * Reads through [ProductSource], so this class does not care whether the catalog is a few objects
 * away or a few milliseconds away over HTTP.
 */
@Service
class ReindexService(
    private val products: ProductSource,
    private val lifecycle: IndexLifecycle,
    private val properties: SearchProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    /** Creates the first index if none exists. Safe to call from several replicas at once. */
    fun initialize() {
        if (lifecycle.aliasExists()) return
        val index = lifecycle.newIndexName()
        if (lifecycle.createIndex(index, attachAlias = true)) {
            val count = load(index)
            log.info("Initialised search index {} with {} products", index, count)
        }
    }

    fun reindex(): ReindexResult {
        if (!running.compareAndSet(false, true)) throw ReindexInProgressException()
        try {
            val started = Instant.now()
            val index = lifecycle.newIndexName()
            lifecycle.createIndex(index, attachAlias = false)
            val count =
                try {
                    load(index)
                } catch (e: Exception) {
                    lifecycle.deleteIndex(index)
                    throw e
                }
            val previous = lifecycle.swapAlias(index)
            previous.forEach(lifecycle::deleteIndex)
            return ReindexResult(index, count, previous, Duration.between(started, Instant.now())).also {
                log.info("Reindexed {} products into {} in {}", it.documents, it.index, it.duration)
            }
        } finally {
            running.set(false)
        }
    }

    private fun load(index: String): Long {
        var after: UUID? = null
        var total = 0L
        while (true) {
            val batch = products.page(after, properties.bulkSize)
            if (batch.isEmpty()) break
            lifecycle.bulkIndex(index, batch)
            total += batch.size
            after = batch.last().id
            // A short page is the last one; a full page may still be followed by an empty one.
            if (batch.size < properties.bulkSize) break
        }
        lifecycle.refresh(index)
        return total
    }
}
