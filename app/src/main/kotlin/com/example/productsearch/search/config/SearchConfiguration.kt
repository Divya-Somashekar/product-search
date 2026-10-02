package com.example.productsearch.search.config

import com.example.productsearch.search.application.ReindexService
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class SearchConfiguration {
    /**
     * Makes sure the alias exists before traffic arrives. Fails startup if Elasticsearch is
     * unreachable, so Kubernetes restarts the pod instead of serving a broken search.
     */
    @Bean
    fun searchIndexInitializer(reindexService: ReindexService) = ApplicationRunner { reindexService.initialize() }
}
