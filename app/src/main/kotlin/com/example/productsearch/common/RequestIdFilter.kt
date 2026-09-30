package com.example.productsearch.common

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Tags every log line of a request with a correlation id, taken from `X-Request-Id` when the
 * caller (or ingress) sends a sane one, and echoes it back so clients can quote it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val requestId = request.getHeader(HEADER)?.takeIf { VALID.matches(it) } ?: UUID.randomUUID().toString()
        MDC.put(MDC_KEY, requestId)
        response.setHeader(HEADER, requestId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
        private val VALID = Regex("[A-Za-z0-9._-]{1,64}")
    }
}
