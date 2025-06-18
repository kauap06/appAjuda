package com.example.appajuda

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat

class NotificationActionReceiver : BroadcastReceiver() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "ACTION_SEND_LOCATION") {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            sendLocationFromNotification(context)
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun sendLocationFromNotification(context: Context) {
        if (!hasAllPermissions(context)) {
            Toast.makeText(context, "Permissões de localização necessárias não concedidas!", Toast.LENGTH_SHORT).show()
            return
        }

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
                        Log.e("WhatsApp", "Erro ao enviar localização via WhatsApp: ${e.message}")
                    }
                } else {
                    Toast.makeText(context, "Nenhum número cadastrado para enviar a localização.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Localização não encontrada!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Erro ao obter localização!", Toast.LENGTH_SHORT).show()
            Log.e("Location", "Erro ao obter localização: ${it.message}")
        }
    }

    private fun hasAllPermissions(context: Context): Boolean {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
}

