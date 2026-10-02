package com.example.productsearch.catalog.config

import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * The catalog prices everything in one currency, which is what makes price filters and
 * price sorting meaningful without conversion.
 */
@Validated
@ConfigurationProperties("catalog")
data class CatalogProperties(
    @field:Pattern(regexp = "[A-Z]{3}")
    val currency: String = "EUR",
    val outbox: Outbox = Outbox(),
) {
    data class Outbox(
        /**
         * How long an outbox entry is held back from consumers after it was written.
         *
         * `seq` is handed out when the entry is inserted, but the row only becomes visible when
         * its transaction commits, and two transactions can do those in opposite orders. The
         * hold-back has to outlast the gap between an entry's insert and its commit, or a reader
         * can move its cursor past a `seq` that is still on its way. Raise it if writes are slow;
         * `POST /api/v1/admin/reindex` repairs anything that slips through.
         */
        val visibilityLag: Duration = Duration.ofSeconds(1),
    )
}
