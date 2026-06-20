package com.tata.mensajes

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.tata.mensajes.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: MessageAdapter
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Mensaje que se está respondiendo cuando vuelve el reconocedor de voz.
    private var pendingMessage: Message? = null

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
        val target = pendingMessage
        pendingMessage = null
        val reply = target?.reply
        if (!spoken.isNullOrEmpty() && target != null && reply != null) {
            sendReply(reply, spoken)
            // Respondida: la conversación desaparece de la lista.
            MessageStore.removeConversation(target.convKey)
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = MessageAdapter(
            onRead = ::speak,
            onReply = ::startVoiceReply,
            onCall = ::callBack,
            onOpen = ::openConversation
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.grantButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        initTts()
    }

    private fun initTts() {
        // Fuerza el motor de Google si está; si no, usa el predeterminado.
        val engine = "com.google.android.tts"
        val listener = TextToSpeech.OnInitListener { status ->
            if (status != TextToSpeech.SUCCESS) {
                ttsReady = false
                return@OnInitListener
            }
            val res = tts?.setLanguage(Locale("es", "ES"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())  // reserva
            }
            ttsReady = true
        }
        tts = try {
            TextToSpeech(this, listener, engine)
        } catch (e: Exception) {
            TextToSpeech(this, listener)  // por si el motor de Google no existe
        }
    }

    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())
    private val purgeRunnable = object : Runnable {
        override fun run() {
            MessageStore.purgeExpired()  // borra lo que pase de 24 h
            ticker.postDelayed(this, 60_000)
        }
    }

    override fun onResume() {
        super.onResume()
        MessageStore.onChanged = { runOnUiThread { refresh() } }
        refresh()
        ticker.postDelayed(purgeRunnable, 60_000)
    }

    override fun onPause() {
        super.onPause()
        MessageStore.onChanged = null
        ticker.removeCallbacks(purgeRunnable)
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun refresh() {
        val granted = NotificationManagerCompat
            .getEnabledListenerPackages(this)
            .contains(packageName)

        if (!granted) {
            binding.permissionPanel.visibility = android.view.View.VISIBLE
            binding.list.visibility = android.view.View.GONE
            binding.emptyText.visibility = android.view.View.GONE
            return
        }
        binding.permissionPanel.visibility = android.view.View.GONE

        val items = MessageStore.snapshot()
        adapter.submit(items)
        binding.emptyText.visibility =
            if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.list.visibility =
            if (items.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        // Lista en orden antiguo -> reciente: muestra siempre el más nuevo.
        if (items.isNotEmpty()) {
            binding.list.post { binding.list.scrollToPosition(items.size - 1) }
        }
    }

    private fun speak(m: Message) {
        val engine = tts
        if (!ttsReady || engine == null) {
            Toast.makeText(this, "Voz no disponible", Toast.LENGTH_SHORT).show()
            return
        }
        // Sube la voz al máximo dentro de la app.
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val phrase = "Mensaje de ${m.sender}. ${m.text}"
        val result = engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, m.sig)
        if (result != TextToSpeech.SUCCESS) {
            Toast.makeText(this, "No se pudo reproducir la voz", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceReply(m: Message) {
        if (m.reply == null) return
        pendingMessage = m
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga su respuesta para ${m.sender}")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            pendingMessage = null
            Toast.makeText(this, "No hay reconocimiento de voz", Toast.LENGTH_LONG).show()
        }
    }

    private fun openConversation(m: Message) {
        val open = m.open ?: return
        try {
            open.send()  // abre WhatsApp en ese chat para oír el audio / ver la foto
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir", Toast.LENGTH_LONG).show()
        }
    }

    private fun callBack(m: Message) {
        val call = m.call ?: return
        try {
            call.send()
            // Llamada devuelta: quita el aviso de la lista.
            MessageStore.removeConversation(m.convKey)
            refresh()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo llamar", Toast.LENGTH_LONG).show()
        }
    }

    private fun sendReply(reply: ReplyAction, text: String) {
        try {
            val intent = Intent()
            val results = Bundle()
            for (ri in reply.remoteInputs) {
                results.putCharSequence(ri.resultKey, text)
            }
            RemoteInput.addResultsToIntent(reply.remoteInputs, intent, results)
            reply.pendingIntent.send(this, 0, intent)
            Toast.makeText(this, "Respuesta enviada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo enviar", Toast.LENGTH_LONG).show()
        }
    }
}
