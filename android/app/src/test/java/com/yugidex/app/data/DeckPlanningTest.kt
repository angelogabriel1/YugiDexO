package com.yugidex.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DeckPlanningTest {
    @Test
    fun reconcilesOwnedAndMissingCardsWithInventory() {
        val cards = listOf(
            DeckCardPayload(1, "Owned", null, null, null, null, DeckCardStatus.MISSING, 2),
            DeckCardPayload(2, "Missing", null, null, null, null, DeckCardStatus.OWNED, 1),
            DeckCardPayload(1, "Duplicate", null, null, null, null, DeckCardStatus.OWNED, 5)
        )

        val normalized = normalizeDeckCards(cards, setOf(1))

        assertEquals(2, normalized.size)
        assertEquals(DeckCardStatus.OWNED, normalized[0].status)
        assertEquals(2, normalized[0].quantity)
        assertEquals(DeckCardStatus.MISSING, normalized[1].status)
    }
}
