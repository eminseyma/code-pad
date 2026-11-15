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
import com.codepad.app.data.AppDatabase
import com.codepad.app.data.Note
import com.codepad.app.data.Notebook
import com.codepad.app.data.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AiAction(val label: String, val instruction: String) {
    EXPLAIN("Açıkla", "Aşağıdaki notu, bir yazılım öğrencisine anlatır gibi sade Türkçe ile açıkla. Kısa ol."),
    SUMMARIZE("Özetle", "Aşağıdaki notu en fazla 5 maddede özetle."),
    EXAMPLE("Örnek ekle", "Aşağıdaki notun konusuna uygun, kısa ve çalışır bir kod örneği ekle. Kodu markdown kod bloğu içinde ver, kısa bir açıklama yaz."),
    BUGS("Hata bul", "Aşağıdaki notta (özellikle kod bloklarında) hata, eksik ya da yanlış bilgi var mı? Varsa düzeltmesiyle birlikte kısaca açıkla."),
}

sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Done(val text: String) : AiState
    data class Failed(val message: String) : AiState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    private val settings = SettingsStore(app)

    val notebooks: StateFlow<List<Notebook>> =
        dao.notebooks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val noteCounts: StateFlow<Map<Long, Int>> =
        dao.counts().map { list -> list.associate { it.notebookId to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val allNotes: StateFlow<List<Note>> =
        dao.allNotesFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun notesIn(notebookId: Long) = dao.notesIn(notebookId)

    var apiKey by mutableStateOf(settings.apiKey)
        private set
    var model by mutableStateOf(settings.model)
        private set

    var aiState by mutableStateOf<AiState>(AiState.Idle)
        private set

    val chat = mutableStateListOf<ChatMessage>()
    var chatBusy by mutableStateOf(false)
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

    fun deleteNotebook(notebook: Notebook) = viewModelScope.launch { dao.deleteNotebook(notebook) }

    /** Boş bir not oluşturur ve id'sini döndürür. */
    suspend fun createNote(notebookId: Long?): Long {
        val target = notebookId ?: notebooks.value.firstOrNull()?.id ?: dao.insertNotebook(Notebook(name = "Genel Notlar"))
        return dao.upsertNote(Note(notebookId = target))
    }

    suspend fun loadNote(id: Long): Note? = dao.note(id)

    fun saveNote(note: Note) = viewModelScope.launch {
        dao.upsertNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    fun deleteNote(note: Note) = viewModelScope.launch { dao.deleteNote(note) }

    // ---- Ayarlar ----

    fun saveSettings(newKey: String, newModel: String) {
        settings.apiKey = newKey
        settings.model = newModel
        apiKey = settings.apiKey
        model = settings.model
    }

    // ---- AI: not üzerinde eylemler ----

    fun runAction(action: AiAction, title: String, content: String) {
        aiState = AiState.Loading
        viewModelScope.launch {
            val prompt = "Not başlığı: $title\n\n$content"
            val result = GeminiClient.generate(
                apiKey, model,
                "Sen Code Pad adlı yazılım not uygulamasının asistanısın. Türkçe, kısa ve net yanıt ver. ${action.instruction}",
                listOf(ChatMessage("user", prompt)),
            )
            aiState = result.fold({ AiState.Done(it) }, { AiState.Failed(it.message ?: "Bilinmeyen hata") })
        }
    }

    fun clearAi() {
        aiState = AiState.Idle
    }

    // ---- AI: notlarla sohbet ----

    fun ask(question: String, notebookId: Long? = null) {
        if (question.isBlank() || chatBusy) return
        chat += ChatMessage("user", question)
        chatBusy = true
        viewModelScope.launch {
            val pool = dao.allNotes().let { all -> if (notebookId == null) all else all.filter { it.notebookId == notebookId } }
            val context = relevantNotes(question, pool)
            val system = buildString {
                append("Sen Code Pad adlı yazılım not uygulamasının asistanısın. Türkçe yanıt ver. ")
                append("Önce kullanıcının notlarına dayan; notlarda yoksa bunu söyle, sonra genel bilginle tamamla. ")
                append("Hangi notlardan yararlandığını sonda 'Kaynak:' satırında not başlıklarıyla belirt.\n\n")
                append("KULLANICI NOTLARI:\n")
                if (context.isEmpty()) append("(ilgili not bulunamadı)")
                context.forEach { append("### ${it.title.ifBlank { "Başlıksız" }}\n${it.content.take(1_500)}\n\n") }
            }
            val result = GeminiClient.generate(apiKey, model, system, chat.toList())
            chat += ChatMessage("model", result.getOrElse { "Hata: ${it.message}" })
            chatBusy = false
        }
    }

    fun clearChat() = chat.clear()

    /** Basit anahtar kelime puanlaması; ileride gömme (embedding) ile değiştirilebilir. */
    private fun relevantNotes(query: String, notes: List<Note>): List<Note> {
        val words = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
        if (words.isEmpty()) return notes.take(5)
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
        // Eşleşme yoksa (ör. "genel özet çıkar") en son güncellenen notlara bak.
        return ranked.ifEmpty { notes.sortedByDescending { it.updatedAt }.take(5) }
    }
}
