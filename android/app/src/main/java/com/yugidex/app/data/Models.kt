package com.yugidex.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

data class CardResponse(val data: List<Card>)
data class SetCard(@SerializedName("id") val id: Long)
data class Card(
    val id: Long,
    val name: String,
    val type: String? = null,
    val desc: String? = null,
    val atk: Int? = null,
    val def: Int? = null,
    val level: Int? = null,
    val attribute: String? = null,
    val race: String? = null,
    @SerializedName("card_images") val images: List<CardImage> = emptyList(),
    @SerializedName("card_sets") val sets: List<CardSet> = emptyList(),
    val localized: Localized? = null,
    val prices: PriceSummary? = null
)
data class CardImage(@SerializedName("image_url") val url: String, @SerializedName("image_url_small") val thumbnail: String? = null)
data class CardSet(@SerializedName("set_name") val name: String, @SerializedName("set_code") val code: String, @SerializedName("set_rarity") val rarity: String? = null)
data class Localized(val name: String?, val description: String?)
data class PriceSummary(val source: String, val currency: String, val min: Double, val max: Double, val editions: List<PriceEdition> = emptyList())
data class PriceEdition(val edition: String, val price: Double)

@Entity(tableName = "inventory_cards")
data class InventoryCard(
    @PrimaryKey val cardId: Long,
    val name: String,
    val imageUrl: String?,
    val type: String?,
    val attribute: String?,
    val rarity: String?,
    val collectionName: String? = null,
    val quantity: Int = 1,
    val savedAt: String = java.time.Instant.now().toString()
)

data class Credentials(val email: String, val password: String, val username: String? = null)
data class AuthResponse(val token: String?, val refreshToken: String? = null, val username: String, val requiresEmailConfirmation: Boolean = false, val restoredCards: Int = 0)
data class InventoryResponse(val cards: List<InventoryCard>)
data class SyncBody(val cards: List<InventoryCard>)
data class SyncResponse(val synced: Int, val username: String, val publicUrl: String)
