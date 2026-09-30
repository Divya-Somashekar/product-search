package com.example.productsearch.search

import com.example.productsearch.IntegrationTest
import com.example.productsearch.product.ProductRepository
import com.example.productsearch.product.ProductRequest
import com.example.productsearch.product.ProductService
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.lessThanOrEqualTo
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import kotlin.test.Test

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductSearchIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val products: ProductService,
    @Autowired private val repository: ProductRepository,
    @Autowired private val reindex: ReindexService,
) {
    @BeforeAll
    fun catalogue() {
        repository.deleteAll()
        reindex.reindex()
        listOf(
            product("Sony WH-1000XM5 Wireless Headphones", "Noise cancelling over-ear headphones", "audio", "Sony", "349.00", 5),
            product("JBL Tune 520BT Wireless Headphones", "On-ear Bluetooth headphones with deep bass", "audio", "JBL", "44.90", 10),
            product("Anker Q30 Wireless Headphones", "Hybrid noise cancelling headphones", "audio", "Anker", "79.99", 0),
            product(
                "Sennheiser HD 450BT",
                "Wireless over-ear headphones with active noise cancellation",
                "audio",
                "Sennheiser",
                "99.00",
                3,
            ),
            product("Logitech MX Keys Wireless Keyboard", "Illuminated keyboard for Mac and Windows", "computing", "Logitech", "119.99", 8),
            product("JBL Flip 6 Speaker", "Portable waterproof Bluetooth speaker", "audio", "JBL", "119.00", 4),
            product("<script>alert(1)</script> Headphones", "Wired headphones", "audio", "Acme", "9.99", 1),
        ).forEach(products::create)
    }

    @Test
    fun `typos still find wireless headphones, with highlighted snippets`() {
        mockMvc
            .get("/api/v1/products/search") { param("q", "wireles headphons") }
            .andExpect {
                status { isOk() }
                jsonPath("$.items[*].name", hasItem("Sony WH-1000XM5 Wireless Headphones"))
                jsonPath("$.items[*].name", hasItem("JBL Tune 520BT Wireless Headphones"))
                jsonPath("$.items[*].name", not(hasItem("Logitech MX Keys Wireless Keyboard")))
                jsonPath("$.items[0].highlights.name[0]", containsString("<em>Wireless</em>"))
                jsonPath("$.items[0].score") { isNumber() }
            }
    }

    @Test
    fun `price filter excludes more expensive items`() {
        mockMvc
            .get("/api/v1/products/search") {
                param("q", "wireless headphones")
                param("maxPrice", "100")
            }.andExpect {
                status { isOk() }
                // JBL 44.90, Anker 79.99, Sennheiser 99.00 (matches via its description).
                jsonPath("$.total") { value(3) }
                jsonPath("$.items[*].price", everyItem(lessThanOrEqualTo(100.0)))
                jsonPath("$.items[*].name", not(hasItem("Sony WH-1000XM5 Wireless Headphones")))
            }
    }

    @Test
    fun `availability filter drops out-of-stock items`() {
        mockMvc
            .get("/api/v1/products/search") {
                param("q", "headphones")
                param("inStock", "true")
            }.andExpect {
                status { isOk() }
                jsonPath("$.items[*].inStock", everyItem(org.hamcrest.Matchers.equalTo(true)))
                jsonPath("$.items[*].name", not(hasItem("Anker Q30 Wireless Headphones")))
            }
    }

    @Test
    fun `sorts by price in both directions`() {
        mockMvc
            .get("/api/v1/products/search") {
                param("category", "computing", "AUDIO")
                param("minPrice", "40")
                param("sort", "price_asc")
            }.andExpect {
                status { isOk() }
                jsonPath("$.items[*].price", contains(44.9, 79.99, 99.0, 119.0, 119.99, 349.0))
            }
        mockMvc
            .get("/api/v1/products/search") {
                param("brand", "JBL")
                param("sort", "PRICE_DESC")
            }.andExpect {
                jsonPath("$.items[*].price", contains(119.0, 44.9))
            }
    }

    @Test
    fun `paginates and reports the full total`() {
        mockMvc
            .get("/api/v1/products/search") {
                param("category", "audio")
                param("sort", "price_asc")
                param("page", "1")
                param("size", "2")
            }.andExpect {
                status { isOk() }
                jsonPath("$.total") { value(6) }
                jsonPath("$.page") { value(1) }
                jsonPath("$.items.length()") { value(2) }
                jsonPath("$.items[*].price", contains(79.99, 99.0))
            }
    }

    @Test
    fun `returns facets for the matching set`() {
        mockMvc
            .get("/api/v1/products/search") { param("q", "headphones") }
            .andExpect {
                jsonPath("$.facets.category[0].value") { value("audio") }
                jsonPath("$.facets.brand[*].value", hasItem("jbl"))
            }
    }

    @Test
    fun `highlights never echo raw html from product data`() {
        mockMvc
            .get("/api/v1/products/search") { param("q", "alert") }
            .andExpect {
                jsonPath("$.items[0].highlights.name[0]", containsString("&lt;script&gt;"))
                jsonPath("$.items[0].highlights.name[0]", not(containsString("<script>")))
            }
    }

    @Test
    fun `rejects invalid parameters with problem details`() {
        listOf(
            mapOf("size" to "500"),
            mapOf("page" to "-1"),
            mapOf("minPrice" to "50", "maxPrice" to "10"),
            mapOf("sort" to "cheapest"),
            mapOf("page" to "500", "size" to "100"),
        ).forEach { params ->
            mockMvc
                .get("/api/v1/products/search") { params.forEach { (k, v) -> param(k, v) } }
                .andExpect {
                    status { isBadRequest() }
                    content { contentType("application/problem+json") }
                }
        }
    }

    private fun product(
        name: String,
        description: String,
        category: String,
        brand: String,
        price: String,
        stock: Int,
    ) = ProductRequest(name, description, category, brand, BigDecimal(price), stock)
}
