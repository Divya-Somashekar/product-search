package com.example.productsearch.search.api

import com.example.productsearch.search.application.ProductSearchService
import com.example.productsearch.search.domain.SearchCriteria
import com.example.productsearch.search.domain.SortOption
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/products")
class SearchController(
    private val searchService: ProductSearchService,
) {
    @GetMapping("/search")
    fun search(
        @RequestParam(required = false) @Size(max = 200) q: String?,
        @RequestParam(required = false) category: List<
            @Size(max = 100)
            String,
        >?,
        @RequestParam(required = false) brand: List<
            @Size(max = 100)
            String,
        >?,
        @RequestParam(required = false) @DecimalMin("0") minPrice: BigDecimal?,
        @RequestParam(required = false) @DecimalMin("0") maxPrice: BigDecimal?,
        @RequestParam(required = false) inStock: Boolean?,
        @RequestParam(defaultValue = "relevance") sort: SortOption,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): SearchResponse =
        searchService
            .search(
                SearchCriteria(
                    query = q,
                    categories = category.orEmpty().filter { it.isNotBlank() },
                    brands = brand.orEmpty().filter { it.isNotBlank() },
                    minPrice = minPrice,
                    maxPrice = maxPrice,
                    inStock = inStock,
                    sort = sort,
                    page = page,
                    size = size,
                ),
            ).toResponse()
}
