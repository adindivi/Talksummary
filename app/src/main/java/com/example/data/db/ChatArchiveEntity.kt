package com.example.data.db

import androidx.room.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_archives")
data class ChatArchiveEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val roomTitle: String,
    val importedAt: Long,
    val lastOpenedAt: Long,
    val startDate: String,
    val endDate: String,
    val totalDays: Int,
    val totalMessages: Int,
    val topParticipantsJson: String,
    val internalFilePath: String,
    val isFavorite: Boolean = false
)

@Dao
interface ChatArchiveDao {
    @Query("SELECT * FROM chat_archives ORDER BY isFavorite DESC, lastOpenedAt DESC")
    fun getAllArchivesFlow(): Flow<List<ChatArchiveEntity>>

    @Query("SELECT * FROM chat_archives ORDER BY isFavorite DESC, lastOpenedAt DESC")
    suspend fun getAllArchives(): List<ChatArchiveEntity>

    @Query("SELECT * FROM chat_archives WHERE id = :id LIMIT 1")
    suspend fun getArchiveById(id: String): ChatArchiveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArchive(archive: ChatArchiveEntity)

    @Query("DELETE FROM chat_archives WHERE id = :id")
    suspend fun deleteArchiveById(id: String)

    @Query("UPDATE chat_archives SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: String, isFavorite: Boolean)

    @Query("UPDATE chat_archives SET lastOpenedAt = :lastOpenedAt WHERE id = :id")
    suspend fun updateLastOpened(id: String, lastOpenedAt: Long)

    @Query("UPDATE chat_archives SET roomTitle = :newTitle WHERE id = :id")
    suspend fun updateRoomTitle(id: String, newTitle: String)

    @Query("DELETE FROM chat_archives")
    suspend fun clearAll()
}

object ChatArchiveMapper {
    private val moshi = Moshi.Builder().build()
    private val listAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    fun participantsToJson(participants: List<String>): String {
        return try {
            listAdapter.toJson(participants)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun participantsFromJson(json: String): List<String> {
        return try {
            listAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
