package com.example.appajuda

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.appajuda.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bumptech.glide.Glide

import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var speechRecognizer: SpeechRecognizer
    private val SPEECH_REQUEST_CODE = 100

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermissions()

        createNotificationChannel()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { Toast.makeText(this@MainActivity, "Pode falar...", Toast.LENGTH_SHORT).show() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
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
                Toast.makeText(this@MainActivity, "Erro no reconhecimento de voz: $errorMessage", Toast.LENGTH_LONG).show()
                Log.e("SpeechRecognizer", "Erro: $error - $errorMessage")
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                    val keyword = sharedPrefs.getString("voice_keyword", "ajuda")?.lowercase(Locale.getDefault())

                    if (spokenText.lowercase(Locale.getDefault()).contains(keyword!!)) {
                        handleEmergencyButton()
                    } else {
                        Toast.makeText(this@MainActivity, "Palavra-chave não reconhecida. Tente novamente.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        binding.pm.setOnClickListener { callNumber("190") }
        binding.pmcivil.setOnClickListener { callNumber("180") }
        binding.samu.setOnClickListener { callNumber("192") }
        binding.bombeiro.setOnClickListener { callNumber("193") }

        binding.telefoneimg.setOnClickListener { handleEmergencyButton() }

        binding.btnSalvar.setOnClickListener {
            val intent = Intent(this, SaveNumberActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }

        binding.btnVoiceCommand.setOnClickListener { startVoiceRecognition() } // Novo botão para comando de voz

        Glide.with(this)
            .asGif()
            .load(R.drawable.gps)
            .into(binding.telefoneimg)

        // Verifica se a Activity foi iniciada pela notificação para iniciar o reconhecimento de voz
        if (intent.getBooleanExtra("START_VOICE_RECOGNITION_FROM_NOTIFICATION", false)) {
            startVoiceRecognition()
        }
    }

    // Adicione este método para lidar com intents se a Activity já estiver em execução
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Atualiza a intent para a Activity
        if (intent?.getBooleanExtra("START_VOICE_RECOGNITION_FROM_NOTIFICATION", false) == true) {
            startVoiceRecognition()
        }
    }

    private fun startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), SPEECH_REQUEST_CODE)
        } else {
            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale a palavra-chave")
            }
            speechRecognizer.startListening(speechIntent)
        }
    }


    private fun handleEmergencyButton() {
        val currentUser = auth.currentUser
        // Se não houver usuário logado, não faz nada.
        // Idealmente, a MainActivity nem deveria ser acessível sem login.
        if (currentUser == null) {
            Toast.makeText(this, "Você precisa estar logado para usar esta função.", Toast.LENGTH_SHORT).show()
            // Exemplo: startActivity(Intent(this, LoginActivity::class.java))
            return
        }

        // Referência para o documento do usuário no Firestore
        val userDocRef = db.collection("usuarios").document(currentUser.uid)

        // Busca os dados do documento uma única vez
        userDocRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Pega o número do campo "active_number"
                    val phoneNumber = document.getString("active_number")

                    if (!phoneNumber.isNullOrEmpty()) {
                        // Se encontrou o número, chama a função para enviar a localização
                        sendLocationToWhatsApp(phoneNumber)
                    } else {
                        // Se o campo está vazio ou não existe
                        Toast.makeText(this, "Nenhum número ativo cadastrado.", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, SaveNumberActivity::class.java)
                        startActivity(intent)
                    }
                } else {
                    // Se o documento do usuário não existe
                    Toast.makeText(this, "Nenhum número ativo cadastrado.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, SaveNumberActivity::class.java)
                    startActivity(intent)
                }
            }
            .addOnFailureListener { exception ->
                // Em caso de falha na comunicação com o Firestore
                Toast.makeText(this, "Erro ao buscar número: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startPowerButtonService() {
        val serviceIntent = Intent(this, GyroService::class.java)
        if (hasAllPermissions()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    fun callNumber(phone: String) {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        startActivity(dialIntent)
    }

    // Função para enviar localização via WhatsApp
    private fun sendLocationToWhatsApp(phoneNumber: String) {
        if (!hasAllPermissions()) {
            Toast.makeText(this, "Permissões de localização necessárias não concedidas!", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permissões de localização não concedidas!", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude
                val message = "Preciso de ajuda! Minha localização: https://maps.google.com/?q=$latitude,$longitude"

                try {
                    val uri = Uri.parse("whatsapp://send?phone=$phoneNumber&text=${Uri.encode(message)}")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "WhatsApp não instalado ou erro ao enviar.", Toast.LENGTH_SHORT).show()
                    Log.e("WhatsApp", "Erro ao enviar localização via WhatsApp: ${e.message}")
                }
            } else {
                Toast.makeText(this, "Localização não encontrada!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Erro ao obter localização!", Toast.LENGTH_SHORT).show()
            Log.e("Location", "Erro ao obter localização: ${it.message}")
        }
    }

    private fun hasAllPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS, // Manter para compatibilidade ou se o SMS ainda for uma opção
            Manifest.permission.FOREGROUND_SERVICE
        )
        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS, // Manter para compatibilidade ou se o SMS ainda for uma opção
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val notGranted = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            startPowerButtonService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }


    @SuppressLint("MissingPermission")
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            startPowerButtonService()
            showPersistentNotification()
        } else {
            Toast.makeText(this, "Permissões necessárias não concedidas!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alerta de Emergência"
            val descriptionText = "Notificação para envio rápido de localização"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("emergency_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showPersistentNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val sendLocationIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = "ACTION_SEND_LOCATION"
        }
        val sendLocationPendingIntent: PendingIntent = PendingIntent.getBroadcast(this, 0, sendLocationIntent, PendingIntent.FLAG_IMMUTABLE)

        // CORREÇÃO AQUI: Mudar para PendingIntent.getActivity e passar uma flag para MainActivity
        val startVoiceCommandIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN // Ação principal para iniciar a Activity
            addCategory(Intent.CATEGORY_LAUNCHER) // Para garantir que a Activity seja lançada
            putExtra("START_VOICE_RECOGNITION_FROM_NOTIFICATION", true) // Nova flag para indicar que o reconhecimento de voz deve ser iniciado
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Para trazer a Activity para o topo ou criar uma nova instância
        }
        val startVoiceCommandPendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, startVoiceCommandIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, "emergency_channel")
            .setContentTitle("App de Ajuda")
            .setContentText("Toque para enviar sua localização de emergência.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Substitua pelo seu ícone
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true) // Torna a notificação persistente
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Enviar Localização", sendLocationPendingIntent) // Substitua pelo seu ícone
            .addAction(R.drawable.ic_launcher_foreground, "Comando de Voz", startVoiceCommandPendingIntent) // Nova ação para comando de voz
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        notificationManager.notify(1, notification)
    }
}

