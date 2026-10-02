package com.example.productsearch.search.infrastructure

import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.transport.TransportException
import com.example.productsearch.search.domain.SearchUnavailableException

/**
 * Runs [block] and rewrites client failures as [SearchUnavailableException], so Elasticsearch
 * types stay inside this package and the layers above handle one search error.
 */
internal fun <T> elasticsearch(block: () -> T): T =
    try {
        block()
    } catch (e: ElasticsearchException) {
        throw SearchUnavailableException(e)
    } catch (e: TransportException) {
        throw SearchUnavailableException(e)
    }
