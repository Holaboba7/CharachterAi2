package com.example.characterai

import android.content.Context
import android.content.SharedPreferences
import androidx.room.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val greeting: String,
    val systemPrompt: String
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(CharacterEntity::class, ["id"], ["characterId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("characterId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: Long,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY name COLLATE NOCASE") fun all(): Flow<List<CharacterEntity>>
    @Query("SELECT * FROM characters WHERE id = :id") suspend fun find(id: Long): CharacterEntity?
    @Query("SELECT COUNT(*) FROM characters") suspend fun count(): Int
    @Upsert suspend fun upsert(c: CharacterEntity): Long
    @Delete suspend fun delete(c: CharacterEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE characterId = :id ORDER BY timestamp, id") fun forCharacter(id: Long): Flow<List<MessageEntity>>
    @Insert suspend fun insert(m: MessageEntity)
    @Query("DELETE FROM messages WHERE characterId = :id") suspend fun clear(id: Long)
}

@Database(entities = [CharacterEntity::class, MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun characters(): CharacterDao
    abstract fun messages(): MessageDao
}

class Settings(context: Context) {
    private val keyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "secure_settings",
        keyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    var apiKey: String
        get() = prefs.getString("key", "") ?: ""
        set(v) = prefs.edit().putString("key", v.trim()).apply()
    var baseUrl: String
        get() = prefs.getString("url", "https://api.openai.com/v1") ?: ""
        set(v) = prefs.edit().putString("url", v.trim()).apply()
    var model: String
        get() = prefs.getString("model", "gpt-4o-mini") ?: ""
        set(v) = prefs.edit().putString("model", v.trim()).apply()
}
