package com.tata.mensajes

import android.app.Notification
import android.app.RemoteInput
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat

/**
 * Lee las notificaciones de las apps de mensajería y las vuelca en
 * [MessageStore] para mostrarlas en grande. Es la única vía viable de leer
 * mensajes de WhatsApp desde otra app.
 */
class MessageNotificationListener : NotificationListenerService() {

    companion object {
        // Apps de mensajería que nos interesan.
        private val MESSAGING_APPS = setOf(
            "com.whatsapp",                       // WhatsApp
            "com.whatsapp.w4b",                   // WhatsApp Business
            "org.telegram.messenger",             // Telegram
            "org.telegram.messenger.web",
            "org.thoughtcrime.securesms",         // Signal
            "com.google.android.apps.messaging",  // Mensajes (SMS) de Google
            "com.android.messaging",              // SMS AOSP
            "com.samsung.android.messaging"       // SMS Samsung
        )

        // Palabras que identifican la acción "devolver llamada".
        private val CALL_WORDS = listOf("llam", "call", "devolver", "marcar")
    }

    override fun onListenerConnected() {
        // Al conectar, recupera lo que ya estuviera en la barra de estado.
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    private fun handle(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val isMissedCall = n.category == Notification.CATEGORY_MISSED_CALL
        // Acepta apps de mensajería y, además, cualquier llamada perdida (también del teléfono).
        if (sbn.packageName !in MESSAGING_APPS && !isMissedCall) return

        val convKey = sbn.key ?: sbn.packageName

        if (isMissedCall) {
            handleMissedCall(sbn, n, convKey)
            return
        }

        val appLabel = appLabel(sbn.packageName)
        val reply = extractReply(n)

        // 1) Intento preferente: MessagingStyle -> todos los mensajes en orden.
        val style = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        } catch (_: Exception) {
            null
        }

        if (style != null && style.messages.isNotEmpty()) {
            val convTitle = style.conversationTitle?.toString()?.trim()
            for (msg in style.messages) {
                // person == null => lo enviaste tú: no debe aparecer.
                if (msg.person == null) continue
                val body = msg.text?.toString()?.trim().orEmpty()
                if (body.isEmpty()) continue
                val person = msg.person?.name?.toString()?.trim()
                // En grupos antepone quién habla; en chats 1 a 1 usa el título.
                val sender = when {
                    !convTitle.isNullOrEmpty() && !person.isNullOrEmpty() ->
                        "$person · $convTitle"
                    !person.isNullOrEmpty() -> person
                    !convTitle.isNullOrEmpty() -> convTitle
                    else -> appLabel
                }
                addMessage(convKey, appLabel, sender, body, msg.timestamp, reply)
            }
            return
        }

        // 2) Reserva (SMS y notificaciones sin MessagingStyle): última línea.
        val extras = n.extras ?: return
        val sender = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString().trim()
        var text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString().trim()
        if (text.isEmpty()) {
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (!lines.isNullOrEmpty()) text = lines.last().toString().trim()
        }
        if (text.isEmpty() || sender.isEmpty()) return
        addMessage(convKey, appLabel, sender, text, sbn.postTime, reply)
    }

    private fun handleMissedCall(sbn: StatusBarNotification, n: Notification, convKey: String) {
        val extras = n.extras ?: return
        val caller = (extras.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString().trim()
        var text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString().trim()
        if (text.isEmpty()) text = "Llamada perdida"

        val callIntent = extractCallAction(n) ?: n.contentIntent

        MessageStore.add(
            Message(
                sig = "$convKey|call|${sbn.postTime}",
                convKey = convKey,
                appLabel = "📞 Llamada perdida",
                sender = if (caller.isNotEmpty()) caller else "Número desconocido",
                text = text,
                time = sbn.postTime,
                reply = null,
                call = callIntent
            )
        )
    }

    /** Busca la acción de "devolver llamada" (sin RemoteInput). */
    private fun extractCallAction(n: Notification): android.app.PendingIntent? {
        val actions = n.actions ?: return null
        for (action in actions) {
            if (!action.remoteInputs.isNullOrEmpty()) continue
            if (action.actionIntent == null) continue
            val title = action.title?.toString()?.lowercase() ?: ""
            if (CALL_WORDS.any { title.contains(it) }) return action.actionIntent
        }
        return null
    }

    private fun addMessage(
        convKey: String,
        appLabel: String,
        sender: String,
        text: String,
        time: Long,
        reply: ReplyAction?
    ) {
        MessageStore.add(
            Message(
                sig = "$convKey|$time|$text",
                convKey = convKey,
                appLabel = appLabel,
                sender = sender,
                text = text,
                time = time,
                reply = reply
            )
        )
    }

    /** Busca una acción de respuesta directa (RemoteInput) en la notificación. */
    private fun extractReply(n: Notification): ReplyAction? {
        val actions = n.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (!remoteInputs.isNullOrEmpty() && action.actionIntent != null) {
                @Suppress("UNCHECKED_CAST")
                return ReplyAction(action.actionIntent, remoteInputs as Array<RemoteInput>)
            }
        }
        return null
    }

    private fun appLabel(pkg: String): String = when {
        pkg.startsWith("com.whatsapp") -> "WhatsApp"
        pkg.startsWith("org.telegram") -> "Telegram"
        pkg.contains("securesms") -> "Signal"
        pkg.contains("messaging") -> "SMS"
        else -> "Mensaje"
    }
}
