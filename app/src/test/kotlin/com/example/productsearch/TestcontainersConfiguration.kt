package com.example.productsearch

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.testcontainers.service.connection.Ssl
import org.springframework.context.annotation.Bean
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/** Real Postgres and Elasticsearch, versions pinned to what the deployment runs. */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    @Ssl
    fun elasticsearchContainer(): ElasticsearchContainer =
        ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:$ELASTICSEARCH_VERSION"))
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withEnv("cluster.routing.allocation.disk.threshold_enabled", "false")

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:$POSTGRES_VERSION"))

    companion object {
        const val ELASTICSEARCH_VERSION = "9.4.5"
        const val POSTGRES_VERSION = "18-alpine"
    }
}
