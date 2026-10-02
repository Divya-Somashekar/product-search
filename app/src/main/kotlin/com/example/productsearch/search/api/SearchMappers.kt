package com.example.productsearch.search.api

import com.example.productsearch.search.domain.FacetBucket
import com.example.productsearch.search.domain.ProductHit
import com.example.productsearch.search.domain.SearchResult

/** The HTTP edge of search: validated criteria in, results out. */
fun SearchResult.toResponse() =
    SearchResponse(
        total = total,
        page = page,
        size = size,
        items = items.map(ProductHit::toResponse),
        facets = facets.mapValues { (_, buckets) -> buckets.map(FacetBucket::toResponse) },
    )

fun ProductHit.toResponse() =
    ProductHitResponse(
        id = id,
        name = name,
        description = description,
        category = category,
        brand = brand,
        price = price,
        currency = currency,
        inStock = inStock,
        score = score,
        highlights = highlights,
    )

fun FacetBucket.toResponse() = FacetBucketResponse(value, count)
