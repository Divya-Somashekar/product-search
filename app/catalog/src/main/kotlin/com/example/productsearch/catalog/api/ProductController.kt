package com.example.productsearch.catalog.api

import com.example.productsearch.catalog.application.ProductService
import com.example.productsearch.catalog.contract.ProductSnapshot
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
        val created = service.create(request.toCommand()).withImage()
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
    ): ProductResponse = service.get(id).withImage()

    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<ProductResponse> = service.list(page, size).toResponse(service::viewUrl)

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ProductRequest,
    ): ProductResponse = service.update(id, request.toCommand()).withImage()

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
    ): ResponseEntity<Unit> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Step one of an image upload: ask where to put it.
     *
     * POST rather than GET even though nothing is persisted, because it mints a credential — a
     * signed, time-limited grant to write to the bucket — and those should not be cacheable or
     * sitting in a browser history.
     */
    @PostMapping("/{id}/image-upload-url")
    fun requestImageUpload(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ImageUploadRequest,
    ): ImageUploadResponse {
        val target = service.requestImageUpload(id, request.contentType)
        return ImageUploadResponse(
            uploadUrl = target.url.toString(),
            key = target.key,
            contentType = request.contentType,
        )
    }

    /**
     * Step two: the bytes are in S3, record them against the product.
     *
     * Separate from step one because an upload link can be issued and never used. Committing the
     * key when the link is handed out would leave products pointing at objects that do not exist.
     */
    @PostMapping("/{id}/image")
    fun confirmImage(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ImageConfirmRequest,
    ): ProductResponse = service.attachImage(id, request.key).withImage()

    /** A snapshot plus a freshly signed link to its image, if it has one. */
    private fun ProductSnapshot.withImage() = toResponse(service.viewUrl(this))
}
