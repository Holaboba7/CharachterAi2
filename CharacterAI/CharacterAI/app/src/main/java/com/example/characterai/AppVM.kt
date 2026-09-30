package com.example.characterai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppVM(app: Application) : AndroidViewModel(app) {
    private val db = Room.databaseBuilder(app, AppDb::class.java, "characters.db").build()
    val settings = Settings(app)

    val characters = db.characters().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val streaming = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var job: Job? = null

    init {
        viewModelScope.launch {
            if (db.characters().count() == 0) {
                save(CharacterEntity(name = "Sherlock Holmes", description = "Il celebre detective di Baker Street.",
                    greeting = "Ah, un nuovo caso? Si accomodi e mi racconti tutto.",
                    systemPrompt = "Sei Sherlock Holmes. Rispondi in italiano, con deduzioni brillanti, tono elegante e un po' arrogante. Resta sempre nel personaggio."))
                save(CharacterEntity(name = "Assistente amichevole", description = "Un compagno di chiacchiere sempre disponibile.",
                    greeting = "Ciao! Di cosa vuoi parlare oggi?",
                    systemPrompt = "Sei un amico caloroso e curioso. Rispondi in italiano in modo naturale e conciso."))
            }
        }
    }

    fun messages(id: Long) = db.messages().forCharacter(id)
    suspend fun find(id: Long) = db.characters().find(id)

    fun save(c: CharacterEntity) = viewModelScope.launch {
        val id = db.characters().upsert(c)
        if (c.id == 0L && c.greeting.isNotBlank())
            db.messages().insert(MessageEntity(characterId = id, role = "assistant", content = c.greeting))
    }
    fun delete(c: CharacterEntity) = viewModelScope.launch { db.characters().delete(c) }
    fun clearChat(id: Long) = viewModelScope.launch { db.messages().clear(id) }
    fun stop() { job?.cancel() }

    fun send(id: Long, text: String) {
        if (text.isBlank() || job?.isActive == true) return
        if (settings.apiKey.isBlank()) { error.value = "Imposta la chiave API nelle Impostazioni."; return }
        error.value = null
        job = viewModelScope.launch {
            val ch = db.characters().find(id) ?: return@launch
            busy.value = true
            db.messages().insert(MessageEntity(characterId = id, role = "user", content = text.trim()))
            val hist = db.messages().forCharacter(id).first().takeLast(40).map { it.role to it.content }
            val sb = StringBuilder()
            try {
                Llm.stream(settings.baseUrl, settings.apiKey, settings.model, listOf("system" to ch.systemPrompt) + hist)
                    .collect { sb.append(it); streaming.value = sb.toString() }
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                error.value = e.message ?: "Errore di rete"
            } finally {
                withContext(NonCancellable) {
                    if (sb.isNotBlank()) db.messages().insert(MessageEntity(characterId = id, role = "assistant", content = sb.toString()))
                    streaming.value = ""
                    busy.value = false
                }
            }
        }
    }
}
