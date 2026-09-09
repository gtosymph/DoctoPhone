package com.kmt.healthanalyzer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Le rôle d'un message dans la conversation de l'onglet Analyse. */
enum class ChatRole { USER, ASSISTANT }

/**
 * Un message de la conversation, gardé pour que l'utilisateur la retrouve entre deux
 * ouvertures de l'app.
 *
 * L'index sur [createdAtEpochMillis] sert l'unique requête de lecture : les messages
 * dans l'ordre chronologique, et les [id] derniers pour borner l'historique envoyé au
 * modèle de langage (voir `ChatWithHealthUseCase`).
 *
 * [rangeFrom] et [rangeTo] portent la fenêtre (dates ISO `AAAA-MM-JJ`) que le modèle
 * examinait quand il a écrit ce message, si `healthrange` en a choisi une pour ce tour —
 * `null` sinon. Un graphique de ce message se résout avec cette fenêtre, jamais avec la
 * fenêtre active du moment : sans elle, le graphique d'une ancienne réponse changerait de
 * contenu après coup, dès que la conversation change de période — voir
 * [com.kmt.healthanalyzer.domain.usecase.ChatWithHealthUseCase] et `chat-view.js`
 * (`messageRange`). Migration 3 → 4 ([com.kmt.healthanalyzer.data.db.MIGRATION_3_4]).
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index("createdAtEpochMillis")],
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: ChatRole,
    val content: String,
    val createdAtEpochMillis: Long,
    val rangeFrom: String? = null,
    val rangeTo: String? = null,
)
