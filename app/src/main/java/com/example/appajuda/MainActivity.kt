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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestPermissions()

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

        Glide.with(this)
            .asGif()
            .load(R.drawable.gps)
            .into(binding.telefoneimg)
    }

    private fun handleEmergencyButton() {
        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val phoneNumber = sharedPrefs.getString("active_number", "")?.trim()

        if (!phoneNumber.isNullOrEmpty()) {
            // Alterado para enviar localização via WhatsApp
            sendLocationToWhatsApp(phoneNumber)
        } else {
            Toast.makeText(this, "Nenhum número cadastrado.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, SaveNumberActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
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

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.all { it }) {
            startPowerButtonService()
        } else {
            Toast.makeText(this, "Permissões necessárias não concedidas!", Toast.LENGTH_SHORT).show()
        }
    }
}
