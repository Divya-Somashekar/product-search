package com.example.productsearch.catalog.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** What a seller sends to ask for somewhere to put an image. */
data class ImageUploadRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val contentType: String,
)

/**
 * Where to PUT the bytes, and the key to send back afterwards.
 *
 * The seller's browser PUTs directly to [uploadUrl] — the bytes never pass through this service,
 * which would otherwise spend heap and request threads proxying megabytes that S3 will accept for
 * free. It then calls the confirm endpoint with [key].
 */
data class ImageUploadResponse(
    val uploadUrl: String,
    val key: String,
    val contentType: String,
)

/** What a seller sends once the PUT has succeeded. */
data class ImageConfirmRequest(
    @field:NotBlank
    @field:Size(max = 300)
    val key: String,
)
