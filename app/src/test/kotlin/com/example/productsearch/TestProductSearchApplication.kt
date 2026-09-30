package com.example.productsearch

import org.springframework.boot.fromApplication
import org.springframework.boot.with

/** `./gradlew bootTestRun`: the app against throwaway containers, with the demo catalogue. */
fun main(args: Array<String>) {
    fromApplication<ProductSearchApplication>()
        .with(TestcontainersConfiguration::class)
        .run("--spring.profiles.active=demo", "--search.refresh=wait_for", *args)
}
