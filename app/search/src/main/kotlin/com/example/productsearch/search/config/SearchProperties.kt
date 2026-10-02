package com.example.productsearch.search.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties("search")
data class SearchProperties(
    /** Alias the application reads and writes through; the concrete index behind it is swapped on reindex. */
    @field:NotBlank
    val alias: String = "products",
    /** Settings and mappings used whenever a new index is created. */
    val indexDefinition: String = "classpath:elasticsearch/products-index.json",
    /** `wait_for` makes writes visible to search before the request returns (tests, demos); `false` for throughput. */
    val refresh: RefreshPolicy = RefreshPolicy.FALSE,
    @field:Min(1)
    val bulkSize: Int = 500,
    /** Mirrors the index's `max_result_window`; deeper paging needs `search_after`. */
    val maxResultWindow: Int = 10_000,
    @field:Min(1)
    @field:Max(100)
    val maxPageSize: Int = 100,
    @field:Min(1)
    val facetSize: Int = 20,
    val indexing: Indexing = Indexing(),
) {
    enum class RefreshPolicy { FALSE, WAIT_FOR }

    data class Indexing(
        @field:Min(1)
        val maxAttempts: Int = 3,
        val backoff: Duration = Duration.ofMillis(200),
    )
}
