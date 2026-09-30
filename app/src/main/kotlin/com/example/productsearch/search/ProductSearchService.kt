package com.example.productsearch.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service

@Service
class ProductSearchService(
    private val client: ElasticsearchClient,
    private val queryBuilder: SearchQueryBuilder,
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
        val response = client.search(queryBuilder.build(criteria), Map::class.java)
        sample.stop(
            Timer
                .builder("product.search")
                .description("Search latency, including the Elasticsearch round trip")
                .tag("sort", criteria.sort.param)
                .tag("text", (!criteria.query.isNullOrBlank()).toString())
                .register(meterRegistry),
        )

        val total = response.hits().total()?.value() ?: 0
        resultCount.record(total.toDouble())
        return SearchResult(
            total = total,
            page = criteria.page,
            size = criteria.size,
            items =
                response.hits().hits().mapNotNull { hit ->
                    hit.source()?.let { source ->
                        val doc = ProductDocument.fromSource(source)
                        ProductHit(
                            id = doc.id,
                            name = doc.name,
                            description = doc.description,
                            category = doc.category,
                            brand = doc.brand,
                            price = doc.price,
                            currency = doc.currency,
                            inStock = doc.inStock,
                            score = hit.score(),
                            highlights = hit.highlight(),
                        )
                    }
                },
            facets =
                mapOf(
                    SearchQueryBuilder.FACET_CATEGORY to buckets(response.aggregations()[SearchQueryBuilder.FACET_CATEGORY]),
                    SearchQueryBuilder.FACET_BRAND to buckets(response.aggregations()[SearchQueryBuilder.FACET_BRAND]),
                ),
        )
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

    private fun buckets(aggregate: Aggregate?): List<FacetBucket> =
        aggregate
            ?.sterms()
            ?.buckets()
            ?.array()
            ?.map { FacetBucket(it.key().stringValue(), it.docCount()) }
            .orEmpty()
}
