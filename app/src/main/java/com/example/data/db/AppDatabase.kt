package com.example.data.db

import android.content.Context
import androidx.room.*
import com.example.data.Message
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "chat_days",
    primaryKeys = ["archiveId", "date"],
    indices = [Index(value = ["archiveId"])]
)
data class ChatDayEntity(
    val archiveId: String = "default",
    val date: String,
    val rawMessagesJson: String,
    val summary: String,
    val rawKeywordsJson: String,
    val rawParticipantsJson: String,
    val msgCount: Int
)

/**
 * Lightweight projection for main list and timeline views.
 * Completely excludes rawMessagesJson to eliminate SQLite 2MB CursorWindow limits.
 */
data class ChatDaySummaryProjection(
    val archiveId: String,
    val date: String,
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
    @Query("SELECT archiveId, date, summary, rawKeywordsJson, rawParticipantsJson, msgCount FROM chat_days WHERE archiveId = :archiveId ORDER BY date DESC")
    fun getSummaryChatDaysFlow(archiveId: String): Flow<List<ChatDaySummaryProjection>>

    @Query("SELECT archiveId, date, summary, rawKeywordsJson, rawParticipantsJson, msgCount FROM chat_days ORDER BY date DESC")
    fun getAllSummaryChatDaysFlow(): Flow<List<ChatDaySummaryProjection>>

    @Query("SELECT * FROM chat_days ORDER BY date DESC")
    fun getAllChatDaysFlow(): Flow<List<ChatDayEntity>>

    @Query("SELECT * FROM chat_days WHERE archiveId = :archiveId ORDER BY date DESC")
    fun getAllChatDaysFlow(archiveId: String): Flow<List<ChatDayEntity>>

    @Query("SELECT * FROM chat_days WHERE archiveId = :archiveId ORDER BY date DESC")
    suspend fun getAllChatDays(archiveId: String): List<ChatDayEntity>

    @Query("SELECT * FROM chat_days ORDER BY date DESC")
    suspend fun getAllChatDays(): List<ChatDayEntity>

    @Query("SELECT * FROM chat_days WHERE archiveId = :archiveId AND date = :date LIMIT 1")
    suspend fun getChatDayByDate(archiveId: String, date: String): ChatDayEntity?

    @Query("SELECT * FROM chat_days WHERE date = :date LIMIT 1")
    suspend fun getChatDayByDate(date: String): ChatDayEntity?

    @Query("SELECT * FROM chat_days WHERE archiveId = :archiveId AND date LIKE :monthPrefix || '%' ORDER BY date ASC")
    suspend fun getChatDaysForMonth(archiveId: String, monthPrefix: String): List<ChatDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatDay(chatDay: ChatDayEntity)

    @Query("UPDATE chat_days SET summary = :summary WHERE archiveId = :archiveId AND date = :date")
    suspend fun updateSummary(archiveId: String, date: String, summary: String)

    @Query("UPDATE chat_days SET summary = :summary WHERE date = :date")
    suspend fun updateSummary(date: String, summary: String)

    @Query("DELETE FROM chat_days WHERE archiveId = :archiveId")
    suspend fun deleteChatDaysByArchive(archiveId: String)

    @Query("SELECT COUNT(*) FROM chat_days WHERE archiveId = :archiveId")
    suspend fun getChatDaysCountByArchive(archiveId: String): Int

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

@Database(entities = [ChatDayEntity::class, SettingEntity::class, ChatArchiveEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDayDao(): ChatDayDao
    abstract fun settingsDao(): SettingsDao
    abstract fun chatArchiveDao(): ChatArchiveDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_archives` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `fileName` TEXT NOT NULL,
                        `roomTitle` TEXT NOT NULL,
                        `importedAt` INTEGER NOT NULL,
                        `lastOpenedAt` INTEGER NOT NULL,
                        `startDate` TEXT NOT NULL,
                        `endDate` TEXT NOT NULL,
                        `totalDays` INTEGER NOT NULL,
                        `totalMessages` INTEGER NOT NULL,
                        `topParticipantsJson` TEXT NOT NULL,
                        `internalFilePath` TEXT NOT NULL,
                        `isFavorite` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `chat_days_new` (
                        `archiveId` TEXT NOT NULL DEFAULT 'default',
                        `date` TEXT NOT NULL,
                        `rawMessagesJson` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `rawKeywordsJson` TEXT NOT NULL,
                        `rawParticipantsJson` TEXT NOT NULL,
                        `msgCount` INTEGER NOT NULL,
                        PRIMARY KEY(`archiveId`, `date`)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `chat_days_new` (`archiveId`, `date`, `rawMessagesJson`, `summary`, `rawKeywordsJson`, `rawParticipantsJson`, `msgCount`)
                    SELECT 'default', `date`, `rawMessagesJson`, `summary`, `rawKeywordsJson`, `rawParticipantsJson`, `msgCount` FROM `chat_days`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `chat_days`")
                db.execSQL("ALTER TABLE `chat_days_new` RENAME TO `chat_days`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_days_archiveId` ON `chat_days` (`archiveId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "TalkSummaryDB"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
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
        return toEntity("default", date, messages, summary, keywords, participants, msgCount)
    }

    fun toEntity(
        archiveId: String,
        date: String,
        messages: List<Message>,
        summary: String,
        keywords: List<String>,
        participants: List<String>,
        msgCount: Int
    ): ChatDayEntity {
        return ChatDayEntity(
            archiveId = archiveId,
            date = date,
            rawMessagesJson = messageListAdapter.toJson(messages),
            summary = summary,
            rawKeywordsJson = stringListAdapter.toJson(keywords),
            rawParticipantsJson = stringListAdapter.toJson(participants),
            msgCount = msgCount
        )
    }

    fun fromSummary(summaryProj: ChatDaySummaryProjection): com.example.data.ChatDay {
        val keywords = try {
            stringListAdapter.fromJson(summaryProj.rawKeywordsJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val participants = try {
            stringListAdapter.fromJson(summaryProj.rawParticipantsJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return com.example.data.ChatDay(
            date = summaryProj.date,
            messages = emptyList(), // Lightweight projection: zero CursorWindow / OOM memory pressure!
            summary = summaryProj.summary,
            keywords = keywords,
            participants = participants,
            msgCount = summaryProj.msgCount
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
