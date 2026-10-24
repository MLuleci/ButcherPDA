package bg.butcher.pda.model

import bg.butcher.pda.network.erpJson
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Responses captured from the development server
class DecodingTest {
    @Test
    fun bomSearch() {
        val json = """
            {"data":[{"name":"BOM-BEEF_MEATBALLS-001","item":"BEEF_MEATBALLS","item_name":"Meatballs (Beef)",
            "uom":"Nos","is_default":1,"shelf_life_in_days":7,"must_be_whole_number":1}]}
        """
        val result = erpJson.decodeFromString<DataWrapper<List<BomSearchResultItem>>>(json).data

        assertEquals(
            listOf(
                BomSearchResultItem(
                    name = "BOM-BEEF_MEATBALLS-001",
                    item = "BEEF_MEATBALLS",
                    itemName = "Meatballs (Beef)",
                    uom = "Nos",
                    isDefault = 1,
                    shelfLife = 7,
                    wholeNumber = 1,
                )
            ),
            result,
        )
        assertTrue(result[0].isDefaultBom)
        assertTrue(result[0].isWholeNumber)
    }

    @Test
    fun bomSearch_withoutLinkFields() {
        val json = """{"data":[{"name":"B","item":"I","item_name":"N","uom":"Kg","is_default":0}]}"""
        val result = erpJson.decodeFromString<DataWrapper<List<BomSearchResultItem>>>(json).data[0]

        assertNull(result.shelfLife)
        assertFalse(result.isDefaultBom)
        assertFalse(result.isWholeNumber)
    }

    @Test
    fun bomDetails() {
        val json = """
            {"message":{"name":"BOM-BEEF_MEATBALLS-001","qty":100.0,"item_code":"BEEF_MEATBALLS",
            "item_name":"Meatballs (Beef)","uom":"Nos","must_be_whole_number":true,"stock_uom":"Nos",
            "shelf_life_in_days":7,"tolerance_percent":5.0,"items":[
            {"item_code":"BEEF","item_name":"Beef","qty":4.0,"uom":"Kg","must_be_whole_number":false,"stock_uom":"Kg","conversion_factor":1.0},
            {"item_code":"SPICE","item_name":"Spice","qty":0.8,"uom":"Kg","must_be_whole_number":false,"stock_uom":"Kg","conversion_factor":1.0},
            {"item_code":"ONION","item_name":"Onion","qty":12.0,"uom":"Nos","must_be_whole_number":true,"stock_uom":"Nos","conversion_factor":1.0}]}}
        """
        val result = erpJson.decodeFromString<MessageWrapper<BomDetails>>(json).message

        assertEquals("BOM-BEEF_MEATBALLS-001", result.name)
        assertEquals(100.0, result.quantity, 0.0)
        assertEquals(7, result.shelfLife)
        assertEquals(5.0, result.tolerancePercent, 0.0)
        assertTrue(result.wholeNumber)
        assertEquals(listOf("BEEF", "SPICE", "ONION"), result.items.map { it.itemCode })
        assertEquals(0.8, result.items[1].quantity, 0.0)
        assertFalse(result.items[0].wholeNumber)
        assertTrue(result.items[2].wholeNumber)
    }

    @Test
    fun bomDetails_withoutTolerance() {
        val json = """
            {"message":{"name":"B","qty":1,"item_code":"I","item_name":"N","uom":"Kg","stock_uom":"Kg",
            "shelf_life_in_days":0,"items":[]}}
        """
        val result = erpJson.decodeFromString<MessageWrapper<BomDetails>>(json).message

        assertEquals(DEFAULT_TOLERANCE_PERCENT, result.tolerancePercent, 0.0)
        assertFalse(result.wholeNumber)
    }

    @Test
    fun manufactureEntry() {
        val json = """
            {"message":{"name":"MAT-STE-2026-00016","batch_no":"BEEF_MEATBALLS0013","item_code":"BEEF_MEATBALLS",
            "item_name":"Meatballs (Beef)","qty":25.0,"uom":"Nos","must_be_whole_number":true,
            "production_date":"2026-10-23","expiry_date":"2026-10-30","is_finished":true}}
        """
        val result = erpJson.decodeFromString<MessageWrapper<ManufactureEntry>>(json).message

        assertEquals(
            ManufactureEntry(
                stockEntryId = "MAT-STE-2026-00016",
                batchNumber = "BEEF_MEATBALLS0013",
                itemCode = "BEEF_MEATBALLS",
                itemName = "Meatballs (Beef)",
                quantity = 25.0,
                uom = "Nos",
                wholeNumber = true,
                productionDate = LocalDate(2026, 10, 23),
                expiryDate = LocalDate(2026, 10, 30),
                isFinished = true,
            ),
            result,
        )
    }

    @Test
    fun batch_fullDocument() {
        val json = """
            {"data":{"name":"BEEF_MEATBALLS0001","owner":"Administrator","creation":"2026-10-23 15:58:47.492924",
            "modified":"2026-10-23 15:58:47.851556","modified_by":"Administrator","docstatus":0,"idx":0,
            "batch_id":"BEEF_MEATBALLS0001","item":"BEEF_MEATBALLS","item_name":"Meatballs (Beef)",
            "manufacturing_date":"2026-10-23","batch_qty":25.0,"stock_uom":"Nos","expiry_date":"2026-10-30",
            "use_batchwise_valuation":1,"allow_negative_stock_for_batch":0,"disabled":0,
            "reference_doctype":"Stock Entry","reference_name":"MAT-STE-2026-00004","qty_to_produce":0.0,
            "produced_qty":0.0,"doctype":"Batch"}}
        """
        val result = erpJson.decodeFromString<DataWrapper<BatchDetails>>(json).data

        assertEquals(
            BatchDetails(
                name = "BEEF_MEATBALLS0001",
                item = "BEEF_MEATBALLS",
                itemName = "Meatballs (Beef)",
                stockUom = "Nos",
                manufacturingDate = LocalDate(2026, 10, 23),
                expiryDate = LocalDate(2026, 10, 30),
                batchQuantity = 25.0,
                referenceDoctype = "Stock Entry",
                referenceName = "MAT-STE-2026-00004",
            ),
            result,
        )
    }

    @Test
    fun batch_withoutOptionalFields() {
        val json = """{"data":{"name":"B1","item":"BEEF","item_name":"Beef","expiry_date":null}}"""
        val result = erpJson.decodeFromString<DataWrapper<BatchDetails>>(json).data

        assertEquals(BatchDetails(name = "B1", item = "BEEF", itemName = "Beef"), result)
    }
}
