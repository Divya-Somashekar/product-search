package com.example.productsearch.catalog.api

import com.example.productsearch.catalog.application.ProductService
import com.example.productsearch.catalog.contract.ProductChange
import com.example.productsearch.catalog.contract.ProductSnapshot
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The catalog's feed for other services, as opposed to `/api/v1` which serves clients. Both
 * endpoints emit contract types — `ProductSnapshot` and `ProductChange` — rather than HTTP DTOs of
 * their own, because the consumer is another service reading the same contract.
 *
 * `/products` is a full read for building a projection from scratch: keyset pages, so pass the
 * last id of the previous page as `after`, and a page shorter than `size` is the last one.
 * `/changes` keeps that projection in step afterwards.
 *
 * Unauthenticated, like `/api/v1/admin/reindex`; see the PoC shortcuts in CLAUDE.md.
 */
@RestController
@RequestMapping("/internal")
class InternalProductController(
    private val service: ProductService,
) {
    @GetMapping("/products")
    fun export(
        @RequestParam(required = false) after: UUID?,
        @RequestParam(defaultValue = "500") @Min(1) @Max(1000) size: Int,
    ): List<ProductSnapshot> = service.export(after, size)

    /**
     * The change log, oldest first. A consumer passes the highest `seq` it has applied and gets
     * what has happened since. An empty page means it is up to date; entries too recent to have
     * settled are held back, so an empty page is not proof that nothing has been written.
     */
    @GetMapping("/changes")
    fun changes(
        @RequestParam(defaultValue = "0") @Min(0) after: Long,
        @RequestParam(defaultValue = "500") @Min(1) @Max(1000) size: Int,
    ): List<ProductChange> = service.changes(after, size)
}
