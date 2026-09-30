package com.example.productsearch

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import

/** Shared setup so every integration test reuses one application context and one set of containers. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(properties = ["search.refresh=wait_for", "logging.structured.format.console="])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
annotation class IntegrationTest
