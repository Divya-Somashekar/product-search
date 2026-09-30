package com.example.productsearch.product

import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ProductService(
    private val repository: ProductRepository,
    private val events: ApplicationEventPublisher,
    private val catalog: CatalogProperties,
) {
    @Transactional
    fun create(request: ProductRequest): ProductResponse {
        val product =
            Product(
                name = request.name.trim(),
                description = request.description.trim(),
                category = request.category.trim(),
                brand = request.brand.trim(),
                price = request.price,
                currency = catalog.currency,
                stockQuantity = request.stockQuantity,
            )
        return publishUpsert(repository.saveAndFlush(product))
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): ProductResponse = find(id).toResponse()

    @Transactional(readOnly = true)
    fun list(
        page: Int,
        size: Int,
    ): PageResponse<ProductResponse> {
        val result = repository.findAll(PageRequest.of(page, size, Sort.by("createdAt", "id")))
        return PageResponse(result.content.map { it.toResponse() }, page, size, result.totalElements)
    }

    @Transactional
    fun update(
        id: UUID,
        request: ProductRequest,
    ): ProductResponse {
        val product =
            find(id).apply {
                name = request.name.trim()
                description = request.description.trim()
                category = request.category.trim()
                brand = request.brand.trim()
                price = request.price
                stockQuantity = request.stockQuantity
            }
        return publishUpsert(repository.saveAndFlush(product))
    }

    @Transactional
    fun delete(id: UUID) {
        repository.delete(find(id))
        events.publishEvent(ProductDeleted(id))
    }

    private fun find(id: UUID): Product = repository.findByIdOrNull(id) ?: throw ProductNotFoundException(id)

    private fun publishUpsert(product: Product): ProductResponse = product.toResponse().also { events.publishEvent(ProductUpserted(it)) }
}
