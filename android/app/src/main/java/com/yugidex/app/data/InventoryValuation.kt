package com.yugidex.app.data

data class InventoryValuation(
    val value: Double,
    val pricedQuantity: Int,
    val totalQuantity: Int
)

fun inventoryValuation(cards: List<InventoryCard>): InventoryValuation {
    var value = 0.0
    var pricedQuantity = 0
    var totalQuantity = 0

    cards.forEach { card ->
        val quantity = card.quantity.coerceAtLeast(0)
        totalQuantity += quantity
        card.estimatedUnitValue?.takeIf { it.isFinite() && it >= 0 }?.let { unitValue ->
            value += unitValue * quantity
            pricedQuantity += quantity
        }
    }

    return InventoryValuation(value, pricedQuantity, totalQuantity)
}
