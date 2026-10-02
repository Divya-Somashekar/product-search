package com.example.productsearch.search.infrastructure

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.SortOptions
import co.elastic.clients.elasticsearch._types.SortOrder
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.search.HighlightField
import co.elastic.clients.elasticsearch.core.search.HighlighterEncoder
import co.elastic.clients.util.NamedValue
import com.example.productsearch.search.config.SearchProperties
import com.example.productsearch.search.domain.SearchCriteria
import com.example.productsearch.search.domain.SortOption
import org.springframework.stereotype.Component

/** Translates [SearchCriteria] into an Elasticsearch request. Pure: no I/O, unit-testable. */
@Component
class SearchQueryBuilder(
    private val properties: SearchProperties,
) {
    fun build(criteria: SearchCriteria): SearchRequest =
        SearchRequest.of { s ->
            s
                .index(properties.alias)
                .query { q -> q.bool { b -> b.apply { text(criteria) }.apply { filters(criteria) } } }
                .from(criteria.from)
                .size(criteria.size)
                .trackTotalHits { it.enabled(true) }
                .trackScores(true)
                .sort(sort(criteria.sort))
                .highlight { h ->
                    h
                        .encoder(HighlighterEncoder.Html)
                        .preTags("<em>")
                        .postTags("</em>")
                        .fields(
                            NamedValue.of("name", HighlightField.of { it.numberOfFragments(0) }),
                            NamedValue.of("description", HighlightField.of { it.fragmentSize(150).numberOfFragments(2) }),
                        )
                }.aggregations(FACET_CATEGORY) { a -> a.terms { it.field("category").size(properties.facetSize) } }
                .aggregations(FACET_BRAND) { a -> a.terms { it.field("brand").size(properties.facetSize) } }
        }

    private fun BoolQuery.Builder.text(criteria: SearchCriteria) {
        val text = criteria.query?.trim()
        if (text.isNullOrEmpty()) {
            must { it.matchAll { m -> m } }
            return
        }
        must { m ->
            m.multiMatch { mm ->
                mm
                    .query(text)
                    .fields(SEARCH_FIELDS)
                    .type(TextQueryType.BestFields)
                    .tieBreaker(0.3)
                    // AUTO: 0 edits up to 2 chars, 1 up to 5, 2 beyond. "wireles" -> "wireless".
                    .fuzziness("AUTO")
                    // The first letter must match: keeps fuzzy expansion cheap and results plausible.
                    .prefixLength(1)
                    // Up to two words all must match ("wireless headphones" is not "wireless keyboard");
                    // longer queries tolerate one word in four missing.
                    .minimumShouldMatch("2<75%")
            }
        }
        // Rank items whose name contains the words close together above scattered matches.
        should { sh ->
            sh.matchPhrase {
                it
                    .field("name")
                    .query(text)
                    .slop(2)
                    .boost(2f)
            }
        }
    }

    // Filters narrow the result set without affecting scores, and Elasticsearch caches them.
    private fun BoolQuery.Builder.filters(criteria: SearchCriteria) {
        if (criteria.categories.isNotEmpty()) {
            filter { f -> f.terms { t -> t.field("category").terms { v -> v.value(criteria.categories.map(FieldValue::of)) } } }
        }
        if (criteria.brands.isNotEmpty()) {
            filter { f -> f.terms { t -> t.field("brand").terms { v -> v.value(criteria.brands.map(FieldValue::of)) } } }
        }
        if (criteria.minPrice != null || criteria.maxPrice != null) {
            filter { f ->
                f.range { r ->
                    r.number { n ->
                        n.field("price")
                        criteria.minPrice?.let { n.gte(it.toDouble()) }
                        criteria.maxPrice?.let { n.lte(it.toDouble()) }
                        n
                    }
                }
            }
        }
        criteria.inStock?.let { inStock -> filter { f -> f.term { it.field("inStock").value(inStock) } } }
    }

    private fun sort(option: SortOption): List<SortOptions> {
        val byScore = SortOptions.of { s -> s.score { it.order(SortOrder.Desc) } }
        val byId = SortOptions.of { s -> s.field { it.field("id").order(SortOrder.Asc) } }
        return when (option) {
            SortOption.RELEVANCE -> listOf(byScore, byId)
            SortOption.PRICE_ASC -> listOf(byPrice(SortOrder.Asc), byScore, byId)
            SortOption.PRICE_DESC -> listOf(byPrice(SortOrder.Desc), byScore, byId)
        }
    }

    private fun byPrice(order: SortOrder) = SortOptions.of { s -> s.field { it.field("price").order(order) } }

    companion object {
        const val FACET_CATEGORY = "category"
        const val FACET_BRAND = "brand"
        val SEARCH_FIELDS = listOf("name^3", "brand.text^2", "category.text^2", "description")
    }
}
