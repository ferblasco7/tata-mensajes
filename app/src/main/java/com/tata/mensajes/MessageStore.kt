package com.tata.mensajes

/**
 * Almacén en memoria, compartido entre el servicio de notificaciones y la
 * Activity (mismo proceso). Mantiene los mensajes ordenados de más antiguo a
 * más reciente (como WhatsApp), sin duplicados.
 */
object MessageStore {

    private const val MAX = 200

    /** Los mensajes sin contestar se borran solos pasadas 24 horas. */
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    private val items = mutableListOf<Message>()
    private val seen = HashSet<String>()

    /** Callback que la Activity registra para refrescar la lista. */
    var onChanged: (() -> Unit)? = null

    /** Elimina los mensajes con más de 24 h. Devuelve true si quitó alguno. */
    private fun pruneOld(): Boolean {
        val limit = System.currentTimeMillis() - MAX_AGE_MS
        return items.removeAll { old ->
            (old.time < limit).also { if (it) seen.remove(old.sig) }
        }
    }

    @Synchronized
    fun purgeExpired() {
        if (pruneOld()) onChanged?.invoke()
    }

    @Synchronized
    fun add(message: Message) {
        pruneOld()
        if (!seen.add(message.sig)) return  // ya estaba: ignora duplicado

        // Inserta manteniendo orden ascendente por hora.
        var i = items.size
        while (i > 0 && items[i - 1].time > message.time) i--
        items.add(i, message)

        while (items.size > MAX) {
            val removed = items.removeAt(0)
            seen.remove(removed.sig)
        }
        onChanged?.invoke()
    }

    /**
     * Borra todos los mensajes de una conversación (al responderla).
     * Mantiene sus firmas en [seen] para que no reaparezcan al reconectar el
     * servicio; los mensajes nuevos (con hora/texto distintos) sí entrarán.
     */
    @Synchronized
    fun removeConversation(convKey: String) {
        if (items.removeAll { it.convKey == convKey }) onChanged?.invoke()
    }

    @Synchronized
    fun snapshot(): List<Message> {
        pruneOld()
        return ArrayList(items)
    }
}
