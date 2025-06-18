package com.example.appajuda

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class GyroService : Service() {

    private val CHANNEL_ID = "GyroServiceChannel"
    private val NOTIFICATION_ID = 2 // <--- ESTA LINHA FOI ALTERADA

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Serviço de Localização")
            .setContentText("O aplicativo está monitorando sua localização em segundo plano.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Substitua pelo seu ícone
            .build()

        startForeground(NOTIFICATION_ID, notification) // <--- ESTA LINHA FOI ALTERADA

        // Aqui você adicionaria a lógica do seu GyroService, como monitoramento de giroscópio ou localização
        // Por enquanto, apenas para fins de demonstração e para evitar o crash.

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Serviço de Localização",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpeza de recursos, se necessário
    }
}
