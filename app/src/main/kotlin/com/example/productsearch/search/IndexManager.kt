package com.example.productsearch.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch.indices.update_aliases.Action
import org.slf4j.LoggerFactory
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Owns the index lifecycle: versioned concrete indices behind a single alias. */
@Component
class IndexManager(
    private val client: ElasticsearchClient,
    private val properties: SearchProperties,
    private val resourceLoader: ResourceLoader,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    val alias: String get() = properties.alias

    fun aliasExists(): Boolean = client.indices().existsAlias { it.name(alias) }.value()

    fun indicesBehindAlias(): Set<String> =
        if (aliasExists()) {
            client
                .indices()
                .getAlias { it.name(alias) }
                .aliases()
                .keys
        } else {
            emptySet()
        }

    fun newIndexName(): String = "$alias-${TIMESTAMP.format(clock.instant())}"

    /**
     * Creates [name] from the index definition. Returns false if it already exists, which is how
     * concurrently starting replicas agree on who creates the first index.
     */
    fun createIndex(
        name: String,
        attachAlias: Boolean,
    ): Boolean =
        try {
            resourceLoader.getResource(properties.indexDefinition).inputStream.use { definition ->
                client.indices().create { request ->
                    request.withJson(definition).index(name)
                    if (attachAlias) request.aliases(alias) { it }
                    request
                }
            }
            log.info("Created index {} (alias attached: {})", name, attachAlias)
            true
        } catch (e: ElasticsearchException) {
            if (e.error().type() != "resource_already_exists_exception") throw e
            false
        }

    /** Atomically points the alias at [index] only, returning the indices it was taken from. */
    fun swapAlias(index: String): Set<String> {
        val previous = indicesBehindAlias() - index
        client.indices().updateAliases { request ->
            request.actions(
                buildList {
                    add(Action.of { a -> a.add { it.index(index).alias(alias) } })
                    previous.forEach { old -> add(Action.of { a -> a.remove { it.index(old).alias(alias) } }) }
                },
            )
        }
        log.info("Alias {} now points to {} (was {})", alias, index, previous)
        return previous
    }

    fun deleteIndex(name: String) {
        client.indices().delete { it.index(name) }
        log.info("Deleted index {}", name)
    }

    fun refresh(index: String = alias) {
        client.indices().refresh { it.index(index) }
    }

    fun refreshPolicy(): Refresh =
        when (properties.refresh) {
            SearchProperties.RefreshPolicy.FALSE -> Refresh.False
            SearchProperties.RefreshPolicy.WAIT_FOR -> Refresh.WaitFor
        }

    private companion object {
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS").withZone(ZoneOffset.UTC)
    }
}
