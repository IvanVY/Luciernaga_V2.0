package com.example.luciernaga

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.Locale

class RelayControlActivity : AppCompatActivity() {

    private val TAG = "RelayControl"
    private lateinit var relayImage1: ImageView
    private lateinit var relayImage2: ImageView
    private lateinit var relayImage3: ImageView
    private lateinit var relayStatus1: TextView
    private lateinit var relayStatus2: TextView
    private lateinit var relayStatus3: TextView
    private lateinit var btnVoiceCommand: Button
    private lateinit var btnTurnAllOn: Button
    private lateinit var btnTurnAllOff: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var voiceInputLauncher: ActivityResultLauncher<Intent>
    private lateinit var sensorStateListener: ValueEventListener
    private var isInitialLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_relay_control)

        sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        initializeViews()
        setupButtonListeners()
        setupRelayImageListeners()
        setupVoiceInput()
        setupSensorListener()
        fetchInitialRelayStates()
    }

    private fun initializeViews() {
        relayImage1 = findViewById(R.id.relayImage1)
        relayImage2 = findViewById(R.id.relayImage2)
        relayImage3 = findViewById(R.id.relayImage3)
        relayStatus1 = findViewById(R.id.relayStatus1)
        relayStatus2 = findViewById(R.id.relayStatus2)
        relayStatus3 = findViewById(R.id.relayStatus3)
        btnVoiceCommand = findViewById(R.id.btnVoiceCommand)
        btnTurnAllOn = findViewById(R.id.btnTurnAllOn)
        btnTurnAllOff = findViewById(R.id.btnTurnAllOff)
    }

    private fun setupButtonListeners() {
        btnTurnAllOn.setOnClickListener { toggleAllRelays(true) }
        btnTurnAllOff.setOnClickListener { toggleAllRelays(false) }
        btnVoiceCommand.setOnClickListener { startVoiceInput() }
    }

    private fun setupRelayImageListeners() {
        setupRelayImageClickListener(relayImage1, relayStatus1, 0, R.drawable.ic_sala_off, R.drawable.ic_sala_on)
        setupRelayImageClickListener(relayImage2, relayStatus2, 1, R.drawable.ic_cocina_off, R.drawable.ic_cocina_on)
        setupRelayImageClickListener(relayImage3, relayStatus3, 2, R.drawable.ic_entrada_off, R.drawable.ic_entrada_on)
    }

    private fun setupRelayImageClickListener(
        imageView: ImageView,
        textView: TextView,
        relayPin: Int,
        offImage: Int,
        onImage: Int
    ) {
        updateRelayUI(relayPin, loadRelayState(relayPin))

        imageView.setOnClickListener {
            imageView.isEnabled = false
            val newState = !loadRelayState(relayPin)

            NetworkUtils.sendLightCommand(this, relayPin, if (newState) "on" else "off") { success ->
                runOnUiThread {
                    if (success) {
                        verifyActualRelayState(relayPin)
                    } else {
                        showSnackbar("Error al cambiar estado")
                        updateRelayUI(relayPin, !newState)
                    }
                    imageView.isEnabled = true
                }
            }
        }
    }

    private fun verifyActualRelayState(relayPin: Int) {
        NetworkUtils.fetchRelayState(this, relayPin) { actualState ->
            runOnUiThread {
                saveRelayState(relayPin, actualState)
                updateRelayUI(relayPin, actualState)
                showSnackbar("Relé ${relayPin + 1} ${if (actualState) "encendido" else "apagado"}")
            }
        }
    }

    private fun fetchInitialRelayStates() {
        NetworkUtils.fetchRelayStates(this) { states ->
            runOnUiThread {
                if (states.size == 3) {
                    updateRelayUI(0, states[0])
                    updateRelayUI(1, states[1])
                    updateRelayUI(2, states[2])
                    saveRelayStates(states)
                }
            }
        }
    }

    private fun updateRelayUI(relayIndex: Int, isOn: Boolean) {
        when (relayIndex) {
            0 -> {
                relayImage1.setImageResource(if (isOn) R.drawable.ic_sala_on else R.drawable.ic_sala_off)
                relayStatus1.text = if (isOn) "Encendido" else "Apagado"
                relayStatus1.setTextColor(ContextCompat.getColor(this, if (isOn) R.color.green else R.color.gray))
            }
            1 -> {
                relayImage2.setImageResource(if (isOn) R.drawable.ic_cocina_on else R.drawable.ic_cocina_off)
                relayStatus2.text = if (isOn) "Encendido" else "Apagado"
                relayStatus2.setTextColor(ContextCompat.getColor(this, if (isOn) R.color.green else R.color.gray))
            }
            2 -> {
                relayImage3.setImageResource(if (isOn) R.drawable.ic_entrada_on else R.drawable.ic_entrada_off)
                relayStatus3.text = if (isOn) "Encendido" else "Apagado"
                relayStatus3.setTextColor(ContextCompat.getColor(this, if (isOn) R.color.green else R.color.gray))
            }
        }
    }

    private fun setupVoiceInput() {
        voiceInputLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val command = result?.get(0)?.lowercase(Locale.getDefault()) ?: ""
                handleVoiceCommand(command)
            }
        }
    }

    private fun startVoiceInput() {
        if (!packageManager.queryIntentActivities(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
                PackageManager.MATCH_DEFAULT_ONLY
            ).isEmpty()) {

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Di un comando. Ejemplo: 'Encender sala'")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            }

            try {
                voiceInputLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                showSnackbar("Reconocimiento de voz no disponible")
                Log.e(TAG, "Reconocimiento de voz no disponible", e)
            }
        } else {
            showSnackbar("Instala Google Voice Search para usar esta función")
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.voicesearch")))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.voicesearch")))
            }
        }
    }

    private fun handleVoiceCommand(command: String) {
        when {
            command.contains("encender todo") -> toggleAllRelays(true)
            command.contains("apagar todo") -> toggleAllRelays(false)
            command.contains("encender sala") -> toggleSingleRelay(0, true)
            command.contains("apagar sala") -> toggleSingleRelay(0, false)
            command.contains("encender cocina") -> toggleSingleRelay(1, true)
            command.contains("apagar cocina") -> toggleSingleRelay(1, false)
            command.contains("encender entrada") -> toggleSingleRelay(2, true)
            command.contains("apagar entrada") -> toggleSingleRelay(2, false)
            command.contains("encender sala y cocina") -> toggleMultipleRelays(listOf(0, 1), true)
            command.contains("apagar sala y cocina") -> toggleMultipleRelays(listOf(0, 1), false)
            command.contains("encender sala y entrada") -> toggleMultipleRelays(listOf(0, 2), true)
            command.contains("apagar sala y entrada") -> toggleMultipleRelays(listOf(0, 2), false)
            command.contains("encender cocina y entrada") -> toggleMultipleRelays(listOf(1, 2), true)
            command.contains("apagar cocina y entrada") -> toggleMultipleRelays(listOf(1, 2), false)
            else -> showSnackbar("Comando no reconocido")
        }
    }

    private fun toggleSingleRelay(relayPin: Int, state: Boolean) {
        val imageView = when (relayPin) {
            0 -> relayImage1
            1 -> relayImage2
            2 -> relayImage3
            else -> return
        }

        imageView.isEnabled = false
        NetworkUtils.sendLightCommand(this, relayPin, if (state) "on" else "off") { success ->
            runOnUiThread {
                if (success) {
                    verifyActualRelayState(relayPin)
                } else {
                    showSnackbar("Error al cambiar estado")
                    updateRelayUI(relayPin, !state)
                }
                imageView.isEnabled = true
            }
        }
    }

    private fun toggleAllRelays(state: Boolean) {
        listOf(relayImage1, relayImage2, relayImage3).forEach { it.isEnabled = false }

        NetworkUtils.sendMultipleLightCommands(this, listOf(0, 1, 2), if (state) "on" else "off") { success ->
            runOnUiThread {
                if (success) {
                    listOf(0, 1, 2).forEach { verifyActualRelayState(it) }
                    showSnackbar("Todos los relés ${if (state) "encendidos" else "apagados"}")
                } else {
                    showSnackbar("Error al ${if (state) "encender" else "apagar"} todos los relés")
                    listOf(0, 1, 2).forEach { updateRelayUI(it, loadRelayState(it)) }
                }
                listOf(relayImage1, relayImage2, relayImage3).forEach { it.isEnabled = true }
            }
        }
    }

    private fun toggleMultipleRelays(relayPins: List<Int>, state: Boolean) {
        relayPins.forEach { pin ->
            when (pin) {
                0 -> relayImage1.isEnabled = false
                1 -> relayImage2.isEnabled = false
                2 -> relayImage3.isEnabled = false
            }
        }

        NetworkUtils.sendMultipleLightCommands(this, relayPins, if (state) "on" else "off") { success ->
            runOnUiThread {
                if (success) {
                    relayPins.forEach { verifyActualRelayState(it) }
                    val relayNames = relayPins.joinToString(", ") { getRelayName(it) }
                    showSnackbar("Relés ($relayNames) ${if (state) "encendidos" else "apagados"}")
                } else {
                    showSnackbar("Error al cambiar relés")
                    relayPins.forEach { updateRelayUI(it, loadRelayState(it)) }
                }

                relayPins.forEach { pin ->
                    when (pin) {
                        0 -> relayImage1.isEnabled = true
                        1 -> relayImage2.isEnabled = true
                        2 -> relayImage3.isEnabled = true
                    }
                }
            }
        }
    }

    private fun setupSensorListener() {
        val deviceId = sharedPreferences.getString("esp32_id", "") ?: return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        sensorStateListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isInitialLoad) {
                    isInitialLoad = false
                    return
                }

                val isActive = snapshot.child("sensor").getValue(Boolean::class.java) ?: return
                runOnUiThread {
                    showSnackbar(if (isActive) "Sensor activado" else "Sensor desactivado")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error en listener: ${error.message}")
            }
        }

        FirebaseDatabase.getInstance().getReference("users")
            .child(userId)
            .child("devices")
            .child(deviceId)
            .child("status")
            .addValueEventListener(sensorStateListener)
    }

    private fun saveRelayState(relayPin: Int, state: Boolean) {
        with(sharedPreferences.edit()) {
            putBoolean("relay_${relayPin}_state", state)
            apply()
        }
    }

    private fun saveRelayStates(states: List<Boolean>) {
        with(sharedPreferences.edit()) {
            putBoolean("relay_0_state", states[0])
            putBoolean("relay_1_state", states[1])
            putBoolean("relay_2_state", states[2])
            apply()
        }
    }

    private fun loadRelayState(relayPin: Int): Boolean {
        return sharedPreferences.getBoolean("relay_${relayPin}_state", false)
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun getRelayName(relayPin: Int): String {
        return when (relayPin) {
            0 -> "Sala"
            1 -> "Cocina"
            2 -> "Entrada"
            else -> "Desconocido"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val deviceId = sharedPreferences.getString("esp32_id", "") ?: return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseDatabase.getInstance().getReference("users")
            .child(userId)
            .child("devices")
            .child(deviceId)
            .child("status")
            .removeEventListener(sensorStateListener)
    }
}