package com.yugidex.app.data

import com.yugidex.app.scanner.CardDetectionResult
import com.yugidex.app.scanner.CardDetectionType

class CardRepository(private val ygo: YgoApi, private val backend: YugidexApi, private val dao: InventoryDao) {
    suspend fun identify(detection: CardDetectionResult): Card {
        val failures = mutableListOf<Throwable>()

        if (detection.type == CardDetectionType.SET_CODE) {
            runCatching {
                val setCard = ygo.bySetCode(detection.value)
                ygo.byId(setCard.id).data.first()
            }.onSuccess { return it }.onFailure(failures::add)
        }

        val passcode = when (detection.type) {
            CardDetectionType.PASSCODE -> detection.value
            else -> detection.passcodeCandidate
        }
        if (passcode != null) {
            runCatching { ygo.byId(passcode.toLong()).data.first() }
                .onSuccess { return it }
                .onFailure(failures::add)
        }

        val name = when (detection.type) {
            CardDetectionType.NAME -> detection.value
            else -> detection.nameCandidate
        }
        if (!name.isNullOrBlank()) {
            runCatching { ygo.byName(name).data.first() }
                .recoverCatching { ygo.fuzzy(name).data.first() }
                .onSuccess { return it }
                .onFailure(failures::add)
        }

        throw failures.lastOrNull() ?: NoSuchElementException("Nenhuma carta reconhecida")
    }

    suspend fun details(card: Card): Card = runCatching { backend.details(card.id, card.name) }.getOrDefault(card)

    suspend fun save(card: Card, collectionName: String?) = dao.save(InventoryCard(
        cardId = card.id, name = card.localized?.name ?: card.name, imageUrl = card.images.firstOrNull()?.url,
        type = card.type, attribute = card.attribute,
        rarity = card.sets.firstOrNull { it.name == collectionName || it.code == collectionName }?.rarity
            ?: card.sets.firstOrNull()?.rarity,
        collectionName = collectionName
    ))
}
