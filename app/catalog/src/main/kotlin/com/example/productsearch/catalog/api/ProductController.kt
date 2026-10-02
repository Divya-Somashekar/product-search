package com.example.productsearch.catalog.api

import com.example.productsearch.catalog.application.ProductService
import com.example.productsearch.shared.web.PageResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val service: ProductService,
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: ProductRequest,
    ): ResponseEntity<ProductResponse> {
        val created = service.create(request.toCommand()).toResponse()
        val location =
            ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id)
                .toUri()
        return ResponseEntity.created(location).body(created)
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ): ProductResponse = service.get(id).toResponse()

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<ProductResponse> = service.list(page, size).toResponse()

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ProductRequest,
    ): ProductResponse = service.update(id, request.toCommand()).toResponse()

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Unit> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }
}
