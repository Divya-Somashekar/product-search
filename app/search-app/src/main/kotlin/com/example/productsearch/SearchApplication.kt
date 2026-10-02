package com.example.productsearch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * The search service. Owns Elasticsearch and has no database at all: it rebuilds from, and tails,
 * the catalog's HTTP feed. That is what lets it keep serving searches while the catalog is down.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class SearchApplication

fun main(args: Array<String>) {
    runApplication<SearchApplication>(*args)
}
