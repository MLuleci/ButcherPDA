package bg.butcher.pda.ui.screens

import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.model.BomDetailsItem
import bg.butcher.pda.model.BomSearchResultItem
import bg.butcher.pda.network.NetworkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormRulesTest {
    private fun ingredient(code: String, quantity: Double, wholeNumber: Boolean = false) = BomDetailsItem(
        quantity = quantity,
        itemCode = code,
        itemName = code,
        uom = if (wholeNumber) "Nos" else "Kg",
        stockUom = if (wholeNumber) "Nos" else "Kg",
        conversionFactor = 1.0,
        wholeNumber = wholeNumber,
    )

    private fun bom(vararg items: BomDetailsItem, tolerancePercent: Double = 5.0) = BomDetails(
        name = "BOM-1",
        quantity = 1.0,
        itemCode = "FG",
        itemName = "Fg",
        uom = "Kg",
        stockUom = "Kg",
        shelfLife = 5,
        items = items.toList(),
        tolerancePercent = tolerancePercent,
    )

    private fun state(bom: BomDetails, items: BatchMap = emptyMap(), expiry: String = "5") = FormUiState(
        fetchState = NetworkState.Success(bom),
        items = items,
        expiryDaysText = expiry,
    )

    private fun selected(wholeNumber: Boolean) = BomSearchResultItem(
        name = "BOM-1",
        item = "FG",
        itemName = "Fg",
        uom = if (wholeNumber) "Nos" else "Kg",
        isDefault = 1,
        wholeNumber = if (wholeNumber) 1 else 0,
    )

    @Test
    fun canContinue_needsBomQuantityAndExpiry() {
        val ready = FormUiState(selectedItem = selected(false), quantityText = "2.5", expiryDaysText = "5")

        assertTrue(ready.canContinue)
        assertFalse(ready.copy(selectedItem = null).canContinue)
        assertFalse(ready.copy(quantityText = "").canContinue)
        assertFalse(ready.copy(quantityText = "0").canContinue)
        assertFalse(ready.copy(expiryDaysText = "").canContinue)
        assertFalse(ready.copy(expiryDaysText = "1.5").canContinue)
        assertFalse(ready.copy(expiryDaysText = "0").canContinue)
    }

    @Test
    fun canContinue_wholeNumberBomNeedsWholeQuantity() {
        val ready = FormUiState(selectedItem = selected(true), quantityText = "25", expiryDaysText = "5")

        assertTrue(ready.canContinue)
        assertTrue(ready.copy(quantityText = "25.0").canContinue)
        assertFalse(ready.copy(quantityText = "2.5").canContinue)
    }

    @Test
    fun allowedRange_usesTolerancePercent() {
        val beef = ingredient("BEEF", 5.0)
        val s = state(bom(beef))

        assertEquals(4.75, s.allowedRange(beef).start, 1e-9)
        assertEquals(5.25, s.allowedRange(beef).endInclusive, 1e-9)
        assertEquals(Coverage.OK, s.copy(items = mapOf("BEEF" to mapOf("B1" to 4.75))).coverage(beef))
        assertEquals(Coverage.UNDER, s.copy(items = mapOf("BEEF" to mapOf("B1" to 4.74))).coverage(beef))
    }

    @Test
    fun allowedRange_usesServerTolerance() {
        val beef = ingredient("BEEF", 10.0)
        val s = state(bom(beef, tolerancePercent = 3.0))

        assertEquals(9.7, s.allowedRange(beef).start, 1e-9)
        assertEquals(10.3, s.allowedRange(beef).endInclusive, 1e-9)
    }

    @Test
    fun coverage_states() {
        val beef = ingredient("BEEF", 5.0)
        val s = state(bom(beef))

        assertEquals(Coverage.MISSING, s.coverage(beef))
        assertEquals(Coverage.UNDER, s.copy(items = mapOf("BEEF" to mapOf("B1" to 4.6))).coverage(beef))
        assertEquals(Coverage.OK, s.copy(items = mapOf("BEEF" to mapOf("B1" to 3.0, "B2" to 1.9))).coverage(beef))
        assertEquals(Coverage.OVER, s.copy(items = mapOf("BEEF" to mapOf("B1" to 5.4))).coverage(beef))
    }

    @Test
    fun coverage_epsilonAbsorbsRoundingInSums() {
        val beef = ingredient("BEEF", 5.0)
        val s = state(bom(beef))
        // Sums to 4.7499999, a fraction of a gram under the 4.75 minimum
        val justUnder = mapOf("B1" to 1.58, "B2" to 1.58, "B3" to 1.5899999)

        assertEquals(Coverage.OK, s.copy(items = mapOf("BEEF" to justUnder)).coverage(beef))
        assertEquals(Coverage.UNDER, s.copy(items = mapOf("BEEF" to mapOf("B1" to 4.749))).coverage(beef))
    }

    @Test
    fun wholeNumber_usesHalfUnitMargin() {
        val onion = ingredient("ONION", 2.4, wholeNumber = true)
        val s = state(bom(onion))

        assertEquals(2L..2L, s.wholeNumberChoices(onion))
        assertEquals(Coverage.OK, s.copy(items = mapOf("ONION" to mapOf("O1" to 2.0))).coverage(onion))
        assertEquals(Coverage.OVER, s.copy(items = mapOf("ONION" to mapOf("O1" to 3.0))).coverage(onion))
    }

    @Test
    fun wholeNumber_halfwayAllowsBothNeighbours() {
        val onion = ingredient("ONION", 2.5, wholeNumber = true)
        val s = state(bom(onion))

        assertEquals(2L..3L, s.wholeNumberChoices(onion))
        assertEquals(Coverage.OK, s.copy(items = mapOf("ONION" to mapOf("O1" to 2.0))).coverage(onion))
        assertEquals(Coverage.OK, s.copy(items = mapOf("ONION" to mapOf("O1" to 3.0))).coverage(onion))
    }

    @Test
    fun wholeNumber_tooSmallRunIsUnworkable() {
        val onion = ingredient("ONION", 0.12, wholeNumber = true)
        val beef = ingredient("BEEF", 0.04)
        val s = state(bom(beef, onion))

        assertTrue(s.wholeNumberChoices(onion).isEmpty())
        assertEquals(listOf(onion), s.unworkableIngredients)
    }

    @Test
    fun wholeNumber_choicesStartAtOne() {
        val onion = ingredient("ONION", 0.6, wholeNumber = true)
        val s = state(bom(onion))

        assertEquals(1L..1L, s.wholeNumberChoices(onion))
        assertTrue(s.unworkableIngredients.isEmpty())
    }

    @Test
    fun canSubmit_needsEveryIngredientInRangeAndExpiry() {
        val beef = ingredient("BEEF", 5.0)
        val onion = ingredient("ONION", 2.0, wholeNumber = true)
        val items = mapOf("BEEF" to mapOf("B1" to 5.0), "ONION" to mapOf("O1" to 2.0))
        val ready = state(bom(beef, onion), items)

        assertTrue(ready.canSubmit)
        assertFalse(ready.copy(items = items - "ONION").canSubmit)
        assertFalse(ready.copy(expiryDaysText = "").canSubmit)
        assertFalse(ready.copy(submitState = NetworkState.Loading).canSubmit)
        assertFalse(ready.copy(fetchState = NetworkState.Idle).canSubmit)
    }

    @Test
    fun ingredientLines_combineRulesForTheTable() {
        val beef = ingredient("BEEF", 5.0)
        val onion = ingredient("ONION", 2.5, wholeNumber = true)
        val s = state(bom(beef, onion), mapOf("BEEF" to mapOf("B1" to 3.0, "B2" to 1.5)))

        val lines = s.ingredientLines(s.bom!!)

        assertEquals(listOf("BEEF", "ONION"), lines.map { it.itemCode })
        assertEquals(4.5, lines[0].chosen, 1e-9)
        assertEquals(Coverage.UNDER, lines[0].coverage)
        assertEquals(null, lines[0].wholeNumberChoices)
        assertEquals(Coverage.MISSING, lines[1].coverage)
        assertEquals(2L..3L, lines[1].wholeNumberChoices)
    }
}
