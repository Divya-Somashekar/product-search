package com.example.productsearch.catalog.domain

import java.net.URI
import java.util.UUID

/**
 * Short-lived, signed links to product image objects.
 *
 * A port rather than a direct S3 call, for the same reason [ProductRepository] is one: the use
 * cases describe what they need, and the storage service stays an implementation detail that can
 * be substituted in a test.
 */
interface ProductImages {
    /** A link a shopper's browser can GET. Expires. */
    fun viewUrl(key: String): URI

    /**
     * A link a seller's browser can PUT [contentType] bytes to, plus the key those bytes will
     * land at. The key is generated here because only this layer knows the storage layout.
     */
    fun uploadTarget(
        productId: UUID,
        contentType: String,
    ): UploadTarget

    /**
     * What is actually in storage at [key], or null if nothing is. Used to verify an upload
     * happened, and that what arrived is what was promised, before it is recorded against a
     * product.
     */
    fun describe(key: String): StoredImage?

    data class UploadTarget(
        val key: String,
        val url: URI,
    )

    data class StoredImage(
        val contentType: String?,
        val sizeBytes: Long,
    )
}
