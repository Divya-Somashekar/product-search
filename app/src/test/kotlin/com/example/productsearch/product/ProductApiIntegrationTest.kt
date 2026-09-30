package com.example.productsearch.product

import com.example.productsearch.IntegrationTest
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import kotlin.test.Test

@IntegrationTest
class ProductApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `created, updated and deleted products show up in search`() {
        val location =
            mockMvc
                .post("/api/v1/products") {
                    contentType = MediaType.APPLICATION_JSON
                    content = body(name = "Zephyr Studio Monitor Headphones", price = "129.00")
                }.andExpect {
                    status { isCreated() }
                    header { exists("Location") }
                    jsonPath("$.currency") { value("EUR") }
                    jsonPath("$.inStock") { value(true) }
                }.andReturn()
                .response
                .getHeader("Location")!!

        mockMvc
            .get("/api/v1/products/search") { param("q", "zephyr") }
            .andExpect { jsonPath("$.items[*].name", hasItem("Zephyr Studio Monitor Headphones")) }

        mockMvc
            .put(location) {
                contentType = MediaType.APPLICATION_JSON
                content = body(name = "Zephyr Studio Monitor Headphones II", price = "99.00", stock = 0)
            }.andExpect {
                status { isOk() }
                jsonPath("$.version") { value(1) }
                jsonPath("$.inStock") { value(false) }
            }

        mockMvc
            .get("/api/v1/products/search") {
                param("q", "zephyr")
                param("maxPrice", "100")
            }.andExpect { jsonPath("$.items[0].name") { value("Zephyr Studio Monitor Headphones II") } }

        mockMvc.delete(location).andExpect { status { isNoContent() } }
        mockMvc.get(location).andExpect { status { isNotFound() } }
        mockMvc
            .get("/api/v1/products/search") { param("q", "zephyr") }
            .andExpect { jsonPath("$.items[*].name", not(hasItem("Zephyr Studio Monitor Headphones II"))) }
    }

    @Test
    fun `rejects invalid products`() {
        listOf(
            body(name = " ", price = "10.00"),
            body(name = "Negative", price = "-1.00"),
            body(name = "Too precise", price = "1.001"),
            """{"name":"Missing fields"}""",
        ).forEach { json ->
            mockMvc
                .post("/api/v1/products") {
                    contentType = MediaType.APPLICATION_JSON
                    content = json
                }.andExpect {
                    status { isBadRequest() }
                    content { contentType("application/problem+json") }
                }
        }
    }

    @Test
    fun `reindex rebuilds the index behind the alias`() {
        mockMvc
            .post("/api/v1/admin/reindex")
            .andExpect {
                status { isOk() }
                jsonPath("$.index") { exists() }
                jsonPath("$.documents") { isNumber() }
            }
        mockMvc.get("/api/v1/products/search").andExpect { status { isOk() } }
    }

    @Test
    fun `unknown product is a 404 problem`() {
        mockMvc
            .get("/api/v1/products/00000000-0000-0000-0000-000000000000")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.status") { value(404) }
            }
    }

    @Test
    fun `echoes a caller supplied request id`() {
        mockMvc
            .get("/api/v1/products") { header("X-Request-Id", "abc-123") }
            .andExpect { header { string("X-Request-Id", "abc-123") } }
    }

    private fun body(
        name: String,
        price: String,
        stock: Int = 5,
    ) = """
        {"name":"$name","description":"Closed-back studio headphones","category":"audio",
         "brand":"Zephyr","price":$price,"stockQuantity":$stock}
        """
}
