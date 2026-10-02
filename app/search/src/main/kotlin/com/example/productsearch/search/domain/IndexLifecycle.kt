package com.example.productsearch.search.domain

import com.example.productsearch.catalog.contract.ProductSnapshot

/**
 * The index lifecycle a rebuild needs: concrete indices created, filled and then swapped behind
 * the alias that [ProductIndex] reads. Separate from [ProductIndex] because only reindexing
 * addresses a concrete index; everything else goes through the alias.
 */
interface IndexLifecycle {
    fun aliasExists(): Boolean

    fun newIndexName(): String

    /** Creates [name]; false if it already exists, which is how concurrent replicas agree on who created it. */
    fun createIndex(
        name: String,
        attachAlias: Boolean,
    ): Boolean

    fun bulkIndex(
        index: String,
        products: List<ProductSnapshot>,
    )

    /** Atomically points the alias at [index] only, returning the indices it was taken from. */
    fun swapAlias(index: String): Set<String>

    fun deleteIndex(name: String)

    fun refresh(index: String)
}
