package com.example.productsearch.search.application

import com.example.productsearch.catalog.domain.Product
import com.example.productsearch.catalog.domain.ProductRepository
import com.example.productsearch.search.config.SearchProperties
import com.example.productsearch.search.domain.IndexLifecycle
import com.example.productsearch.search.domain.ReindexInProgressException
import com.example.productsearch.search.domain.ReindexResult
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Rebuilds the search index from Postgres without downtime: build a fresh index, fill it, then
 * swap the alias in one atomic step. Writes that land during the rebuild go to the old index and
 * are not copied; run it again, or add an outbox, if that window matters.
 *
 * Reads the catalog's repository directly — the index is a projection of it, and a rebuild is the
 * one place in search that needs the source of truth rather than the index.
 */
@Service
class ReindexService(
    private val repository: ProductRepository,
    private val lifecycle: IndexLifecycle,
    private val properties: SearchProperties,
    private val transactions: TransactionTemplate,
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
        var page = 0
        var total = 0L
        do {
            val batch =
                transactions
                    .execute {
                        repository
                            .findAll(PageRequest.of(page, properties.bulkSize, Sort.by("id")))
                            .content
                            .map(Product::snapshot)
                    }.orEmpty()
            lifecycle.bulkIndex(index, batch)
            total += batch.size
            page++
        } while (batch.size == properties.bulkSize)
        lifecycle.refresh(index)
        return total
    }
}
