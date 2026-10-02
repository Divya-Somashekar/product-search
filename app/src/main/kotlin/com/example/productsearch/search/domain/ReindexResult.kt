package com.example.productsearch.search.domain

import java.time.Duration

data class ReindexResult(
    val index: String,
    val documents: Long,
    val removedIndices: Set<String>,
    val duration: Duration,
)
