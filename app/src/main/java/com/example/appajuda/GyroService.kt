package com.example.appajuda

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat

class GyroService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private val rotationThreshold: Float = 20f // Reduzido para melhorar a detecção
    private var lastToastTime: Long = 0
    private val toastDelay: Long = 2000 // 2 segundos
    private var lastEmergencyTime: Long = 0
    private val emergencyDelay: Long = 5000 // 10 segundos


    override fun onCreate() {
        super.onCreate()
        Log.d("GyroService", "Serviço criado")

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (gyroscope == null) {
            Log.e("GyroService", "Giroscópio não disponível! Encerrando serviço.")
            stopSelf()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("ForegroundServiceType")
    private fun startForegroundServiceProperly() {
        val channelId = "GyroServiceChannel"
        val channelName = "Gyro Service"

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("VICAPP")
            .setContentText("Detectando movimentos de emergência...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()

        // Inicia o serviço em primeiro plano
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("GyroService", "Serviço iniciado")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundServiceProperly()
        }

        // Registra o sensor apenas se ele estiver disponível
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("GyroService", "Serviço destruído")
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val rotationX = event.values[0]
            val rotationY = event.values[1]
            val rotationZ = event.values[2]

            if (Math.abs(rotationX) > rotationThreshold ||
                Math.abs(rotationY) > rotationThreshold ||
                Math.abs(rotationZ) > rotationThreshold) {

                val currentTime = System.currentTimeMillis()

                // Verifica se o Toast pode ser exibido
                if (currentTime - lastToastTime > toastDelay) {
                    Log.d("GyroService", "Movimento giratório detectado!")
                    Toast.makeText(this, "Emergência detectada!", Toast.LENGTH_SHORT).show()
                    lastToastTime = currentTime
                }

                // Verifica se a função sendLocationSms pode ser chamada
                if (currentTime - lastEmergencyTime > emergencyDelay) {
                    sendLocationSms()
                    lastEmergencyTime = currentTime
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun sendLocationSms() {
        // Recupera o número ativo salvo em SharedPreferences
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val phoneNumber = sharedPreferences.getString("active_number", "")  // Recupera o número ativo

        if (phoneNumber.isNullOrEmpty()) {
            Log.e("GyroService", "Nenhum número ativo salvo!")
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("GyroService", "Permissão de localização não concedida")
            return
        }

        val locationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
        locationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                sendSmsWithLocation(phoneNumber, location)
            } else {
                Log.e("GyroService", "Localização indisponível")
            }
        }.addOnFailureListener { e: Exception ->
            Log.e("GyroService", "Erro ao obter localização: ${e.message}")
        }
    }

    private fun sendSmsWithLocation(phoneNumber: String, location: Location) {
        val message = "Preciso de ajuda! Minha localização: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phoneNumber"))
        smsIntent.putExtra("sms_body", message)
        smsIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(smsIntent)
    }
}
