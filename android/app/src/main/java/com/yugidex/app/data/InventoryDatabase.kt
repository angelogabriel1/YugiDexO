package com.yugidex.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_cards ORDER BY savedAt DESC") fun observeByDate(): Flow<List<InventoryCard>>
    @Query("SELECT * FROM inventory_cards ORDER BY name COLLATE NOCASE") fun observeByName(): Flow<List<InventoryCard>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(card: InventoryCard)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAll(cards: List<InventoryCard>)
    @Delete suspend fun delete(card: InventoryCard)
}

@Dao
interface DeckDao {
    @Transaction
    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DeckWithCards>>

    @Transaction
    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DeckWithCards>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDeck(deck: DeckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCards(cards: List<DeckCardEntity>)

    @Query("DELETE FROM deck_cards WHERE deckId = :deckId")
    suspend fun deleteCards(deckId: String)

    @Query("DELETE FROM decks WHERE id = :deckId")
    suspend fun deleteDeck(deckId: String)

    @Transaction
    suspend fun save(deck: DeckEntity, cards: List<DeckCardEntity>) {
        saveDeck(deck)
        deleteCards(deck.id)
        if (cards.isNotEmpty()) saveCards(cards)
    }
}

@Database(entities = [InventoryCard::class, DeckEntity::class, DeckCardEntity::class], version = 6, exportSchema = false)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventory(): InventoryDao
    abstract fun decks(): DeckDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_cards ADD COLUMN collectionName TEXT")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_cards ADD COLUMN estimatedUnitValue REAL")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS decks (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt TEXT NOT NULL,
                        updatedAt TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS deck_cards (
                        deckId TEXT NOT NULL,
                        cardId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        imageUrl TEXT,
                        type TEXT,
                        attribute TEXT,
                        rarity TEXT,
                        status TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        PRIMARY KEY(deckId, cardId),
                        FOREIGN KEY(deckId) REFERENCES decks(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_deck_cards_deckId ON deck_cards(deckId)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE deck_cards ADD COLUMN affiliateUrl TEXT")
                db.execSQL("ALTER TABLE deck_cards ADD COLUMN affiliateLabel TEXT")
                db.execSQL("ALTER TABLE deck_cards ADD COLUMN affiliateProvider TEXT")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Invalida as medias antigas para recalcular pela menor cotacao por edicao.
                db.execSQL("UPDATE inventory_cards SET estimatedUnitValue = NULL")
            }
        }

        fun create(context: Context) = Room.databaseBuilder(context, InventoryDatabase::class.java, "ygo_inventory.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
    }
}
