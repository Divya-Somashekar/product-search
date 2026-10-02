package com.example.productsearch.search.domain

import com.example.productsearch.catalog.contract.ProductChange

/**
 * The catalog's change log, as search reads it. Pass the highest `seq` already applied; an empty
 * page means there is nothing new that has settled yet.
 */
fun interface ChangeFeed {
    fun changes(
        after: Long,
        size: Int,
    ): List<ProductChange>
}
