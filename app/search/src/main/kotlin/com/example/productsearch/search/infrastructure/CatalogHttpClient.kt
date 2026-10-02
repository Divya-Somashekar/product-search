package com.example.productsearch.search.infrastructure

import com.example.productsearch.catalog.contract.ProductChange
import com.example.productsearch.catalog.contract.ProductSnapshot
import com.example.productsearch.search.config.SearchProperties
import com.example.productsearch.search.domain.ChangeFeed
import com.example.productsearch.search.domain.ProductSource
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

/**
 * Reads the catalog over HTTP: the adapter that makes search deployable on its own.
 *
 * It exists only when `search.catalog.base-url` is set, which is the case in the search service
 * and not in the monolith — there the composition root wires the same two ports straight to the
 * catalog's use cases, with no network in between.
 */
@Component
@ConditionalOnProperty("search.catalog.base-url")
class CatalogHttpClient(
    properties: SearchProperties,
) : ProductSource,
    ChangeFeed {
    // An explicit read timeout matters here: without one, a wedged catalog would stall the sync
    // poller indefinitely and search would go quietly stale instead of logging a failure.
    private val client =
        RestClient
            .builder()
            .baseUrl(properties.catalog.baseUrl)
            .requestFactory(JdkClientHttpRequestFactory().apply { setReadTimeout(properties.catalog.readTimeout) })
            .build()

    override fun page(
        after: UUID?,
        size: Int,
    ): List<ProductSnapshot> =
        client
            .get()
            .uri { uri ->
                uri.path("/internal/products").queryParam("size", size)
                after?.let { uri.queryParam("after", it) }
                uri.build()
            }.retrieve()
            .body(object : ParameterizedTypeReference<List<ProductSnapshot>>() {})
            .orEmpty()

    override fun changes(
        after: Long,
        size: Int,
    ): List<ProductChange> =
        client
            .get()
            .uri {
                it
                    .path("/internal/changes")
                    .queryParam("after", after)
                    .queryParam("size", size)
                    .build()
            }.retrieve()
            .body(object : ParameterizedTypeReference<List<ProductChange>>() {})
            .orEmpty()
}
