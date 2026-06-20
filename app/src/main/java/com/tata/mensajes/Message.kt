package com.tata.mensajes

import android.app.PendingIntent
import android.app.RemoteInput

/** Datos necesarios para responder a una notificación con texto. */
data class ReplyAction(
    val pendingIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>
)

/** Un mensaje recibido (WhatsApp, SMS, Telegram, ...). */
data class Message(
    /** Firma única para evitar duplicados (conversación + hora + texto). */
    val sig: String,
    /** Identifica la conversación, para borrarla entera al responder. */
    val convKey: String,
    val appLabel: String,
    val sender: String,
    val text: String,
    val time: Long,
    val reply: ReplyAction?,
    /** Si es una llamada perdida: PendingIntent para devolver la llamada. */
    val call: PendingIntent? = null,
    /** Es un adjunto (audio/foto/vídeo) sin texto reproducible aquí. */
    val isMedia: Boolean = false,
    /** Abre la conversación en su app (para escuchar el audio, ver la foto...). */
    val open: PendingIntent? = null
)
