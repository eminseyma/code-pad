package com.codepad.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codepad.app.ai.ChatMessage
import com.codepad.app.ai.GeminiClient
import com.codepad.app.data.AiAction
import com.codepad.app.data.BackupManager
import com.codepad.app.data.ChatSession
import com.codepad.app.data.SavedMessage
import com.codepad.app.data.AppDatabase
import com.codepad.app.data.DefaultActions
import com.codepad.app.data.Note
import com.codepad.app.data.NoteCard
import com.codepad.app.data.Notebook
import com.codepad.app.data.SettingsStore
import com.codepad.app.data.Tombstone
import com.codepad.app.data.ThemeChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.room.InvalidationTracker
import com.codepad.app.sync.SupabaseClient
import com.codepad.app.sync.SyncEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SYSTEM = "Sen Code Pad adlı yazılım not uygulamasının asistanısın. Türkçe, kısa ve net yanıt ver. "

/** Bir AI işleminin sonucu. excerpt, kaynak metnin başıdır (kart başlığında kullanılır). */
data class AiResult(val label: String, val excerpt: String, val text: String)

sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Done(val result: AiResult) : AiState
    data class Failed(val message: String) : AiState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext: Application = app
    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    private val settings = SettingsStore(app)

    // ---- Hesap ve senkron ----
    var loggedIn by mutableStateOf(settings.hasSession)
        private set
    var userEmail by mutableStateOf(settings.userEmail)
        private set
    var authBusy by mutableStateOf(false)
        private set
    var authError by mutableStateOf("")
        private set
    var syncStatus by mutableStateOf("")
        private set

    private val syncMutex = Mutex()
    private var syncJob: Job? = null
    private var pendingSync = false

    init {
        // Yerel veri her değiştiğinde (sync'in kendi yazmaları hariç) kısa bir gecikmeyle eşitle.
        AppDatabase.get(app).invalidationTracker.addObserver(
            object : InvalidationTracker.Observer("notebooks", "notes", "note_cards", "tombstones") {
                override fun onInvalidated(tables: Set<String>) = scheduleSync()
            },
        )
        if (loggedIn) scheduleSync(1_500)
    }

    fun scheduleSync(delayMs: Long = 3_000) {
        if (!settings.hasSession) return
        if (syncMutex.isLocked) {
            pendingSync = true
            return
        }
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            delay(delayMs)
            runSync()
        }
    }

    fun syncNow() = viewModelScope.launch { runSync() }

    private suspend fun runSync() {
        if (!settings.hasSession) return
        syncMutex.withLock {
            syncStatus = "Eşitleniyor…"
            syncStatus = try {
                SyncEngine.sync(dao, settings)
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date())
                "Son eşitleme: $time"
            } catch (e: Exception) {
                "Eşitlenemedi: ${e.message ?: "bağlantıyı kontrol et"}"
            }
        }
        if (pendingSync) {
            pendingSync = false
            scheduleSync()
        }
    }

    fun authenticate(email: String, password: String, create: Boolean, onDone: () -> Unit) = viewModelScope.launch {
        authBusy = true
        authError = ""
        try {
            val session = withContext(Dispatchers.IO) {
                if (create) SupabaseClient.signUp(email.trim(), password)
                else SupabaseClient.signIn(email.trim(), password)
            }
            adoptSession(session)
            onDone()
            runSync()
        } catch (e: Exception) {
            authError = e.message ?: "Giriş yapılamadı"
        } finally {
            authBusy = false
        }
    }

    /** Kurtarma kodunu e-postaya gönderir. */
    fun sendRecoveryCode(email: String, onSent: () -> Unit) = viewModelScope.launch {
        authBusy = true
        authError = ""
        try {
            withContext(Dispatchers.IO) { SupabaseClient.sendRecoveryCode(email.trim()) }
            onSent()
        } catch (e: Exception) {
            authError = e.message ?: "Kod gönderilemedi"
        } finally {
            authBusy = false
        }
    }

    /** E-postadaki kodla yeni şifre belirler ve oturum açar. */
    fun completeRecovery(email: String, code: String, newPassword: String, onDone: () -> Unit) = viewModelScope.launch {
        authBusy = true
        authError = ""
        try {
            val session = withContext(Dispatchers.IO) {
                val s = SupabaseClient.verifyRecovery(email.trim(), code.trim())
                SupabaseClient.updatePassword(s.accessToken, newPassword)
                s
            }
            adoptSession(session)
            onDone()
            runSync()
        } catch (e: Exception) {
            authError = e.message ?: "Şifre değiştirilemedi"
        } finally {
            authBusy = false
        }
    }

    /** Oturumu kaydeder. Yerel kayıtlar başka bir hesaba aitse her kayda yeni bulut kimliği verir. */
    private suspend fun adoptSession(session: SupabaseClient.Session) {
        if (settings.ownerId.isNotBlank() && settings.ownerId != session.userId) {
            dao.resetNotebookSyncIds()
            dao.resetNoteSyncIds()
            dao.resetCardSyncIds()
            dao.clearAllTombstones()
        }
        settings.ownerId = session.userId
        settings.saveSession(session)
        loggedIn = true
        userEmail = session.email
    }

    /** Çıkış yapar. Telefondaki notlar kalır ama artık senkronize edilmez. */
    fun signOut() {
        settings.clearSession()
        loggedIn = false
        userEmail = ""
        syncStatus = ""
    }
    private val backup = BackupManager(db, settings)

    val notebooks: StateFlow<List<Notebook>> =
        dao.notebooks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val noteCounts: StateFlow<Map<Long, Int>> =
        dao.counts().map { list -> list.associate { it.notebookId to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val allNotes: StateFlow<List<Note>> =
        dao.allNotesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun notesIn(notebookId: Long) = dao.notesIn(notebookId)
    fun cardsFor(noteId: Long) = dao.cardsFor(noteId)

    val sessions: StateFlow<List<ChatSession>> =
        dao.sessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var apiKey by mutableStateOf(settings.apiKey)
        private set
    var model by mutableStateOf(settings.model)
        private set
    var theme by mutableStateOf(settings.theme)
        private set
    var actions by mutableStateOf(settings.actions())
        private set

    var aiState by mutableStateOf<AiState>(AiState.Idle)
        private set

    val chat = mutableStateListOf<ChatMessage>()
    var chatBusy by mutableStateOf(false)
        private set
    var currentSessionId by mutableStateOf<Long?>(null)
        private set

    // Yedek dosyası
    var backupStatus by mutableStateOf("")
        private set
    var backupBusy by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            if (dao.notebookCount() == 0) dao.insertNotebook(Notebook(name = "Genel Notlar"))
        }
    }

    // ---- Defterler ve notlar ----

    fun addNotebook(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.insertNotebook(Notebook(name = name.trim(), colorIndex = notebooks.value.size % 4))
    }

    fun deleteNotebook(notebook: Notebook) = viewModelScope.launch {
        // Silinenleri bulut senkronuna bildir: defter, içindeki notlar ve kartlar.
        val now = System.currentTimeMillis()
        val notes = dao.allNotesOnce().filter { it.notebookId == notebook.id }
        val noteIds = notes.map { it.id }.toSet()
        dao.allCardsOnce().filter { it.noteId in noteIds }.forEach { dao.addTombstone(Tombstone(it.syncId, "card", now)) }
        notes.forEach { dao.addTombstone(Tombstone(it.syncId, "note", now)) }
        dao.addTombstone(Tombstone(notebook.syncId, "notebook", now))
        dao.deleteNotebook(notebook)
    }

    /** Boş bir not oluşturur ve id'sini döndürür. */
    suspend fun createNote(notebookId: Long?): Long {
        val target = notebookId ?: notebooks.value.firstOrNull()?.id
            ?: dao.insertNotebook(Notebook(name = "Genel Notlar"))
        return dao.upsertNote(Note(notebookId = target))
    }

    suspend fun loadNote(id: Long): Note? = dao.note(id)

    fun saveNote(note: Note) = viewModelScope.launch {
        dao.upsertNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    fun deleteNote(note: Note) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        dao.allCardsOnce().filter { it.noteId == note.id }.forEach { dao.addTombstone(Tombstone(it.syncId, "card", now)) }
        dao.addTombstone(Tombstone(note.syncId, "note", now))
        dao.deleteNote(note)
    }

    fun updateNotebookStyle(notebook: Notebook, cover: String, page: String) = viewModelScope.launch {
        dao.updateNotebook(notebook.copy(coverColor = cover, pageStyle = page, updatedAt = System.currentTimeMillis()))
    }

    // ---- Not kartları ----

    fun addCard(noteId: Long, title: String, body: String, section: String = "") = viewModelScope.launch {
        dao.insertCard(NoteCard(noteId = noteId, title = title, body = body, section = section.trim()))
    }

    fun moveCard(card: NoteCard, section: String) = viewModelScope.launch {
        dao.updateCard(card.copy(section = section.trim(), updatedAt = System.currentTimeMillis()))
    }

    fun deleteCard(card: NoteCard) = viewModelScope.launch {
        dao.addTombstone(Tombstone(card.syncId, "card", System.currentTimeMillis()))
        dao.deleteCard(card)
    }

    // ---- Ayarlar ----

    fun saveSettings(newKey: String, newModel: String) {
        settings.apiKey = newKey
        settings.model = newModel
        apiKey = settings.apiKey
        model = settings.model
    }

    fun changeTheme(choice: ThemeChoice) {
        settings.theme = choice
        theme = choice
    }

    fun saveActions(list: List<AiAction>) {
        settings.saveActions(list)
        actions = list
    }

    fun resetActions() = saveActions(DefaultActions)

    // ---- Yedek dosyası ----

    /** Tüm verileri seçilen dosyaya yazar. */
    fun saveBackupTo(uri: Uri, includeKey: Boolean) = viewModelScope.launch {
        backupBusy = true
        backupStatus = "Yedek yazılıyor…"
        try {
            val json = backup.export(includeKey)
            withContext(Dispatchers.IO) {
                appContext.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: error("dosya açılamadı")
            }
            backupStatus = "Yedek kaydedildi."
        } catch (e: Exception) {
            backupStatus = "Yedek kaydedilemedi: ${e.message}"
        } finally {
            backupBusy = false
        }
    }

    /** Seçilen yedek dosyasını yükler; bu telefondaki veriler dosyadakiyle değiştirilir. */
    fun restoreFrom(uri: Uri) = viewModelScope.launch {
        backupBusy = true
        backupStatus = "Yedek okunuyor…"
        try {
            val json = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("dosya açılamadı")
            }
            backup.restore(json)
            apiKey = settings.apiKey
            model = settings.model
            theme = settings.theme
            actions = settings.actions()
            newSession()
            backupStatus = "Yedek yüklendi."
        } catch (e: Exception) {
            backupStatus = "Yükleme başarısız: ${e.message}"
        } finally {
            backupBusy = false
        }
    }

    // ---- AI ----

    /**
     * Verilen metne bir yönerge uygular. input seçili metin ya da tüm nottur.
     * excerpt, kart başlığında gösterilecek kısa alıntıdır.
     */
    fun runAi(label: String, instruction: String, input: String, excerpt: String) {
        if (input.isBlank()) {
            aiState = AiState.Failed("Önce bir metin seç ya da nota bir şey yaz.")
            return
        }
        aiState = AiState.Loading
        viewModelScope.launch {
            val result = GeminiClient.generate(
                apiKey, model,
                SYSTEM + instruction,
                listOf(ChatMessage("user", input)),
            )
            aiState = result.fold(
                { AiState.Done(AiResult(label, excerpt, it)) },
                { AiState.Failed(it.message ?: "Bilinmeyen hata") },
            )
        }
    }

    fun clearAi() {
        aiState = AiState.Idle
    }

    // ---- Asistan: notlarla sohbet ----

    fun ask(question: String, notebookId: Long? = null) {
        if (question.isBlank() || chatBusy) return
        chat += ChatMessage("user", question)
        chatBusy = true
        viewModelScope.launch {
            // Sohbet yoksa yeni bir oturum aç; her mesajı veritabanına yaz.
            val sid = currentSessionId
                ?: dao.insertSession(ChatSession(title = question.trim().take(40))).also { currentSessionId = it }
            dao.insertMessage(SavedMessage(sessionId = sid, role = "user", text = question))

            val all = dao.allNotes()
            val pool = if (notebookId == null) all else all.filter { it.notebookId == notebookId }
            val context = relevantNotes(question, pool)
            val system = buildString {
                append(SYSTEM)
                append("Önce kullanıcının notlarına dayan; notlarda yoksa bunu söyle, sonra genel bilginle tamamla. ")
                append("Hangi notlardan yararlandığını sonda 'Kaynak:' satırında not başlıklarıyla belirt.\n\n")
                append("KULLANICI NOTLARI:\n")
                if (context.isEmpty()) append("(ilgili not bulunamadı)")
                context.forEach { append("### ${it.title.ifBlank { "Başlıksız" }}\n${it.content.take(1_500)}\n\n") }
            }
            val result = GeminiClient.generate(apiKey, model, system, chat.toList())
            val reply = result.getOrElse { "Hata: ${it.message}" }
            chat += ChatMessage("model", reply)
            dao.insertMessage(SavedMessage(sessionId = sid, role = "model", text = reply))
            chatBusy = false
        }
    }

    /** Yeni bir sohbet başlatır; önceki sohbet geçmişte kalır. */
    fun newSession() {
        currentSessionId = null
        chat.clear()
    }

    /** Geçmişteki bir sohbeti yükler. */
    fun openSession(id: Long) = viewModelScope.launch {
        val msgs = dao.messagesOf(id)
        chat.clear()
        msgs.forEach { chat += ChatMessage(it.role, it.text) }
        currentSessionId = id
    }

    fun deleteSession(id: Long) = viewModelScope.launch {
        dao.deleteSession(id)
        if (currentSessionId == id) newSession()
    }

    /** Basit anahtar kelime puanlaması. Eşleşme yoksa en son güncellenen notlara bakar. */
    private fun relevantNotes(query: String, notes: List<Note>): List<Note> {
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
        if (words.isEmpty()) return notes.sortedByDescending { it.updatedAt }.take(5)
        val ranked = notes
            .map { n ->
                val title = n.title.lowercase()
                val body = n.content.lowercase()
                n to words.sumOf { w -> (if (title.contains(w)) 3 else 0) + (if (body.contains(w)) 1 else 0) }
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
        return ranked.ifEmpty { notes.sortedByDescending { it.updatedAt }.take(5) }
    }
}
