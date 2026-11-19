package com.codepad.app.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "notebooks", indices = [Index(value = ["syncId"], unique = true)])
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'mint'") val coverColor: String = "mint",
    @ColumnInfo(defaultValue = "'lined'") val pageStyle: String = "lined",
    @ColumnInfo(defaultValue = "''") val syncId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = Notebook::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("notebookId"), Index(value = ["syncId"], unique = true)],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val title: String = "",
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val syncId: String = UUID.randomUUID().toString(),
)

/** Nottaki AI kartı. section boşsa "bölümsüz" demektir. */
@Entity(
    tableName = "note_cards",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId"), Index(value = ["syncId"], unique = true)],
)
data class NoteCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val section: String = "",
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val syncId: String = UUID.randomUUID().toString(),
)

/** Silinen kayıtların bulut kimliği; diğer cihazlara silmeyi bildirmek için tutulur. */
@Entity(tableName = "tombstones")
data class Tombstone(
    @PrimaryKey val syncId: String,
    val kind: String, // "notebook" | "note" | "card"
    val deletedAt: Long = System.currentTimeMillis(),
)

/** Asistan sohbeti. */
@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Sohbetteki tek bir mesaj. role: "user" veya "model". */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SavedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class NotebookCount(val notebookId: Long, val count: Int)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notebooks ORDER BY createdAt")
    fun notebooks(): Flow<List<Notebook>>

    @Query("SELECT COUNT(*) FROM notebooks")
    suspend fun notebookCount(): Int

    @Query("SELECT notebookId, COUNT(*) AS count FROM notes GROUP BY notebookId")
    fun counts(): Flow<List<NotebookCount>>

    @Insert
    suspend fun insertNotebook(notebook: Notebook): Long

    @Update
    suspend fun updateNotebook(notebook: Notebook)

    @Delete
    suspend fun deleteNotebook(notebook: Notebook)

    @Query("SELECT * FROM notes WHERE notebookId = :notebookId ORDER BY updatedAt DESC")
    fun notesIn(notebookId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun allNotesFlow(): Flow<List<Note>>

    @Query("SELECT * FROM notes")
    suspend fun allNotes(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun note(id: Long): Note?

    @Upsert
    suspend fun upsertNote(note: Note): Long

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("SELECT * FROM note_cards WHERE noteId = :noteId ORDER BY createdAt")
    fun cardsFor(noteId: Long): Flow<List<NoteCard>>

    @Insert
    suspend fun insertCard(card: NoteCard): Long

    @Update
    suspend fun updateCard(card: NoteCard)

    @Delete
    suspend fun deleteCard(card: NoteCard)

    @Query("SELECT * FROM chat_sessions ORDER BY createdAt DESC")
    fun sessions(): Flow<List<ChatSession>>

    @Insert
    suspend fun insertSession(session: ChatSession): Long

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY id")
    suspend fun messagesOf(sessionId: Long): List<SavedMessage>

    @Insert
    suspend fun insertMessage(message: SavedMessage): Long

    // Yedekleme: tüm verileri oku / geri yüklemeden önce temizle
    @Query("SELECT * FROM notebooks")
    suspend fun allNotebooksOnce(): List<Notebook>

    @Query("SELECT * FROM note_cards")
    suspend fun allCardsOnce(): List<NoteCard>

    @Query("SELECT * FROM chat_sessions")
    suspend fun allSessionsOnce(): List<ChatSession>

    @Query("SELECT * FROM chat_messages")
    suspend fun allMessagesOnce(): List<SavedMessage>

    @Upsert
    suspend fun upsertNotebook(notebook: Notebook): Long

    @Upsert
    suspend fun upsertCard(card: NoteCard): Long

    @Query("SELECT * FROM notes")
    suspend fun allNotesOnce(): List<Note>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun addTombstone(tombstone: Tombstone)

    @Query("SELECT * FROM tombstones")
    suspend fun tombstones(): List<Tombstone>

    @Query("DELETE FROM tombstones WHERE syncId IN (:ids)")
    suspend fun clearTombstones(ids: List<String>)

    @Query("DELETE FROM notebooks WHERE syncId = :syncId")
    suspend fun deleteNotebookBySync(syncId: String)

    @Query("DELETE FROM notes WHERE syncId = :syncId")
    suspend fun deleteNoteBySync(syncId: String)

    @Query("DELETE FROM note_cards WHERE syncId = :syncId")
    suspend fun deleteCardBySync(syncId: String)

    @Query("UPDATE notebooks SET syncId = lower(hex(randomblob(16)))")
    suspend fun resetNotebookSyncIds()

    @Query("UPDATE notes SET syncId = lower(hex(randomblob(16)))")
    suspend fun resetNoteSyncIds()

    @Query("UPDATE note_cards SET syncId = lower(hex(randomblob(16)))")
    suspend fun resetCardSyncIds()

    @Query("DELETE FROM tombstones")
    suspend fun clearAllTombstones()

    @Query("DELETE FROM notebooks")
    suspend fun clearNotebooks()

    @Query("DELETE FROM chat_sessions")
    suspend fun clearSessions()
}

/** v1 -> v2: not kartları tablosu. Mevcut notlar korunur. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_cards` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`noteId` INTEGER NOT NULL, " +
                "`section` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_cards_noteId` ON `note_cards` (`noteId`)")
    }
}

/** v3 -> v4: defter kapağı ve sayfa stili. Mevcut defterler varsayılanla açılır. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notebooks` ADD COLUMN `coverColor` TEXT NOT NULL DEFAULT 'mint'")
        db.execSQL("ALTER TABLE `notebooks` ADD COLUMN `pageStyle` TEXT NOT NULL DEFAULT 'lined'")
    }
}

/** v4 -> v5: bulut senkronu için kalıcı kimlikler (syncId), güncelleme zamanları ve silinen kayıt listesi. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notebooks` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `notebooks` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `notebooks` SET `syncId` = lower(hex(randomblob(16))), `updatedAt` = `createdAt`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notebooks_syncId` ON `notebooks` (`syncId`)")

        db.execSQL("ALTER TABLE `notes` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE `notes` SET `syncId` = lower(hex(randomblob(16)))")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notes_syncId` ON `notes` (`syncId`)")

        db.execSQL("ALTER TABLE `note_cards` ADD COLUMN `syncId` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `note_cards` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `note_cards` SET `syncId` = lower(hex(randomblob(16))), `updatedAt` = `createdAt`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_note_cards_syncId` ON `note_cards` (`syncId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tombstones` (" +
                "`syncId` TEXT NOT NULL PRIMARY KEY, " +
                "`kind` TEXT NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL)",
        )
    }
}

/** v2 -> v3: sohbet geçmişi tabloları. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chat_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chat_messages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`role` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_sessionId` ON `chat_messages` (`sessionId`)")
    }
}

@Database(
    entities = [Notebook::class, Note::class, NoteCard::class, ChatSession::class, SavedMessage::class, Tombstone::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): NoteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "codepad.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
