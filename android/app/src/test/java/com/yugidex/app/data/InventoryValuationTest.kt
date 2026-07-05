package com.yugidex.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryValuationTest {
    @Test
    fun multipliesUnitValueByQuantityAndReportsCoverage() {
        val cards = listOf(
            InventoryCard(1, "A", null, null, null, null, quantity = 2, estimatedUnitValue = 12.5),
            InventoryCard(2, "B", null, null, null, null, quantity = 3, estimatedUnitValue = null)
        )

        val valuation = inventoryValuation(cards)

        assertEquals(25.0, valuation.value, 0.001)
        assertEquals(2, valuation.pricedQuantity)
        assertEquals(5, valuation.totalQuantity)
    }
}
