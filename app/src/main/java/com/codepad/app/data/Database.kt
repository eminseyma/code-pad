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
import androidx.room.Upsert
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
}

@Database(entities = [Notebook::class, Note::class], version = 1, exportSchema = false)
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
                ).build().also { instance = it }
            }
    }
}
