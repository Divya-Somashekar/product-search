package com.example.productsearch.search.config

import com.example.productsearch.search.application.ReindexService
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SearchConfiguration {
    /**
     * Makes sure the alias exists before traffic arrives. Fails startup if Elasticsearch is
     * unreachable, so Kubernetes restarts the pod instead of serving a broken search.
     */
    @Bean
    fun searchIndexInitializer(reindexService: ReindexService) = ApplicationRunner { reindexService.initialize() }
}
