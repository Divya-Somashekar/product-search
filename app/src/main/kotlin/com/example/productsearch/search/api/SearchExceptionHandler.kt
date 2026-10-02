package com.example.productsearch.search.api

import com.example.productsearch.search.domain.InvalidSearchRequestException
import com.example.productsearch.search.domain.ReindexInProgressException
import com.example.productsearch.search.domain.SearchUnavailableException
import com.example.productsearch.shared.web.problem
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Search failures as RFC 9457 problems; what went wrong with the engine stays in the logs. */
@RestControllerAdvice
class SearchExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidSearchRequestException::class)
    fun invalidSearch(e: InvalidSearchRequestException) = problem(HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler(ReindexInProgressException::class)
    fun reindexRunning(e: ReindexInProgressException) = problem(HttpStatus.CONFLICT, e.message)

    @ExceptionHandler(SearchUnavailableException::class)
    fun searchUnavailable(e: SearchUnavailableException): ProblemDetail {
        log.error("Elasticsearch request failed", e)
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Search is temporarily unavailable")
    }
}
