package com.example.productsearch.catalog.config

import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * The catalog prices everything in one currency, which is what makes price filters and
 * price sorting meaningful without conversion.
 */
@Validated
@ConfigurationProperties("catalog")
data class CatalogProperties(
    @field:Pattern(regexp = "[A-Z]{3}")
    val currency: String = "EUR",
    val outbox: Outbox = Outbox(),
    val images: Images = Images(),
) {
    data class Outbox(
        /**
         * How long an outbox entry is held back from consumers after it was written.
         *
         * `seq` is handed out when the entry is inserted, but the row only becomes visible when
         * its transaction commits, and two transactions can do those in opposite orders. The
         * hold-back has to outlast the gap between an entry's insert and its commit, or a reader
         * can move its cursor past a `seq` that is still on its way. Raise it if writes are slow;
         * `POST /api/v1/admin/reindex` repairs anything that slips through.
         */
        val visibilityLag: Duration = Duration.ofSeconds(1),
    )

    /**
     * Product image storage. The bucket comes from the environment as `CATALOG_IMAGES_BUCKET`,
     * which Terraform owns and the deploy overlay sets; it has no default, so a missing value
     * fails startup rather than presigning against a bucket that does not exist.
     */
    data class Images(
        val bucket: String = "",
        /**
         * Blank means "let the SDK resolve it", which in the cluster is `AWS_REGION` from the
         * deploy overlay and on EKS comes with the IRSA credentials. Set explicitly only where
         * there is no ambient AWS environment to read — a test context, or LocalStack.
         *
         * It has to be resolvable at startup either way: the presigner reads its region when it
         * is built, not when it signs, so an unresolvable region fails the context rather than
         * the first request.
         */
        val region: String = "",
        /**
         * How long a shopper's view link stays valid. Long enough for a page and its images to
         * load on a slow connection, short enough that a copied URL is not a lasting leak of a
         * private object.
         */
        val viewUrlTtl: Duration = Duration.ofMinutes(15),
        /** How long a seller has to start the upload after asking for a link. */
        val uploadUrlTtl: Duration = Duration.ofMinutes(10),
        /**
         * Rejected after the fact, not before: a presigned PUT carries no size limit that S3 will
         * enforce, so the only honest check is to look at what arrived. See
         * `S3ProductImages.describe`.
         */
        val maxUploadBytes: Long = 5L * 1024 * 1024,
        /**
         * Content types a seller may upload. The type is bound into the PUT signature, so a
         * client that sends anything else gets a 403 from S3 rather than storing it.
         */
        val allowedContentTypes: Set<String> =
            setOf("image/jpeg", "image/png", "image/webp"),
    )
}
