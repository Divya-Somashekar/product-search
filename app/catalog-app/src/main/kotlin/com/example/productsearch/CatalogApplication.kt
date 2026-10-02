package com.example.productsearch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/** The catalog service. Owns Postgres; knows nothing about Elasticsearch or search. */
@SpringBootApplication
@ConfigurationPropertiesScan
class CatalogApplication

fun main(args: Array<String>) {
    runApplication<CatalogApplication>(*args)
}
