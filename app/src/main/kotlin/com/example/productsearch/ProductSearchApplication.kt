package com.example.productsearch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ProductSearchApplication

fun main(args: Array<String>) {
    runApplication<ProductSearchApplication>(*args)
}
