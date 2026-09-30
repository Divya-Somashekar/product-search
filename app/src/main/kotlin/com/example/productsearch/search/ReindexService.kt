package com.example.productsearch.search

import com.example.productsearch.product.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

data class ReindexResult(
    val index: String,
    val documents: Long,
    val removedIndices: Set<String>,
    val duration: Duration,
)

class ReindexInProgressException : RuntimeException("A reindex is already running")

/**
 * Rebuilds the search index from Postgres without downtime: build a fresh index, fill it, then
 * swap the alias in one atomic step. Writes that land during the rebuild go to the old index and
 * are not copied; run it again, or add an outbox, if that window matters.
 */
@Service
class ReindexService(
    private val repository: ProductRepository,
    private val indexManager: IndexManager,
    private val indexer: ProductIndexer,
    private val properties: SearchProperties,
    private val transactions: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    /** Creates the first index if none exists. Safe to call from several replicas at once. */
    fun initialize() {
        if (indexManager.aliasExists()) return
        val index = indexManager.newIndexName()
        if (indexManager.createIndex(index, attachAlias = true)) {
            val count = load(index)
            log.info("Initialised search index {} with {} products", index, count)
        }
    }

    fun reindex(): ReindexResult {
        if (!running.compareAndSet(false, true)) throw ReindexInProgressException()
        try {
            val started = Instant.now()
            val index = indexManager.newIndexName()
            indexManager.createIndex(index, attachAlias = false)
            val count =
                try {
                    load(index)
                } catch (e: Exception) {
                    indexManager.deleteIndex(index)
                    throw e
                }
            val previous = indexManager.swapAlias(index)
            previous.forEach(indexManager::deleteIndex)
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
                            .map(ProductDocument::of)
                    }.orEmpty()
            indexer.bulkIndex(index, batch)
            total += batch.size
            page++
        } while (batch.size == properties.bulkSize)
        indexManager.refresh(index)
        return total
    }
}
