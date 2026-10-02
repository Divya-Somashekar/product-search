package com.example.productsearch.catalog.api

import com.example.productsearch.IntegrationTest
import com.example.productsearch.catalog.TestProductImages
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import kotlin.test.Test

/**
 * The seller's upload flow. Everything here exercises the checks that exist because none of the
 * upload can be trusted from the client: the key is supplied by the caller, and a presigned PUT
 * enforces no size or type limit that S3 itself will apply.
 */
@IntegrationTest
class ProductImageApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val images: TestProductImages,
    @Autowired private val json: ObjectMapper,
) {
    @Test
    fun `a confirmed upload becomes a presigned link on the product`() {
        val id = createProduct()

        val key =
            mockMvc
                .post("/api/v1/products/$id/image-upload-url") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"contentType":"image/png"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.uploadUrl") { exists() }
                    jsonPath("$.key") { exists() }
                }.andReturn()
                .response
                .let { json.readTree(it.contentAsString)["key"].asString() }

        // Asking for a link records nothing: until the bytes exist the product has no image.
        mockMvc.get("/api/v1/products/$id").andExpect { jsonPath("$.imageUrl") { value(null) } }

        // What the browser's PUT would have left behind.
        images.store(key)

        mockMvc
            .post("/api/v1/products/$id/image") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"key":"$key"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.imageUrl") { value("https://s3.test.invalid/$key?signature=view") }
            }

        mockMvc.get("/api/v1/products/$id").andExpect { jsonPath("$.imageUrl") { exists() } }
    }

    @Test
    fun `refuses a key belonging to another product`() {
        val mine = createProduct()
        val theirs = createProduct()
        val key = "products/$theirs/stolen.png"
        images.store(key)

        // Without this check a seller could point their product at any object in the bucket,
        // including an image somebody else uploaded.
        confirm(mine, key).andExpect {
            status { isBadRequest() }
            content { contentType("application/problem+json") }
            jsonPath("$.detail") { value("key $key does not belong to product $mine") }
        }
    }

    @Test
    fun `refuses a key with nothing stored at it`() {
        val id = createProduct()

        // The link was issued and never used, or it expired.
        confirm(id, "products/$id/never-uploaded.png").andExpect {
            status { isBadRequest() }
            jsonPath("$.detail") { value("nothing is stored at products/$id/never-uploaded.png; upload the image first") }
        }
    }

    @Test
    fun `refuses an object that is too large or the wrong type`() {
        val id = createProduct()

        val oversized = "products/$id/oversized.png"
        images.store(oversized, sizeBytes = 6L * 1024 * 1024)
        confirm(id, oversized).andExpect { status { isBadRequest() } }

        val wrongType = "products/$id/payload.png"
        images.store(wrongType, contentType = "application/zip")
        confirm(id, wrongType).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `refuses to issue a link for an unsupported content type`() {
        val id = createProduct()

        mockMvc
            .post("/api/v1/products/$id/image-upload-url") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"contentType":"application/pdf"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("content type application/pdf is not accepted; allowed: image/jpeg, image/png, image/webp") }
            }
    }

    @Test
    fun `refuses to issue a link for a product that does not exist`() {
        mockMvc
            .post("/api/v1/products/00000000-0000-0000-0000-000000000000/image-upload-url") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"contentType":"image/png"}"""
            }.andExpect { status { isNotFound() } }
    }

    private fun confirm(
        productId: String,
        key: String,
    ) = mockMvc.post("/api/v1/products/$productId/image") {
        contentType = MediaType.APPLICATION_JSON
        content = """{"key":"$key"}"""
    }

    private fun createProduct(): String =
        mockMvc
            .post("/api/v1/products") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"name":"Zephyr Compact Camera","description":"Point and shoot",
                     "category":"cameras","brand":"Zephyr","price":"199.00","stockQuantity":3}
                    """
            }.andReturn()
            .response
            .let { json.readTree(it.contentAsString)["id"].asString() }
}
