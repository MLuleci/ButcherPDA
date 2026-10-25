package bg.butcher.pda.data

import bg.butcher.pda.model.BatchFilters
import bg.butcher.pda.model.BatchSort
import bg.butcher.pda.network.ErpError
import bg.butcher.pda.network.createErpService
import bg.butcher.pda.network.erpJson
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkErpRepositoryTest {
    private val server = MockWebServer()
    private val today = LocalDate(2026, 10, 25)
    private lateinit var repository: NetworkErpRepository

    @Before
    fun setUp() {
        server.start()
        repository = NetworkErpRepository(createErpService(server.url("/").toString(), OkHttpClient())) { today }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(MockResponse(code = code, body = body))
    }

    private inline fun <reified T : ErpError> Result<*>.assertError(): T {
        val error = exceptionOrNull()
        assertTrue("Expected ${T::class.simpleName}, got $error", error is T)
        return error as T
    }

    @Test
    fun searchBom_sendsFieldsAndFilters() = runTest {
        enqueue(
            """{"data":[{"name":"BOM-BEEF_MEATBALLS-001","item":"BEEF_MEATBALLS","item_name":"Meatballs (Beef)",
            "uom":"Nos","is_default":1,"shelf_life_in_days":7,"must_be_whole_number":1}]}"""
        )

        val result = repository.searchBom(" beef ").getOrThrow()

        assertEquals(1, result.size)
        assertEquals(7, result[0].shelfLife)
        assertTrue(result[0].isWholeNumber)

        val url = server.takeRequest().url
        assertEquals("/api/resource/BOM", url.encodedPath)
        assertEquals(
            """["name","item","item_name","uom","is_default",""" +
                """"item.shelf_life_in_days as shelf_life_in_days",""" +
                """"uom.must_be_whole_number as must_be_whole_number"]""",
            url.queryParameter("fields"),
        )
        assertEquals(
            """[["item_name","like","%beef%"],["is_active","=",1],["docstatus","=",1]]""",
            url.queryParameter("filters"),
        )
        assertEquals("item_name asc, name asc", url.queryParameter("order_by"))
        assertEquals(BOM_SEARCH_LIMIT.toString(), url.queryParameter("limit_page_length"))
    }

    @Test
    fun searchBom_encodesAndEscapesQuery() = runTest {
        enqueue("""{"data":[]}""")

        repository.searchBom("""50% "be_ef" \""")

        val url = server.takeRequest().url
        val filters = erpJson.parseToJsonElement(url.queryParameter("filters")!!).jsonArray
        assertEquals("""%50\% "be\_ef" \\%""", filters[0].jsonArray[2].jsonPrimitive.content)
        // The LIKE wildcards must be sent as %25, or the server decodes "%be" as a byte
        assertTrue(url.encodedQuery!!.contains("%2550"))
    }

    private val batchFields =
        """["name","item","item.item_name as item_name","item.default_bom as default_bom",""" +
            """"batch_qty","stock_uom","manufacturing_date","expiry_date","reference_name"]"""

    @Test
    fun searchBatches_sendsTheConfirmedRequest() = runTest {
        enqueue(
            """{"data":[{"name":"BEEF_MEATBALLS0013","item":"BEEF_MEATBALLS","item_name":"Meatballs (Beef)",
            "default_bom":"BOM-BEEF_MEATBALLS-001","batch_qty":25.0,"stock_uom":"Nos",
            "manufacturing_date":"2026-10-23","expiry_date":"2026-10-30","reference_name":"MAT-STE-2026-00016"}]}"""
        )
        val filters = BatchFilters(producedWithinDaysText = "30", hideExpired = true).withSort(BatchSort.EXPIRES)

        val result = repository.searchBatches(" beef ", filters, start = 30).getOrThrow()

        assertEquals("BOM-BEEF_MEATBALLS-001", result.single().defaultBom)
        val url = server.takeRequest().url
        assertEquals("/api/resource/Batch", url.encodedPath)
        assertEquals(batchFields, url.queryParameter("fields"))
        assertEquals(
            """[["item.default_bom","is","set"],["manufacturing_date",">=","2026-09-25"],["expiry_date",">=","2026-10-25"]]""",
            url.queryParameter("filters"),
        )
        assertEquals(
            """[["name","like","%beef%"],["item.item_name","like","%beef%"]]""",
            url.queryParameter("or_filters"),
        )
        assertEquals("expiry_date asc, name desc", url.queryParameter("order_by"))
        assertEquals("30", url.queryParameter("limit_start"))
        assertEquals(BATCH_PAGE_SIZE.toString(), url.queryParameter("limit_page_length"))
    }

    @Test
    fun searchBatches_withoutTextOrLimits() = runTest {
        enqueue("""{"data":[]}""")
        val filters = BatchFilters(producedWithinDaysText = "").withSort(BatchSort.PRODUCT)

        repository.searchBatches("  ", filters)

        val url = server.takeRequest().url
        assertEquals("""[["item.default_bom","is","set"]]""", url.queryParameter("filters"))
        assertNull(url.queryParameter("or_filters"))
        assertEquals("item_name asc, name desc", url.queryParameter("order_by"))
        assertEquals("0", url.queryParameter("limit_start"))
    }

    @Test
    fun getBatch_usesAOneRowListRequest() = runTest {
        enqueue(
            """{"data":[{"name":"BEEF_MEATBALLS0013","item":"BEEF_MEATBALLS","item_name":"Meatballs (Beef)",
            "default_bom":"BOM-BEEF_MEATBALLS-001","batch_qty":25.0,"stock_uom":"Nos",
            "manufacturing_date":"2026-10-23","expiry_date":"2026-10-30","reference_name":"MAT-STE-2026-00016"}]}"""
        )

        val result = repository.getBatch(" BEEF_MEATBALLS0013 ").getOrThrow()

        assertEquals("Nos", result.stockUom)
        assertEquals("BOM-BEEF_MEATBALLS-001", result.defaultBom)
        val url = server.takeRequest().url
        assertEquals("/api/resource/Batch", url.encodedPath)
        assertEquals(batchFields, url.queryParameter("fields"))
        assertEquals("""[["name","=","BEEF_MEATBALLS0013"]]""", url.queryParameter("filters"))
    }

    @Test
    fun getBatch_emptyListIsNotFound() = runTest {
        enqueue("""{"data":[]}""")

        val error = repository.getBatch("NOPE.0001").assertError<ErpError.Http>()

        assertTrue(error.isNotFound)
    }

    @Test
    fun getStockEntry_encodesTheDoctypeAndUnwrapsData() = runTest {
        enqueue(javaClass.classLoader!!.getResource("stock_entry.json")!!.readText())

        val result = repository.getStockEntry("MAT-STE-2026-00016").getOrThrow()

        assertEquals("BEEF.0001", result.ingredients.single().batchNumber)
        assertEquals("/api/resource/Stock%20Entry/MAT-STE-2026-00016", server.takeRequest().url.encodedPath)
    }

    @Test
    fun getBomDetails_sendsFormAndUnwrapsMessage() = runTest {
        enqueue(
            """{"message":{"name":"BOM-1","qty":100.0,"item_code":"FG","item_name":"Fg","uom":"Nos",
            "stock_uom":"Nos","shelf_life_in_days":7,"tolerance_percent":5.0,"items":[]}}"""
        )

        val result = repository.getBomDetails("BOM-1", 0.1 + 0.2, explode = true).getOrThrow()

        assertEquals("BOM-1", result.name)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/method/my_app.my_app.api.get_bom_details", request.url.encodedPath)
        assertEquals("bom=BOM-1&qty=0.3&explode=true", request.body!!.utf8())
    }

    @Test
    fun getBomDetails_validationError() = runTest {
        enqueue(
            """{"exception":"frappe.exceptions.ValidationError: Количеството трябва да е цяло число за мерна единица Nos","exc_type":"ValidationError","_server_messages":"[\"{\\\"message\\\":\\\"Количеството трябва да е цяло число за мерна единица Nos\\\",\\\"as_table\\\":false,\\\"title\\\":\\\"Message\\\",\\\"indicator\\\":\\\"red\\\",\\\"raise_exception\\\":1}\"]"}""",
            code = 417,
        )

        val error = repository.getBomDetails("BOM-1", 2.5, explode = false).assertError<ErpError.Http>()

        assertEquals(417, error.code)
        assertEquals("Количеството трябва да е цяло число за мерна единица Nos", error.message)
    }

    @Test
    fun createManufactureEntry_sendsJsonBody() = runTest {
        enqueue(
            """{"message":{"name":"MAT-STE-2026-00016","batch_no":"BEEF_MEATBALLS0013","item_code":"BEEF_MEATBALLS",
            "item_name":"Meatballs (Beef)","qty":25.0,"uom":"Nos","must_be_whole_number":true,
            "production_date":"2026-10-23","expiry_date":"2026-10-30","is_finished":true}}"""
        )

        val result = repository.createManufactureEntry(
            bom = "BOM-BEEF_MEATBALLS-001",
            quantity = 25.0,
            items = mapOf("BEEF" to mapOf("BEEF.0001" to 0.1 + 0.2, "BEEF.0002" to 1.0)),
            expiryDate = LocalDate(2026, 10, 30),
            explode = false,
            isFinished = true,
            requestId = "request-1",
        ).getOrThrow()

        assertEquals("MAT-STE-2026-00016", result.stockEntryId)
        val request = server.takeRequest()
        assertEquals("/api/method/my_app.my_app.api.create_manufacture_entry", request.url.encodedPath)
        assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
        assertEquals(
            erpJson.parseToJsonElement(
                """{"bom":"BOM-BEEF_MEATBALLS-001","qty":25.0,"items":{"BEEF":{"BEEF.0001":0.3,"BEEF.0002":1.0}},
                "expiry_date":"2026-10-30","explode":false,"is_finished":true,"request_id":"request-1"}"""
            ),
            erpJson.parseToJsonElement(request.body!!.utf8()),
        )
    }

    @Test
    fun createManufactureEntry_serverErrorIsUnknownOutcome() = runTest {
        enqueue("""{"exc_type":"QueryDeadlockError"}""", code = 500)

        val error = repository.createManufactureEntry(
            bom = "B", quantity = 1.0, items = emptyMap(), expiryDate = LocalDate(2026, 10, 30),
            explode = false, isFinished = true, requestId = "request-1",
        ).assertError<ErpError.Http>()

        assertTrue(error.isOutcomeUnknown)
        assertNull(error.message)
    }

    @Test
    fun unauthorized() = runTest {
        enqueue("""{"exception":"frappe.exceptions.AuthenticationError","exc_type":"AuthenticationError"}""", code = 401)

        repository.searchBom("beef").assertError<ErpError.Unauthorized>()
    }

    @Test
    fun forbidden() = runTest {
        enqueue(
            """{"exception":"frappe.exceptions.PermissionError: Insufficient Permission for Customer","exc_type":"PermissionError","_server_messages":"[\"{\\\"message\\\":\\\"Insufficient Permission for <strong>Customer</strong>\\\",\\\"title\\\":\\\"Permission Error\\\",\\\"indicator\\\":\\\"red\\\",\\\"raise_exception\\\":1}\"]"}""",
            code = 403,
        )

        val error = repository.getBatch("B1").assertError<ErpError.Forbidden>()

        assertEquals("Insufficient Permission for Customer", error.message)
    }

    @Test
    fun unexpectedResponse() = runTest {
        // What a list returns when the role can't read the fields: only `name`
        enqueue("""{"data":[{"name":"B1"}]}""")

        repository.getBatch("B1").assertError<ErpError.UnexpectedResponse>()
    }

    @Test
    fun networkFailure() = runTest {
        server.close()

        val error = repository.searchBom("beef").assertError<ErpError.Network>()

        assertTrue(error.isOutcomeUnknown)
    }

    @Test
    fun serverMessage_joinsMessagesAndStripsHtml() {
        val body = """{"_server_messages":"[\"{\\\"message\\\":\\\"First\\\"}\",\"{\\\"message\\\":\\\"<b>Second</b> &amp; more\\\"}\"]"}"""

        assertEquals("First\nSecond & more", ErpError.serverMessage(body))
        assertNull(ErpError.serverMessage("not json"))
        assertNull(ErpError.serverMessage("""{"exc_type":"ValidationError"}"""))
    }
}
