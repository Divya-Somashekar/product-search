package com.example.productsearch.catalog.application

import com.example.productsearch.catalog.config.CatalogProperties
import com.example.productsearch.catalog.domain.Product
import com.example.productsearch.catalog.domain.ProductCommand
import com.example.productsearch.catalog.domain.ProductDeleted
import com.example.productsearch.catalog.domain.ProductNotFoundException
import com.example.productsearch.catalog.domain.ProductPage
import com.example.productsearch.catalog.domain.ProductRepository
import com.example.productsearch.catalog.domain.ProductSnapshot
import com.example.productsearch.catalog.domain.ProductUpserted
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** The catalog's use cases. Each write commits to Postgres, then announces itself for indexing. */
@Service
class ProductService(
    private val repository: ProductRepository,
    private val events: ApplicationEventPublisher,
    private val catalog: CatalogProperties,
) {
    @Transactional
    fun create(command: ProductCommand): ProductSnapshot = publishUpsert(Product.from(command, catalog.currency))

    @Transactional(readOnly = true)
    fun get(id: UUID): ProductSnapshot = find(id).snapshot()

    @Transactional(readOnly = true)
    fun list(
        page: Int,
        size: Int,
    ): ProductPage {
        val result = repository.findAll(PageRequest.of(page, size, Sort.by("createdAt", "id")))
        return ProductPage(result.content.map(Product::snapshot), page, size, result.totalElements)
    }

    @Transactional
    fun update(
        id: UUID,
        command: ProductCommand,
    ): ProductSnapshot = publishUpsert(find(id).also { it.update(command) })

    @Transactional
    fun delete(id: UUID) {
        repository.delete(find(id))
        events.publishEvent(ProductDeleted(id))
    }

    private fun find(id: UUID): Product = repository.findByIdOrNull(id) ?: throw ProductNotFoundException(id)

    private fun publishUpsert(product: Product): ProductSnapshot =
        repository.saveAndFlush(product).snapshot().also { events.publishEvent(ProductUpserted(it)) }
}
