package com.example.productsearch.shared.web

import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Framework-level errors (validation, type mismatches, malformed bodies) are turned into
 * problem responses by the base class. Each bounded context maps its own failures in its
 * `api` package.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler()
