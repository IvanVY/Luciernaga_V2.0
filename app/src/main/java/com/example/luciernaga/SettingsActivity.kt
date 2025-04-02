package com.example.luciernaga

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.net.HttpURLConnection
import java.net.URL

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("SettingsActivity", "Iniciando SettingsActivity")
        setContentView(R.layout.activity_settings)

        // 1. Inicialización temprana de componentes clave
        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        firestore = FirebaseFirestore.getInstance()

        // 2. Verificar si ya está configurado
        if (isAlreadyConfigured()) {
            redirectToMain()
            return
        }

        // 3. Mostrar UI solo si es necesario configurar
        setContentView(R.layout.activity_settings)
        setupUI()
    }

    private fun isAlreadyConfigured(): Boolean {
        val savedIP = sharedPreferences.getString("esp32_ip", null)
        val currentUser = auth.currentUser
        val forceConfig = intent.getBooleanExtra("force_config", false)  // <-- Leer el flag correctamente

        return !forceConfig && !savedIP.isNullOrEmpty() && currentUser != null
    }


    private fun redirectToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    private fun setupUI() {
        val isFirstTimeSetup = intent.getBooleanExtra("first_time_setup", false)
        val ipInput = findViewById<EditText>(R.id.ipInput).apply {
            setText(sharedPreferences.getString("esp32_ip", ""))
        }

        setupDoneButton(isFirstTimeSetup)

        // Configuración de botones
        findViewById<Button>(R.id.btnSendCredentials).setOnClickListener { sendCredentials() }
        findViewById<Button>(R.id.btnClearCredentials).setOnClickListener { clearCredentials() }
        findViewById<Button>(R.id.btnRestartESP32).setOnClickListener { restartESP32() }

        // Asegúrate de que el botón btnDone siempre sea visible
        val btnDone = findViewById<Button>(R.id.btnDone)
        btnDone.visibility = View.VISIBLE  // Garantiza que el botón sea visible

        // Agregar un botón para validar y guardar la IP manualmente
        btnDone.setOnClickListener {
            val ip = ipInput.text.toString().trim()
            validateAndSaveIP(ip)
        }
    }


    private fun validateAndSaveIP(ip: String) {
        if (isValidIPAddress(ip)) {
            // Guardar la IP en SharedPreferences
            sharedPreferences.edit().putString("esp32_ip", ip).apply()

            // Guardar la IP en Firebase Firestore
            saveConfiguration(ip)

            // Mostrar el botón "Listo"
            findViewById<MaterialButton>(R.id.btnDone).visibility = View.VISIBLE

            showSnackbar("IP configurada correctamente")
        } else {
            showSnackbar("Ingrese una IP válida (ej. 192.168.x.x)")
        }
    }

    private fun isValidIPAddress(ip: String): Boolean {
        val parts = ip.trim().split(".")
        if (parts.size != 4) return false

        return try {
            parts.all { part ->
                val num = part.toInt()
                num in 0..255
            }
        } catch (e: NumberFormatException) {
            false
        }
    }

    private fun saveConfiguration(newIP: String) {
        // Guardar la IP en SharedPreferences
        sharedPreferences.edit().putString("esp32_ip", newIP).apply()

        // Guardar la IP en Firebase Firestore
        auth.currentUser?.uid?.let { userId ->
            firestore.collection("users").document(userId)
                .set(mapOf("esp32_ip" to newIP), SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("FirestoreConfig", "IP guardada en Firestore: $newIP")
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreConfig", "Error al guardar IP en Firestore", e)
                }
        }
    }

    private fun sendCredentials() {
        val ipInput = findViewById<EditText>(R.id.ipInput)
        val ssidInput = findViewById<EditText>(R.id.ssidInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)

        val apIP = validateIP(ipInput.text.toString())
        val ssid = ssidInput.text.toString()
        val password = passwordInput.text.toString()

        if (apIP.isEmpty() || ssid.isEmpty() || password.isEmpty()) {
            showSnackbar("Complete todos los campos")
            return
        }

        sendCredentialsToESP32(apIP, ssid, password) { success ->
            runOnUiThread {
                if (success) {
                    handleSuccessfulConfig(apIP)
                } else {
                    showSnackbar("Error al enviar credenciales")
                }
            }
        }
    }

    private fun clearCredentials() {
        val esp32IP = getESP32IP()
        if (esp32IP.isEmpty()) return

        val url = "http://$esp32IP/clearCredentials"
        sendRequestToESP32(url) { success ->
            runOnUiThread {
                if (success) {
                    showSnackbar("Credenciales limpiadas correctamente")
                } else {
                    showSnackbar("Error al limpiar credenciales")
                }
            }
        }
    }

    private fun restartESP32() {
        val esp32IP = getESP32IP()
        if (esp32IP.isEmpty()) return

        val url = "http://$esp32IP/restart"
        sendRequestToESP32(url) { success ->
            runOnUiThread {
                if (success) {
                    showSnackbar("ESP32 reiniciado correctamente")
                } else {
                    showSnackbar("Error al reiniciar el ESP32")
                }
            }
        }
    }

    private fun getESP32IP(): String {
        val ip = findViewById<EditText>(R.id.ipInput).text.toString().trim()
        Log.d("IP_VALIDATION", "Validando IP: $ip")

        if (ip.isEmpty()) {
            showSnackbar("Por favor ingrese la dirección IP")
            return ""
        }

        if (!isValidIPAddress(ip)) {
            Log.e("IP_VALIDATION", "IP inválida detectada: $ip")
            showSnackbar("Formato de IP inválido. Ejemplo: 192.168.1.1")
            return ""
        }

        Log.d("IP_VALIDATION", "IP válida aceptada: $ip")
        return ip
    }

    private fun handleSuccessfulConfig(apIP: String) {
        Thread.sleep(5000) // Esperar para que el ESP32 se reconfigure
        fetchNewESP32IP(apIP) { newIP ->
            runOnUiThread {
                if (!newIP.isNullOrEmpty()) {
                    saveConfiguration(newIP)
                    showSnackbar("Configuración exitosa")
                    if (intent.getBooleanExtra("first_time_setup", false)) {
                        redirectToMain()
                    }
                } else {
                    showSnackbar("Error obteniendo nueva IP")
                }
            }
        }
    }

    private fun sendCredentialsToESP32(apIP: String, ssid: String, password: String, callback: (Boolean) -> Unit) {
        val url = "http://$apIP/saveCredentials?ssid=$ssid&password=$password"
        sendRequestToESP32(url, callback)
    }

    private fun fetchNewESP32IP(apIP: String, callback: (String?) -> Unit) {
        val url = "http://$apIP/getIP"
        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    callback(response.trim())
                } else {
                    callback(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(null)
            }
        }.start()
    }

    private fun sendRequestToESP32(urlString: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val success = connection.responseCode == 200
                callback(success)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(false)
            }
        }.start()
    }

    private fun validateIP(ip: String): String {
        return if (isValidIPAddress(ip)) ip else ""
    }

    private fun setupDoneButton(isFirstTimeSetup: Boolean) {
        val btnDone = findViewById<MaterialButton>(R.id.btnDone).apply {
            visibility = if (isFirstTimeSetup) View.VISIBLE else View.GONE
            setOnClickListener {
                if (sharedPreferences.getString("esp32_ip", "").isNullOrEmpty()) {
                    showSnackbar("Configure una IP válida primero")
                } else {
                    redirectToMain()
                }
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }
}