package com.example.productsearch.search.api

import com.example.productsearch.search.application.ReindexService
import com.example.productsearch.search.domain.ReindexResult
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Operational endpoint: rebuilds the index from Postgres. Restrict it at the network or auth layer. */
@RestController
@RequestMapping("/api/v1/admin")
class AdminReindexController(
    private val reindexService: ReindexService,
) {
    @PostMapping("/reindex")
    fun reindex(): ReindexResult = reindexService.reindex()
}
