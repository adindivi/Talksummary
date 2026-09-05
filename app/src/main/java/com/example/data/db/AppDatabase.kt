package com.example.data.db

import android.content.Context
import androidx.room.*
import com.example.data.Message
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_days")
data class ChatDayEntity(
    @PrimaryKey val date: String,
    val rawMessagesJson: String,
    val summary: String,
    val rawKeywordsJson: String,
    val rawParticipantsJson: String,
    val msgCount: Int
)

@Entity(tableName = "user_settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface ChatDayDao {
    @Query("SELECT * FROM chat_days ORDER BY date DESC")
    fun getAllChatDaysFlow(): Flow<List<ChatDayEntity>>

    @Query("SELECT * FROM chat_days ORDER BY date DESC")
    suspend fun getAllChatDays(): List<ChatDayEntity>

    @Query("SELECT * FROM chat_days WHERE date = :date LIMIT 1")
    suspend fun getChatDayByDate(date: String): ChatDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatDay(chatDay: ChatDayEntity)

    @Query("UPDATE chat_days SET summary = :summary WHERE date = :date")
    suspend fun updateSummary(date: String, summary: String)

    @Query("DELETE FROM chat_days")
    suspend fun clearAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): SettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingEntity)

    @Query("DELETE FROM user_settings")
    suspend fun clearAll()
}

@Database(entities = [ChatDayEntity::class, SettingEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDayDao(): ChatDayDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "TalkSummaryDB"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Convert functions between Entities and Domain Models
object ChatDayMapper {
    private val moshi = Moshi.Builder().build()
    private val messageListAdapter = moshi.adapter<List<Message>>(
        Types.newParameterizedType(List::class.java, Message::class.java)
    )
    private val stringListAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java)
    )

    fun toEntity(
        date: String,
        messages: List<Message>,
        summary: String,
        keywords: List<String>,
        participants: List<String>,
        msgCount: Int
    ): ChatDayEntity {
        return ChatDayEntity(
            date = date,
            rawMessagesJson = messageListAdapter.toJson(messages),
            summary = summary,
            rawKeywordsJson = stringListAdapter.toJson(keywords),
            rawParticipantsJson = stringListAdapter.toJson(participants),
            msgCount = msgCount
        )
    }

    fun fromEntity(entity: ChatDayEntity): com.example.data.ChatDay {
        val messages = try {
            messageListAdapter.fromJson(entity.rawMessagesJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val keywords = try {
            stringListAdapter.fromJson(entity.rawKeywordsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val participants = try {
            stringListAdapter.fromJson(entity.rawParticipantsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        return com.example.data.ChatDay(
            date = entity.date,
            messages = messages,
            summary = entity.summary,
            keywords = keywords,
            participants = participants,
            msgCount = entity.msgCount
        )
    }
}
