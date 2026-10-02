package com.example.productsearch.catalog.api

import com.example.productsearch.catalog.domain.ProductNotFoundException
import com.example.productsearch.shared.web.problem
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Catalog failures as RFC 9457 problems. Framework-level errors are handled in `shared.web`. */
@RestControllerAdvice
class CatalogExceptionHandler {
    @ExceptionHandler(ProductNotFoundException::class)
    fun notFound(e: ProductNotFoundException) = problem(HttpStatus.NOT_FOUND, e.message)
}
