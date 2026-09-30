package com.example.productsearch.common

import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.transport.TransportException
import com.example.productsearch.product.ProductNotFoundException
import com.example.productsearch.search.InvalidSearchRequestException
import com.example.productsearch.search.ReindexInProgressException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Every error leaves as RFC 9457 `application/problem+json`. Framework errors (validation,
 * type mismatches, malformed bodies) are handled by the base class; internal details never
 * reach the client.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ProductNotFoundException::class)
    fun notFound(e: ProductNotFoundException) = problem(HttpStatus.NOT_FOUND, e.message)

    @ExceptionHandler(InvalidSearchRequestException::class)
    fun invalidSearch(e: InvalidSearchRequestException) = problem(HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler(ReindexInProgressException::class)
    fun reindexRunning(e: ReindexInProgressException) = problem(HttpStatus.CONFLICT, e.message)

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun concurrentUpdate() = problem(HttpStatus.CONFLICT, "The product was changed concurrently; reload and retry")

    @ExceptionHandler(ElasticsearchException::class, TransportException::class)
    fun searchUnavailable(e: Exception): ProblemDetail {
        log.error("Elasticsearch request failed", e)
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Search is temporarily unavailable")
    }

    private fun problem(
        status: HttpStatus,
        detail: String?,
    ) = ProblemDetail.forStatusAndDetail(status, detail ?: status.reasonPhrase)
}
