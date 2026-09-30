package com.example.productsearch.search

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SearchQueryBuilderTest {
    private val builder = SearchQueryBuilder(SearchProperties())

    private fun json(criteria: SearchCriteria) = builder.build(criteria).toString()

    @Test
    fun `text query is fuzzy across the weighted fields`() {
        val request = json(SearchCriteria(query = "wireles headphons"))

        assertContains(request, "\"fuzziness\":\"AUTO\"")
        assertContains(request, "\"name^3\"")
        assertContains(request, "\"brand.text^2\"")
        assertContains(request, "\"match_phrase\"")
    }

    @Test
    fun `blank query matches everything`() {
        val request = json(SearchCriteria(query = "  "))

        assertContains(request, "\"match_all\"")
        assertFalse(request.contains("multi_match"))
    }

    @Test
    fun `filters go into the filter clause`() {
        val request =
            json(
                SearchCriteria(
                    categories = listOf("audio"),
                    brands = listOf("Sony"),
                    minPrice = BigDecimal("10"),
                    maxPrice = BigDecimal("100"),
                    inStock = true,
                ),
            )

        assertContains(request, "\"filter\"")
        assertContains(request, "\"gte\":10.0")
        assertContains(request, "\"lte\":100.0")
        assertContains(request, "\"inStock\":{\"value\":true}")
        assertContains(request, "\"category\":[\"audio\"]")
    }

    @Test
    fun `price sort falls back to score and id`() {
        val request = json(SearchCriteria(sort = SortOption.PRICE_DESC))

        assertContains(
            request,
            "\"sort\":[{\"price\":{\"order\":\"desc\"}},{\"_score\":{\"order\":\"desc\"}},{\"id\":{\"order\":\"asc\"}}]",
        )
    }

    @Test
    fun `pagination becomes from and size`() {
        val request = json(SearchCriteria(page = 3, size = 10))

        assertContains(request, "\"from\":30")
        assertContains(request, "\"size\":10")
    }

    @Test
    fun `highlights are html-encoded`() {
        assertContains(json(SearchCriteria(query = "x")), "\"encoder\":\"html\"")
    }
}
