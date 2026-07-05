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

@Database(entities = [InventoryCard::class], version = 2, exportSchema = false)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun inventory(): InventoryDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_cards ADD COLUMN collectionName TEXT")
            }
        }

        fun create(context: Context) = Room.databaseBuilder(context, InventoryDatabase::class.java, "ygo_inventory.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }
}
