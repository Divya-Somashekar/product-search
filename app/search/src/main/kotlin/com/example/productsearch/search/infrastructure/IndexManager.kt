package com.example.productsearch.search.infrastructure

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch._types.Refresh
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation
import co.elastic.clients.elasticsearch.indices.update_aliases.Action
import com.example.productsearch.catalog.contract.ProductSnapshot
import com.example.productsearch.search.config.SearchProperties
import com.example.productsearch.search.domain.IndexLifecycle
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
) : IndexLifecycle {
    private val log = LoggerFactory.getLogger(javaClass)

    val alias: String get() = properties.alias

    override fun aliasExists(): Boolean = elasticsearch { client.indices().existsAlias { it.name(alias) }.value() }

    override fun newIndexName(): String = "$alias-${TIMESTAMP.format(clock.instant())}"

    override fun createIndex(
        name: String,
        attachAlias: Boolean,
    ): Boolean =
        elasticsearch {
            try {
                resourceLoader.getResource(properties.indexDefinition).inputStream.use { definition ->
                    client.indices().create { request ->
                        request.withJson(definition).index(name)
                        if (attachAlias) request.aliases(alias) { it }
                        request
                    }
                }
                log.info("Created index {}{}", name, if (attachAlias) " with alias $alias" else "")
                true
            } catch (e: ElasticsearchException) {
                if (e.error().type() != "resource_already_exists_exception") throw e
                false
            }
        }

    override fun bulkIndex(
        index: String,
        products: List<ProductSnapshot>,
    ) {
        if (products.isEmpty()) return
        val documents = products.map(ProductDocument::of)
        val response =
            elasticsearch {
                client.bulk { request ->
                    request.index(index).operations(
                        documents.map { doc -> BulkOperation.of { op -> op.index { it.id(doc.id).document(doc.toSource()) } } },
                    )
                }
            }
        if (response.errors()) {
            val firstError = response.items().firstNotNullOfOrNull { it.error() }
            throw IllegalStateException("Bulk indexing into $index failed: ${firstError?.reason()}")
        }
    }

    override fun swapAlias(index: String): Set<String> {
        val previous = indicesBehindAlias() - index
        elasticsearch {
            client.indices().updateAliases { request ->
                request.actions(
                    buildList {
                        add(Action.of { a -> a.add { it.index(index).alias(alias) } })
                        previous.forEach { old -> add(Action.of { a -> a.remove { it.index(old).alias(alias) } }) }
                    },
                )
            }
        }
        log.info("Alias {} now points to {} (was {})", alias, index, previous)
        return previous
    }

    override fun deleteIndex(name: String) {
        elasticsearch { client.indices().delete { it.index(name) } }
        log.info("Deleted index {}", name)
    }

    override fun refresh(index: String) {
        elasticsearch { client.indices().refresh { it.index(index) } }
    }

    fun refreshPolicy(): Refresh =
        when (properties.refresh) {
            SearchProperties.RefreshPolicy.FALSE -> Refresh.False
            SearchProperties.RefreshPolicy.WAIT_FOR -> Refresh.WaitFor
        }

    private fun indicesBehindAlias(): Set<String> =
        if (aliasExists()) {
            elasticsearch {
                client
                    .indices()
                    .getAlias { it.name(alias) }
                    .aliases()
                    .keys
            }
        } else {
            emptySet()
        }

    private companion object {
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS").withZone(ZoneOffset.UTC)
    }
}
