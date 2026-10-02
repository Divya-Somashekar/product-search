package com.example.productsearch.catalog.config

import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * The catalog prices everything in one currency, which is what makes price filters and
 * price sorting meaningful without conversion.
 */
@Validated
@ConfigurationProperties("catalog")
data class CatalogProperties(
    @field:Pattern(regexp = "[A-Z]{3}")
    val currency: String = "EUR",
)
