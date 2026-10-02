package com.example.productsearch.shared.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

/** Every error leaves as RFC 9457 `application/problem+json`; internal details never reach the client. */
fun problem(
    status: HttpStatus,
    detail: String?,
): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail ?: status.reasonPhrase)
