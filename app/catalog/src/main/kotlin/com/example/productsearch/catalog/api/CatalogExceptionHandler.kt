package com.example.productsearch.catalog.api

import com.example.productsearch.catalog.domain.ProductImageRejectedException
import com.example.productsearch.catalog.domain.ProductNotFoundException
import com.example.productsearch.shared.web.problem
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Catalog failures as RFC 9457 problems. Framework-level errors are handled in `shared.web`. */
@RestControllerAdvice
class CatalogExceptionHandler {
    @ExceptionHandler(ProductNotFoundException::class)
    fun notFound(e: ProductNotFoundException) = problem(HttpStatus.NOT_FOUND, e.message)

    /** The detail is safe to return: it describes what the caller sent, never anything internal. */
    @ExceptionHandler(ProductImageRejectedException::class)
    fun imageRejected(e: ProductImageRejectedException) = problem(HttpStatus.BAD_REQUEST, e.message)

    /**
     * Only the catalog has versioned entities, so this belongs here rather than in `shared.web`:
     * keeping it there would mean `shared` — and so a future standalone search service — had to
     * carry a dependency on spring-orm for an exception it can never throw.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun concurrentUpdate() = problem(HttpStatus.CONFLICT, "The product was changed concurrently; reload and retry")
}
