package com.example.appajuda

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Locale
import android.Manifest
import androidx.annotation.RequiresPermission

class VoiceCommandService : Service(), RecognitionListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val CHANNEL_ID = "VoiceCommandServiceChannel"
    private val NOTIFICATION_ID = 2 // Usar um ID diferente da notificação persistente da MainActivity

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Comando de Voz Ativo")
            .setContentText("Aguardando comando de voz...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Substitua pelo seu ícone
            .setPriority(NotificationCompat.PRIORITY_LOW) // Prioridade mais baixa para não ser tão intrusivo
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        if (intent?.action == "ACTION_START_VOICE_COMMAND") {
            startListeningForVoice()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Serviço de Comando de Voz",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startListeningForVoice() {
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale a palavra-chave")
        }
        speechRecognizer.startListening(speechIntent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }

    // RecognitionListener Callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        Toast.makeText(this, "Pode falar...", Toast.LENGTH_SHORT).show()
    }

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        // Opcional: parar o serviço se não houver mais necessidade de escutar
        // stopSelf()
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Erro de áudio"
            SpeechRecognizer.ERROR_CLIENT -> "Erro do cliente"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissões insuficientes"
            SpeechRecognizer.ERROR_NETWORK -> "Erro de rede"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo limite da rede"
            SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma correspondência encontrada"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconhecedor ocupado"
            SpeechRecognizer.ERROR_SERVER -> "Erro do servidor"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tempo limite da fala"
            else -> "Erro desconhecido"
        }
        Toast.makeText(this, "Erro no reconhecimento de voz: $errorMessage", Toast.LENGTH_LONG).show()
        Log.e("VoiceCommandService", "Erro: $error - $errorMessage")
        stopSelf() // Parar o serviço em caso de erro
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spokenText = matches[0]
            val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val keyword = sharedPrefs.getString("voice_keyword", "ajuda")?.lowercase(Locale.getDefault())

            if (spokenText.lowercase(Locale.getDefault()).contains(keyword!!)) {
                sendLocationToWhatsApp()
            } else {
                Toast.makeText(this, "Palavra-chave não reconhecida. Tente novamente.", Toast.LENGTH_SHORT).show()
            }
        }
        stopSelf() // Parar o serviço após o resultado
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun sendLocationToWhatsApp() {
        val context = this
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(context, "Permissões de localização não concedidas!", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude
                val message = "Preciso de ajuda! Minha localização: https://maps.google.com/?q=$latitude,$longitude"

                val sharedPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                val phoneNumber = sharedPrefs.getString("active_number", "")?.trim()

                if (!phoneNumber.isNullOrEmpty()) {
                    try {
                        val uri = Uri.parse("whatsapp://send?phone=$phoneNumber&text=${Uri.encode(message)}")
                        val whatsappIntent = Intent(Intent.ACTION_VIEW, uri)
                        whatsappIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(whatsappIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "WhatsApp não instalado ou erro ao enviar.", Toast.LENGTH_SHORT).show()
                        Log.e("VoiceCommandService", "Erro ao enviar localização via WhatsApp: ${e.message}")
                    }
                } else {
                    Toast.makeText(context, "Nenhum número cadastrado para enviar a localização.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Localização não encontrada!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Erro ao obter localização!", Toast.LENGTH_SHORT).show()
            Log.e("VoiceCommandService", "Erro ao obter localização: ${it.message}")
        }
    }
}

