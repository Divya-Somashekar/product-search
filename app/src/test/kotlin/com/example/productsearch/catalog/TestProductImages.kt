package com.example.productsearch.catalog

import com.example.productsearch.catalog.domain.ProductImages
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.net.URI
import java.util.UUID

/**
 * Stands in for S3 so the suite never needs AWS credentials or network.
 *
 * `@Primary` rather than a bean override: the real [com.example.productsearch.catalog
 * .infrastructure.S3ProductImages] stays in the context, and this one simply wins injection.
 * Nothing in a test then signs a URL, which is what keeps the tests hermetic.
 *
 * Registered from the shared `@IntegrationTest` setup on purpose. Importing it from a single test
 * class instead would give that class a different context cache key, and a second application
 * context means a second Postgres and a second Elasticsearch container.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestProductImagesConfiguration {
    @Bean
    @Primary
    fun testProductImages() = TestProductImages()
}

/**
 * An in-memory object store. [store] is what a successful upload would have produced, so a test
 * can describe the state S3 would be in without performing one.
 */
class TestProductImages : ProductImages {
    private val objects = mutableMapOf<String, ProductImages.StoredImage>()

    fun store(
        key: String,
        contentType: String = "image/png",
        sizeBytes: Long = 1_024,
    ) {
        objects[key] = ProductImages.StoredImage(contentType, sizeBytes)
    }

    override fun viewUrl(key: String): URI = URI.create("https://s3.test.invalid/$key?signature=view")

    override fun uploadTarget(
        productId: UUID,
        contentType: String,
    ): ProductImages.UploadTarget {
        val key = "products/$productId/${UUID.randomUUID()}.png"
        return ProductImages.UploadTarget(key, URI.create("https://s3.test.invalid/$key?signature=put"))
    }

    override fun describe(key: String): ProductImages.StoredImage? = objects[key]
}
