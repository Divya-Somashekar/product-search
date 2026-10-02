package com.example.productsearch.search.application

import com.example.productsearch.search.config.SearchProperties
import com.example.productsearch.search.domain.InvalidSearchRequestException
import com.example.productsearch.search.domain.ProductIndex
import com.example.productsearch.search.domain.SearchCriteria
import com.example.productsearch.search.domain.SearchResult
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service

/** Validates a search, runs it through the index port and measures what it cost. */
@Service
class ProductSearchService(
    private val index: ProductIndex,
    private val properties: SearchProperties,
    private val meterRegistry: MeterRegistry,
) {
    private val resultCount =
        DistributionSummary
            .builder("product.search.results")
            .description("Total hits per search")
            .register(meterRegistry)

    fun search(criteria: SearchCriteria): SearchResult {
        validate(criteria)
        val sample = Timer.start(meterRegistry)
        val result = index.search(criteria)
        sample.stop(
            Timer
                .builder("product.search")
                .description("Search latency, including the Elasticsearch round trip")
                .tag("sort", criteria.sort.param)
                .tag("text", (!criteria.query.isNullOrBlank()).toString())
                .register(meterRegistry),
        )
        resultCount.record(result.total.toDouble())
        return result
    }

    private fun validate(criteria: SearchCriteria) {
        val (min, max) = criteria.minPrice to criteria.maxPrice
        if (min != null && max != null && min > max) {
            throw InvalidSearchRequestException("minPrice must not be greater than maxPrice")
        }
        if (criteria.from + criteria.size > properties.maxResultWindow) {
            throw InvalidSearchRequestException("page * size + size must not exceed ${properties.maxResultWindow}")
        }
    }
}
