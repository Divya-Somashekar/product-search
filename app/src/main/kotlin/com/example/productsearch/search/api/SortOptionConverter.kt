package com.example.productsearch.search.api

import com.example.productsearch.search.domain.SortOption
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/** Accepts the documented lower-case values (`price_asc`) rather than enum constant names. */
@Component
class SortOptionConverter : Converter<String, SortOption> {
    override fun convert(source: String): SortOption = SortOption.fromParam(source)
}
