package com.example.productsearch.search.domain

enum class SortOption(
    val param: String,
) {
    RELEVANCE("relevance"),
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc"),
    ;

    companion object {
        fun fromParam(value: String): SortOption =
            entries.firstOrNull { it.param.equals(value.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("sort must be one of ${entries.joinToString { it.param }}")
    }
}
