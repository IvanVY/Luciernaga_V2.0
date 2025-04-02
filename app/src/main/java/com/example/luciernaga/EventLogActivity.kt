package com.example.luciernaga

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.widget.SwitchCompat
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.HttpURLConnection
import java.net.URL

class EventLogActivity : AppCompatActivity() {

    private lateinit var eventLogRecyclerView: RecyclerView
    private lateinit var sensorSwitch: SwitchCompat
    private lateinit var btnSelectRelays: Button
    private lateinit var btnClearLog: Button
    private lateinit var relayCheckbox0: CheckBox
    private lateinit var relayCheckbox1: CheckBox
    private lateinit var relayCheckbox2: CheckBox
    private lateinit var eventLogHandler: Handler
    private lateinit var eventLogRunnable: Runnable
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_log)

        // Inicializar SharedPreferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // Inicializar referencias a los elementos de la interfaz
        eventLogRecyclerView = findViewById(R.id.eventLogRecyclerView)
        sensorSwitch = findViewById(R.id.sensorSwitch)
        btnSelectRelays = findViewById(R.id.btnSelectRelays)
        btnClearLog = findViewById(R.id.btnClearLog)
        relayCheckbox0 = findViewById(R.id.relayCheckbox0)
        relayCheckbox1 = findViewById(R.id.relayCheckbox1)
        relayCheckbox2 = findViewById(R.id.relayCheckbox2)

        // Configurar el RecyclerView
        eventLogRecyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = EventLogAdapter()
        eventLogRecyclerView.adapter = adapter

        // Cargar la selección de relés guardada
        loadRelaySelection()

        // Configurar listeners
        setupRelaySelectionListeners()
        setupSensorSwitch()
        setupClearLogButton(adapter)

        // Obtener el registro de eventos inicialmente
        fetchInitialStates()

        // Configurar el Handler para actualizar el registro en tiempo real
        eventLogHandler = Handler(Looper.getMainLooper())
        eventLogRunnable = object : Runnable {
            override fun run() {
                fetchEventLog(adapter)
                eventLogHandler.postDelayed(this, 5000)
            }
        }
        eventLogHandler.post(eventLogRunnable)
    }

    override fun onResume() {
        super.onResume()
        fetchInitialStates()
    }

    private fun fetchInitialStates() {
        fetchEventLog(eventLogRecyclerView.adapter as EventLogAdapter)
        fetchSensorState()
        fetchRelaySelection()
    }

    private fun loadRelaySelection() {
        relayCheckbox0.isChecked = sharedPreferences.getBoolean("relay_0_selected", false)
        relayCheckbox1.isChecked = sharedPreferences.getBoolean("relay_1_selected", false)
        relayCheckbox2.isChecked = sharedPreferences.getBoolean("relay_2_selected", false)
    }

    private fun saveRelaySelection() {
        sharedPreferences.edit().apply {
            putBoolean("relay_0_selected", relayCheckbox0.isChecked)
            putBoolean("relay_1_selected", relayCheckbox1.isChecked)
            putBoolean("relay_2_selected", relayCheckbox2.isChecked)
            apply() // Usar apply() para guardar asincrónicamente
        }
    }

    private fun setupRelaySelectionListeners() {
        btnSelectRelays.setOnClickListener {
            val selectedRelays = mutableListOf<Int>().apply {
                if (relayCheckbox0.isChecked) add(0)
                if (relayCheckbox1.isChecked) add(1)
                if (relayCheckbox2.isChecked) add(2)
            }

            if (selectedRelays.isEmpty()) {
                Toast.makeText(this, "Selecciona al menos un relé", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            NetworkUtils.sendRelaySelection(this, selectedRelays) { success, response ->
                runOnUiThread {
                    if (success) {
                        saveRelaySelection()
                        // Mostrar la respuesta del ESP32 directamente
                        Toast.makeText(
                            this@EventLogActivity,
                            response ?: "Configuración guardada",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@EventLogActivity,
                            "Error al guardar",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }
    private fun setupSensorSwitch() {
        // Primero desactivar el listener temporalmente
        sensorSwitch.setOnCheckedChangeListener(null)

        NetworkUtils.fetchSensorState(this) { isActive ->
            runOnUiThread {
                // Actualizar el estado del switch sin activar el listener
                sensorSwitch.isChecked = isActive

                // Ahora configurar el listener
                sensorSwitch.setOnCheckedChangeListener { _, isChecked ->
                    toggleSensorState(isChecked)
                }
            }
        }
    }

    private fun setupClearLogButton(adapter: EventLogAdapter) {
        btnClearLog.setOnClickListener {
            val esp32IP = NetworkUtils.getESP32IP(this) // Usar el método de NetworkUtils
            if (esp32IP.isEmpty()) {
                Toast.makeText(
                    this,
                    "La dirección IP no está configurada correctamente",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // Construir URL con parámetro de cache busting
            val url = "$esp32IP/clearLog?t=${System.currentTimeMillis()}"
            Log.d("ClearLog", "URL de limpieza: $url")

            // Usar OkHttpClient de NetworkUtils para consistencia
            NetworkUtils.sendGetRequest(url) { success ->
                runOnUiThread {
                    if (success) {
                        // Limpiar el adaptador y mostrar mensaje
                        adapter.updateData(emptyList())
                        Toast.makeText(
                            this@EventLogActivity,
                            "Registro limpiado correctamente",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Forzar actualización del log
                        fetchEventLog(adapter)
                    } else {
                        Toast.makeText(
                            this@EventLogActivity,
                            "Error al limpiar. Intente nuevamente",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e("ClearLog", "Falló la solicitud de limpieza")
                    }
                }
            }
        }
    }

    private fun fetchEventLog(adapter: EventLogAdapter) {
        NetworkUtils.fetchEventLog(this) { log ->
            runOnUiThread {
                val filteredLog = log.split("\n")
                    .filter { it.isNotEmpty() }
                    .takeLast(50) // Limitar a las últimas 50 entradas
                adapter.updateData(filteredLog)
                Log.d("EventLog", "Registro actualizado. Entradas: ${filteredLog.size}")
            }
        }
    }

    private fun fetchSensorState() {
        NetworkUtils.fetchSensorState(this) { isActive ->
            runOnUiThread {
                sensorSwitch.isChecked = isActive
            }
        }
    }

    private fun fetchRelaySelection() {
        NetworkUtils.fetchEventLog(this) { log ->
            runOnUiThread {
                val relay0Selected = log.contains("Sala seleccionada") || log.contains("Relay 0 seleccionado")
                val relay1Selected = log.contains("Cocina seleccionada") || log.contains("Relay 1 seleccionado")
                val relay2Selected = log.contains("Entrada seleccionada") || log.contains("Relay 2 seleccionado")

                relayCheckbox0.setOnCheckedChangeListener(null)
                relayCheckbox1.setOnCheckedChangeListener(null)
                relayCheckbox2.setOnCheckedChangeListener(null)

                relayCheckbox0.isChecked = relay0Selected
                relayCheckbox1.isChecked = relay1Selected
                relayCheckbox2.isChecked = relay2Selected

                setupRelaySelectionListeners()
                saveRelaySelectionToPrefs(relay0Selected, relay1Selected, relay2Selected)
            }
        }
    }

    private fun saveRelaySelectionToPrefs(r0: Boolean, r1: Boolean, r2: Boolean) {
        sharedPreferences.edit().apply {
            putBoolean("relay_0_selected", r0)
            putBoolean("relay_1_selected", r1)
            putBoolean("relay_2_selected", r2)
            apply()
        }
    }

    private fun toggleSensorState(isChecked: Boolean) {
        val url = "${NetworkUtils.getESP32IP(this)}/toggleSensor"
        NetworkUtils.sendGetRequest(url) { success: Boolean ->  // Especificamos el tipo Boolean
            runOnUiThread {
                if (!success) {  // Ahora el operador '!' funcionará correctamente
                    // Si falla, revertir el cambio
                    sensorSwitch.isChecked = !isChecked
                    Toast.makeText(this, "Error al cambiar estado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getESP32IP(): String {
        return sharedPreferences.getString("esp32_ip", "")?.trim() ?: ""
    }

    private fun sendRequestToESP32(urlString: String, callback: (Boolean) -> Unit) {
        Thread {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                callback(connection.responseCode == 200)
            } catch (e: Exception) {
                Log.e("NetworkUtils", "Error: ${e.message}")
                callback(false)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        eventLogHandler.removeCallbacks(eventLogRunnable)
    }
}