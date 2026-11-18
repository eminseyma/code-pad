package com.codepad.app

import android.app.Application
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
import com.codepad.app.drive.DriveApi
import com.codepad.app.data.ChatSession
import com.codepad.app.data.SavedMessage
import com.codepad.app.data.AppDatabase
import com.codepad.app.data.DefaultActions
import com.codepad.app.data.Note
import com.codepad.app.data.NoteCard
import com.codepad.app.data.Notebook
import com.codepad.app.data.SettingsStore
import com.codepad.app.data.ThemeChoice
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    // Google Drive yedeği
    var driveConnected by mutableStateOf(settings.driveConnected)
        private set
    var driveHold by mutableStateOf(settings.driveHold)
        private set
    var lastBackup by mutableStateOf(settings.lastBackup)
        private set
    var backupKeyEnabled by mutableStateOf(settings.backupKey)
        private set
    var backupStatus by mutableStateOf("")
        private set
    var backupBusy by mutableStateOf(false)
        private set
    private var backupJob: Job? = null
    private var pendingBackup = false

    init {
        viewModelScope.launch {
            if (dao.notebookCount() == 0) dao.insertNotebook(Notebook(name = "Genel Notlar"))
        }
    }

    // ---- Defterler ve notlar ----

    fun addNotebook(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.insertNotebook(Notebook(name = name.trim(), colorIndex = notebooks.value.size % 4))
        scheduleBackup()
    }

    fun deleteNotebook(notebook: Notebook) = viewModelScope.launch {
        dao.deleteNotebook(notebook)
        scheduleBackup()
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
        scheduleBackup()
    }

    fun deleteNote(note: Note) = viewModelScope.launch {
        dao.deleteNote(note)
        scheduleBackup()
    }

    fun updateNotebookStyle(notebook: Notebook, cover: String, page: String) = viewModelScope.launch {
        dao.updateNotebook(notebook.copy(coverColor = cover, pageStyle = page))
        scheduleBackup()
    }

    // ---- Not kartları ----

    fun addCard(noteId: Long, title: String, body: String, section: String = "") = viewModelScope.launch {
        dao.insertCard(NoteCard(noteId = noteId, title = title, body = body, section = section.trim()))
        scheduleBackup()
    }

    fun moveCard(card: NoteCard, section: String) = viewModelScope.launch {
        dao.updateCard(card.copy(section = section.trim()))
        scheduleBackup()
    }

    fun deleteCard(card: NoteCard) = viewModelScope.launch {
        dao.deleteCard(card)
        scheduleBackup()
    }

    // ---- Ayarlar ----

    fun saveSettings(newKey: String, newModel: String) {
        settings.apiKey = newKey
        settings.model = newModel
        apiKey = settings.apiKey
        model = settings.model
        scheduleBackup()
    }

    fun changeTheme(choice: ThemeChoice) {
        settings.theme = choice
        theme = choice
        scheduleBackup()
    }

    fun saveActions(list: List<AiAction>) {
        settings.saveActions(list)
        actions = list
        scheduleBackup()
    }

    fun resetActions() = saveActions(DefaultActions)

    // ---- Google Drive yedeği ----

    /** Drive'dan sessizce erişim jetonu alır; izin gerekiyorsa bağlantıyı düşürür. */
    private suspend fun silentToken(): String? {
        val r = DriveApi.authorize(appContext)
        if (r.hasResolution()) {
            settings.driveConnected = false
            driveConnected = false
            backupStatus = "Drive izni yenilenmeli. Ayarlar'dan tekrar bağlan."
            return null
        }
        return r.accessToken
    }

    private suspend fun uploadBackup(token: String) {
        DriveApi.upload(token, backup.export(backupKeyEnabled))
        pendingBackup = false
        settings.lastBackup = System.currentTimeMillis()
        lastBackup = settings.lastBackup
        backupStatus = "Yedeklendi."
    }

    fun reportBackup(message: String) {
        backupStatus = message
    }

    /** İlk girişten sonra çağrılır. Drive'da yedek varsa üzerine yazmadan önce kullanıcıya sorar. */
    fun onDriveConnected() {
        settings.driveConnected = true
        driveConnected = true
        viewModelScope.launch {
            backupBusy = true
            try {
                val token = silentToken() ?: return@launch
                if (DriveApi.hasBackup(token)) {
                    settings.driveHold = true
                    driveHold = true
                    backupStatus = "Drive'da daha önce alınmış bir yedek var. 'Geri yükle' ile getir ya da " +
                        "'Şimdi yedekle' ile bu telefondaki verilerle değiştir."
                } else {
                    settings.driveHold = false
                    driveHold = false
                    uploadBackup(token)
                }
            } catch (e: Exception) {
                backupStatus = "Bağlandı ama Drive'a erişilemedi: ${e.message}"
            } finally {
                backupBusy = false
            }
        }
    }

    fun disconnectDrive() {
        settings.driveConnected = false
        driveConnected = false
        settings.driveHold = false
        driveHold = false
        backupStatus = "Bağlantı kesildi. Drive'daki yedek silinmedi."
    }

    fun changeBackupKey(on: Boolean) {
        settings.backupKey = on
        backupKeyEnabled = on
        scheduleBackup()
    }

    /** Değişikliklerden 15 sn sonra yedekler (üst üste yazmaları birleştirir). */
    fun scheduleBackup() {
        if (!driveConnected) return
        pendingBackup = true
        backupJob?.cancel()
        backupJob = viewModelScope.launch {
            delay(15_000)
            backupNow()
        }
    }

    /** Uygulama arka plana giderken bekleyen yedek varsa hemen gönderir. */
    fun flushBackup() {
        if (pendingBackup && driveConnected) {
            backupJob?.cancel()
            backupNow()
        }
    }

    /** manual = true ise "Drive'da yedek var" beklemesini de kaldırır (bu telefonun verisi yedeğin yerine geçer). */
    fun backupNow(manual: Boolean = false) {
        if (backupBusy) return
        if (driveHold && !manual) return
        viewModelScope.launch {
            backupBusy = true
            if (manual) backupStatus = "Yedekleniyor…"
            try {
                val token = silentToken() ?: return@launch
                uploadBackup(token)
                if (driveHold) {
                    driveHold = false
                    settings.driveHold = false
                }
            } catch (e: Exception) {
                backupStatus = "Yedekleme başarısız: ${e.message}"
            } finally {
                backupBusy = false
            }
        }
    }

    /** Drive'daki yedeği bu telefona yükler; mevcut yerel verinin yerine geçer. */
    fun restoreFromDrive() {
        viewModelScope.launch {
            backupBusy = true
            backupStatus = "Yedek indiriliyor…"
            try {
                val token = silentToken() ?: return@launch
                val json = DriveApi.download(token)
                if (json == null) {
                    backupStatus = "Drive'da yedek bulunamadı."
                } else {
                    backup.restore(json)
                    apiKey = settings.apiKey
                    model = settings.model
                    theme = settings.theme
                    actions = settings.actions()
                    driveHold = false
                    settings.driveHold = false
                    newSession()
                    backupStatus = "Yedek geri yüklendi."
                }
            } catch (e: Exception) {
                backupStatus = "Geri yükleme başarısız: ${e.message}"
            } finally {
                backupBusy = false
            }
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
            scheduleBackup()
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
        scheduleBackup()
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
