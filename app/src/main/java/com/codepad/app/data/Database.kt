package com.codepad.app.data

import android.content.Context
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

@Entity(tableName = "notebooks")
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
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
    indices = [Index("notebookId")],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val title: String = "",
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
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
    indices = [Index("noteId")],
)
data class NoteCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val section: String = "",
    val title: String,
    val body: String,
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

@Database(entities = [Notebook::class, Note::class, NoteCard::class], version = 2, exportSchema = false)
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
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
