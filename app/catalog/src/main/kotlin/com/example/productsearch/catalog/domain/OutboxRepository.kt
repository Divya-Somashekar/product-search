package com.example.productsearch.catalog.domain

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface OutboxRepository : JpaRepository<OutboxEntry, Long> {
    /**
     * Entries after a consumer's cursor, oldest first, holding back anything newer than
     * [settledBefore].
     *
     * The hold-back is not politeness, it is correctness. `seq` comes from a sequence and is
     * assigned when a transaction inserts, but transactions commit in their own order: a slow
     * write can take seq 5 and commit *after* a quick write took seq 6 and committed. A reader
     * that saw 6 and moved its cursor past it would never see 5. Ignoring entries whose
     * transaction started more recently than the longest write takes gives those gaps time to
     * fill in. See `catalog.outbox.visibility-lag`.
     */
    fun findBySeqGreaterThanAndCreatedAtLessThanEqualOrderBySeqAsc(
        seq: Long,
        settledBefore: Instant,
        limit: Limit,
    ): List<OutboxEntry>
}
