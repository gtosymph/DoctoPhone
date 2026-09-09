package com.kmt.healthanalyzer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kmt.healthanalyzer.data.db.entity.ChatMessageEntity

/** DAO Room pour la conversation de l'onglet Analyse. */
@Dao
interface ChatMessageDao {

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    /** Tous les messages, dans l'ordre chronologique — c'est celui que la vue affiche. */
    @Query("SELECT * FROM chat_messages ORDER BY createdAtEpochMillis ASC")
    suspend fun all(): List<ChatMessageEntity>

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}
