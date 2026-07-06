package com.yugidex.app.data

object DeckCardStatus {
    const val OWNED = "owned"
    const val MISSING = "missing"
}

fun normalizeDeckCards(
    cards: List<DeckCardPayload>,
    ownedCardIds: Set<Long>
): List<DeckCardPayload> = cards
    .distinctBy { it.cardId }
    .map { card ->
        card.copy(
            status = if (card.cardId in ownedCardIds) DeckCardStatus.OWNED else DeckCardStatus.MISSING,
            quantity = card.quantity.coerceIn(1, 999)
        )
    }
