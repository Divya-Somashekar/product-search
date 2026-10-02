package com.example.productsearch.search.infrastructure

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate
import com.example.productsearch.catalog.domain.ProductSnapshot
import com.example.productsearch.search.domain.FacetBucket
import com.example.productsearch.search.domain.ProductHit
import com.example.productsearch.search.domain.ProductIndex
import com.example.productsearch.search.domain.SearchCriteria
import com.example.productsearch.search.domain.SearchResult
import org.springframework.stereotype.Component
import java.util.UUID

/** [ProductIndex] on Elasticsearch: every call goes through the alias, never a concrete index. */
@Component
class ElasticsearchProductIndex(
    private val client: ElasticsearchClient,
    private val queryBuilder: SearchQueryBuilder,
    private val indexManager: IndexManager,
) : ProductIndex {
    override fun search(criteria: SearchCriteria): SearchResult =
        elasticsearch {
            val response = client.search(queryBuilder.build(criteria), Map::class.java)
            SearchResult(
                total = response.hits().total()?.value() ?: 0,
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

    override fun index(product: ProductSnapshot) {
        val doc = ProductDocument.of(product)
        elasticsearch {
            client.index {
                it
                    .index(indexManager.alias)
                    .id(doc.id)
                    .document(doc.toSource())
                    .refresh(indexManager.refreshPolicy())
            }
        }
    }

    override fun delete(productId: UUID) {
        elasticsearch {
            client.delete { it.index(indexManager.alias).id(productId.toString()).refresh(indexManager.refreshPolicy()) }
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
