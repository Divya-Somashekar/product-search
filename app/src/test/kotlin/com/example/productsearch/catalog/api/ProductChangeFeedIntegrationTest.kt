package com.example.productsearch.catalog.api

import com.example.productsearch.IntegrationTest
import com.example.productsearch.catalog.application.ProductService
import com.example.productsearch.catalog.domain.OutboxEntry
import com.example.productsearch.catalog.domain.OutboxRepository
import com.example.productsearch.catalog.domain.ProductCommand
import com.example.productsearch.catalog.domain.ProductRepository
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The catalog's change log: what a separate search service would consume to stay in step.
 *
 * Entries are re-stamped as old wherever the hold-back would otherwise hide them, so these tests
 * assert the real query instead of waiting on a clock.
 */
@IntegrationTest
class ProductChangeFeedIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val products: ProductService,
    @Autowired private val repository: ProductRepository,
    @Autowired private val outbox: OutboxRepository,
    @Autowired private val transactions: TransactionTemplate,
) {
    @BeforeEach
    fun clean() {
        outbox.deleteAll()
        repository.deleteAll()
        outbox.deleteAll()
    }

    /** Re-stamps everything written so far as settled, in the order it was written. */
    private fun settleAll() {
        val settled = outbox.findAll().sortedBy { it.seq }.map { OutboxEntry(it.productId, it.deleted, LONG_AGO) }
        outbox.deleteAll()
        outbox.saveAll(settled)
    }

    private fun feed(after: Long = 0): String =
        mockMvc
            .get("/internal/changes") { param("after", after.toString()) }
            .andExpect { status { isOk() } }
            .andReturn()
            .response
            .contentAsString

    private fun command(name: String) = ProductCommand(name, "", "misc", "Acme", BigDecimal("10.00"), 1)

    @Test
    fun `a write and its change log entry commit together, or not at all`() {
        val id =
            transactions.execute {
                val created = products.create(command("Rolled Back Widget"))
                it.setRollbackOnly()
                created.id
            }

        assertEquals(0, outbox.count(), "a rolled-back write must not leave a change log entry")
        assertTrue(repository.findById(id).isEmpty, "and must not leave a product either")
    }

    @Test
    fun `an entry resolves to the product's current state, so repeated edits collapse`() {
        val created = products.create(command("Collapsing Widget v1"))
        products.update(created.id, command("Collapsing Widget v2"))
        products.update(created.id, command("Collapsing Widget v3"))
        settleAll()

        val names: List<String> = JsonPath.read(feed(), "$[*].product.name")
        assertEquals(3, names.size, "every entry is still reported; the log is not compacted")
        assertTrue(names.all { it == "Collapsing Widget v3" }, "each resolves to the current state, got $names")
    }

    @Test
    fun `a deleted product resolves to a null product, which means remove it`() {
        val created = products.create(command("Doomed Widget"))
        products.delete(created.id)
        settleAll()

        val body = feed()
        val ids: List<String> = JsonPath.read(body, "$[*].productId")
        val resolved: List<Any?> = JsonPath.read(body, "$[*].product")
        assertEquals(2, ids.size, "the create and the delete are both reported")
        assertTrue(resolved.all { it == null }, "a product that is gone resolves to null, got $resolved")
    }

    @Test
    fun `entries too recent to have settled are held back`() {
        val product = products.create(command("Fresh Widget"))
        // The entry ProductService just wrote carries `now`, so it is inside the hold-back window.
        assertEquals("[]", feed().trim(), "a just-written entry is not served yet")

        settleAll()
        val ids: List<String> = JsonPath.read(feed(), "$[*].productId")
        assertEquals(listOf(product.id.toString()), ids, "once settled, the same product is served")
    }

    @Test
    fun `the cursor is exclusive and entries arrive in seq order`() {
        val first = products.create(command("First Widget"))
        val second = products.create(command("Second Widget"))
        settleAll()

        val body = feed()
        val seqs: List<Int> = JsonPath.read(body, "$[*].seq")
        val ids: List<String> = JsonPath.read(body, "$[*].productId")
        assertEquals(seqs.sorted(), seqs, "oldest first")
        assertEquals(listOf(first.id.toString(), second.id.toString()), ids, "in the order they were written")

        val afterFirst: List<Int> = JsonPath.read(feed(after = seqs.first().toLong()), "$[*].seq")
        assertEquals(listOf(seqs.last()), afterFirst, "the cursor row is not served again")
        assertEquals("[]", feed(after = seqs.last().toLong()).trim(), "an up-to-date consumer gets nothing")
    }

    @Test
    fun `the feed validates its parameters`() {
        mockMvc.get("/internal/changes") { param("after", "-1") }.andExpect { status { isBadRequest() } }
        mockMvc.get("/internal/changes") { param("size", "1001") }.andExpect { status { isBadRequest() } }
    }

    private companion object {
        val LONG_AGO: Instant = Instant.now().minus(1, ChronoUnit.HOURS)
    }
}
