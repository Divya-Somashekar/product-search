package com.example.productsearch.search.application

import com.example.productsearch.search.config.SearchProperties
import com.example.productsearch.search.domain.ChangeFeed
import com.example.productsearch.search.domain.ProductIndex
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

/**
 * Keeps the index in step by tailing the catalog's change log. This is what replaces the
 * in-process after-commit listener once search is its own service.
 *
 * The cursor is held in memory and starts at zero, so a restart replays the log from the
 * beginning. That is cheap here and safe because an entry resolves to the product's *current*
 * state, which makes applying one twice a no-op. A long-lived deployment would persist the cursor
 * and prune the log instead.
 */
@Service
@ConditionalOnProperty("search.sync.enabled", havingValue = "true")
class ProductSyncService(
    private val feed: ChangeFeed,
    private val index: ProductIndex,
    private val properties: SearchProperties,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cursor = AtomicLong(0)
    private val failures = meterRegistry.counter("search.sync.failures")

    init {
        // Staleness is the failure mode of a poller, so make the cursor observable from the start.
        meterRegistry.gauge("search.sync.cursor", cursor)
    }

    @Scheduled(fixedDelayString = "\${search.sync.interval:1s}")
    fun poll() {
        try {
            while (drain() == properties.sync.batchSize) {
                // A full page means there is probably more; keep going until we catch up.
            }
        } catch (e: Exception) {
            failures.increment()
            log.warn("Change log sync failed at seq {}: {}", cursor.get(), e.message)
        }
    }

    /** Applies one page and returns how many entries it held. */
    fun drain(): Int {
        val batch = feed.changes(cursor.get(), properties.sync.batchSize)
        batch.forEach { change ->
            change.product?.let(index::index) ?: index.delete(change.productId)
            cursor.set(change.seq)
        }
        if (batch.isNotEmpty()) log.debug("Applied {} changes, cursor now {}", batch.size, cursor.get())
        return batch.size
    }
}
