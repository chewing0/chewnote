package com.example.myapplication.agent.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.example.myapplication.agent.model.LedgerEntry
import com.example.myapplication.agent.model.ScheduleItem
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries")
    suspend fun getAll(): List<LedgerEntry>

    @Upsert
    suspend fun upsert(entry: LedgerEntry)

    @Upsert
    suspend fun upsertAll(entries: List<LedgerEntry>)

    @Query("DELETE FROM ledger_entries WHERE id = :entryId")
    suspend fun deleteById(entryId: String)

    @Query("DELETE FROM ledger_entries WHERE actionBatchId = :batchId")
    suspend fun deleteByActionBatchId(batchId: String)

    @Query("DELETE FROM ledger_entries")
    suspend fun deleteAll()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScheduleItem>>

    @Upsert
    suspend fun upsert(entry: ScheduleItem)

    @Upsert
    suspend fun upsertAll(entries: List<ScheduleItem>)

    @Query("DELETE FROM schedule_items WHERE id = :itemId")
    suspend fun deleteById(itemId: String)

    @Query("DELETE FROM schedule_items WHERE actionBatchId = :batchId")
    suspend fun deleteByActionBatchId(batchId: String)

    @Query("DELETE FROM schedule_items")
    suspend fun deleteAll()
}

@Database(
    entities = [LedgerEntry::class, ScheduleItem::class],
    version = 1,
    exportSchema = false,
)
abstract class AgentDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var instance: AgentDatabase? = null

        fun getInstance(context: Context): AgentDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgentDatabase::class.java,
                    "agent_local.db",
                ).build().also { instance = it }
            }
        }
    }
}
