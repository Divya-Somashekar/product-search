package com.example.productsearch

import com.example.productsearch.catalog.TestProductImagesConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import

/** Shared setup so every integration test reuses one application context and one set of containers. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(
    properties = [
        "search.refresh=wait_for",
        "logging.structured.format.console=",
        // No AWS here. A bucket name satisfies the startup check and a region lets the presigner
        // build; neither is ever contacted, because nothing signs a URL unless a product has an
        // image_key and the demo data that sets one is not loaded under this profile.
        "catalog.images.bucket=test-product-images",
        "catalog.images.region=eu-west-3",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestProductImagesConfiguration::class)
annotation class IntegrationTest
