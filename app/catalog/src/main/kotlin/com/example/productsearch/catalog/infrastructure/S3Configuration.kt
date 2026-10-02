package com.example.productsearch.catalog.infrastructure

import com.example.productsearch.catalog.config.CatalogProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3ClientBuilder
import software.amazon.awssdk.services.s3.presigner.S3Presigner

/**
 * The S3 clients.
 *
 * Credentials always come from the SDK's default chain — never from configuration. In the cluster
 * that chain finds `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`, delivered by External Secrets
 * Operator from SSM; on EKS it finds a projected IRSA token and no static key exists at all. The
 * move to EKS therefore changes no code here, which is the point of not naming a credential.
 *
 * The region is read from configuration when set, because unlike credentials it is resolved
 * *eagerly*: the presigner captures its region when built, so with no region on the environment
 * and none configured, the context fails at startup rather than at the first request. A test
 * context has no ambient AWS environment, so it sets `catalog.images.region` instead.
 *
 * The region must also match the bucket's own. A SigV4 signature is scoped to a region, so
 * signing with the wrong one yields links that fail with a 403 — which reads like a permissions
 * problem and is not one.
 */
@Configuration
class S3Configuration(
    private val properties: CatalogProperties,
) {
    @Bean
    fun s3Client(): S3Client = S3Client.builder().applyRegion().build()

    /** Signs URLs locally using credentials this process already holds; makes no network call. */
    @Bean
    fun s3Presigner(): S3Presigner =
        S3Presigner
            .builder()
            .also { builder -> configuredRegion()?.let(builder::region) }
            .build()

    /**
     * Fails startup when the bucket was never configured, instead of presigning valid-looking
     * URLs against an empty bucket name and returning errors that look like missing products.
     */
    @Bean
    fun imagesBucketCheck(): BucketConfigured {
        require(properties.images.bucket.isNotBlank()) {
            "catalog.images.bucket is not set; expected CATALOG_IMAGES_BUCKET in the environment"
        }
        return BucketConfigured(properties.images.bucket)
    }

    private fun S3ClientBuilder.applyRegion(): S3ClientBuilder = also { builder -> configuredRegion()?.let(builder::region) }

    /** Null when nothing is configured, which leaves the SDK to resolve it as usual. */
    private fun configuredRegion(): Region? =
        properties.images.region
            .takeIf { it.isNotBlank() }
            ?.let(Region::of)

    /** Marker for the startup check above; nothing injects it. */
    data class BucketConfigured(
        val bucket: String,
    )
}
