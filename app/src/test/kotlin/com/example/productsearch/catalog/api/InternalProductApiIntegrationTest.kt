package com.example.productsearch.catalog.api

import com.example.productsearch.IntegrationTest
import com.example.productsearch.catalog.application.ProductService
import com.example.productsearch.catalog.domain.ProductCommand
import com.example.productsearch.catalog.domain.ProductRepository
import com.example.productsearch.search.application.ReindexService
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The feed a separate search service would rebuild its index from, and the contract
 * `ReindexService` already reads through today.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InternalProductApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val products: ProductService,
    @Autowired private val repository: ProductRepository,
    @Autowired private val reindex: ReindexService,
) {
    @BeforeAll
    fun catalogue() {
        repository.deleteAll()
        reindex.reindex()
        repeat(SEEDED) { products.create(ProductCommand("Product $it", "", "misc", "Acme", BigDecimal("1.00"), 1)) }
    }

    private fun page(
        size: Int,
        after: String? = null,
    ): List<String> {
        val body =
            mockMvc
                .get("/internal/products") {
                    param("size", size.toString())
                    after?.let { param("after", it) }
                }.andExpect { status { isOk() } }
                .andReturn()
                .response
                .contentAsString
        return JsonPath.read(body, "$[*].id")
    }

    @Test
    fun `keyset pages walk the whole catalog exactly once, in id order`() {
        val seen = mutableListOf<String>()
        var after: String? = null
        var pages = 0
        while (true) {
            val ids = page(PAGE, after)
            assertTrue(pages++ < 100, "the feed never reported a last page")
            if (ids.isEmpty()) break
            seen += ids
            after = ids.last()
            if (ids.size < PAGE) break
        }

        assertEquals(SEEDED, seen.size, "every product appears exactly once")
        assertEquals(seen.size, seen.distinct().size, "no product is repeated across pages")
        assertEquals(seen.sorted(), seen, "pages are ordered by id, so the cursor cannot skip rows")
        // 23 rows in pages of 7 is 7 + 7 + 7 + 2: several full pages and a short one to stop on.
        assertEquals(4, pages, "the walk must span several pages and end on a short one")
    }

    @Test
    fun `after is exclusive`() {
        val first = page(size = 1).single()
        assertTrue(page(size = 1, after = first).single() > first, "the cursor row is not served again")
    }

    @Test
    fun `the feed emits the published contract, not an HTTP DTO of its own`() {
        mockMvc
            .get("/internal/products") { param("size", "1") }
            .andExpect {
                // ProductSnapshot carries `version`, which a consumer needs for idempotent writes.
                jsonPath("$[0].version") { exists() }
                jsonPath("$[0].currency") { value("EUR") }
                jsonPath("$[0].inStock") { value(true) }
            }
    }

    @Test
    fun `size is bounded`() {
        mockMvc.get("/internal/products") { param("size", "0") }.andExpect { status { isBadRequest() } }
        mockMvc.get("/internal/products") { param("size", "1001") }.andExpect { status { isBadRequest() } }
    }

    private companion object {
        const val SEEDED = 23
        const val PAGE = 7
    }
}
