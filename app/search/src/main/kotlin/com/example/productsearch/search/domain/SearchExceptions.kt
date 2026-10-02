package com.example.productsearch.search.domain

class InvalidSearchRequestException(
    message: String,
) : RuntimeException(message)

class ReindexInProgressException : RuntimeException("A reindex is already running")

/** The search engine could not be reached or refused the request; the adapter translates its errors into this. */
class SearchUnavailableException(
    cause: Throwable,
) : RuntimeException("Search is temporarily unavailable", cause)
