package com.example.luciernaga

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkUtils {

    private const val TAG = "NetworkUtils"
    private const val DEFAULT_TIMEOUT = 10L // segundos
    private const val DEFAULT_ESP32_IP = "http://192.168.200.102"

    private fun parseRelayStatesFromLog(log: String): List<Boolean> {
        return listOf(
            log.contains("Sala: ON") || log.contains("Relay 0: ON"),
            log.contains("Cocina: ON") || log.contains("Relay 1: ON"),
            log.contains("Entrada: ON") || log.contains("Relay 2: ON")
        )
    }

    fun fetchSensorConfiguration(context: Context, callback: (List<Boolean>) -> Unit) {
        fetchEventLog(context) { log ->
            val config = parseSensorConfigFromLog(log)
            callback(config)
        }
    }


    private fun parseSensorConfigFromLog(log: String): List<Boolean> {
        return listOf(
            log.contains("Sala configurada para sensor") || log.contains("Relay 0 para sensor"),
            log.contains("Cocina configurada para sensor") || log.contains("Relay 1 para sensor"),
            log.contains("Entrada configurada para sensor") || log.contains("Relay 2 para sensor")
        )
    }



    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .build()
    }

    fun getESP32IP(context: Context): String {
        return context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            .getString("esp32_ip", "")?.trim().takeUnless { it.isNullOrEmpty() }
            ?.let { "http://$it" } ?: DEFAULT_ESP32_IP
    }

    fun sendLightCommand(
        context: Context,
        relay: Int,
        state: String,
        callback: (Boolean) -> Unit
    ) {
        val url = "${getESP32IP(context)}/control?relay=$relay&state=$state"
        executeRequest(url, callback)
    }

    // Función NUEVA para selección de relés para sensor
    fun sendRelaySelection(
        context: Context,
        relays: List<Int>,
        callback: (Boolean, String?) -> Unit
    ) {
        val ip = getESP32IP(context)
        if (ip.isEmpty()) {
            callback(false, null)
            return
        }

        val url = "$ip/selectRelays?relays=${relays.joinToString(",")}"
        Log.d(TAG, "Enviando selección de relés: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Error en sendRelaySelection: ${e.message}")
                callback(false, null)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Respuesta de selección: $responseBody")
                callback(response.isSuccessful, responseBody)
                response.close()
            }
        })
    }


    fun sendMultipleLightCommands(
        context: Context,
        relayPins: List<Int>,
        state: String,
        callback: (Boolean) -> Unit
    ) {
        val relaysString = relayPins.joinToString(",")
        val url = "${getESP32IP(context)}/controlMultiple?relays=$relaysString&state=$state"
        executeRequest(url, callback)
    }

    fun fetchEventLog(context: Context, callback: (String) -> Unit) {
        val url = "${getESP32IP(context)}/status?t=${System.currentTimeMillis()}"
        // El parámetro t evita caché
        executeRequest(url, callback, onFailure = { callback("") })
    }

    fun fetchRelayState(context: Context, relayPin: Int, callback: (Boolean) -> Unit) {
        fetchEventLog(context) { log ->
            val state = when (relayPin) {
                0 -> log.contains("Sala: ON") || log.contains("Relay 0: ON")
                1 -> log.contains("Cocina: ON") || log.contains("Relay 1: ON")
                2 -> log.contains("Entrada: ON") || log.contains("Relay 2: ON")
                else -> false
            }
            callback(state)
        }
    }

    fun fetchRelayStates(context: Context, callback: (List<Boolean>) -> Unit) {
        fetchEventLog(context) { log ->
            val states = parseRelayStatesFromLog(log)
            callback(states)
        }
    }

    fun sendGetRequest(url: String, callback: (Boolean) -> Unit) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/plain")
            .header("Connection", "close")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Error en sendGetRequest: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                val success = response.isSuccessful
                Log.d(TAG, "Respuesta de $url - Éxito: $success, Código: ${response.code}")
                response.close() // Cerrar la respuesta explícitamente
                callback(success)
            }
        })
    }

    fun fetchSensorState(context: Context, callback: (Boolean) -> Unit) {
        fetchEventLog(context) { log ->
            val isActive = log.contains("Sensor: ACTIVADO") ||
                    !log.contains("Sensor: DESACTIVADO")
            callback(isActive)
        }
    }



    private fun executeRequest(
        url: String,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit = {}
    ) {
        Log.d(TAG, "Enviando solicitud a: $url")

        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Error en la solicitud: ${e.message}")
                onFailure()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        Log.d(TAG, "Respuesta exitosa: $body")
                        onSuccess(body)
                    } else {
                        Log.e(TAG, "Respuesta no exitosa: ${response.code}")
                        onFailure()
                    }
                }
            }
        })
    }

    private fun executeRequest(url: String, callback: (Boolean) -> Unit) {
        executeRequest(
            url,
            onSuccess = { callback(true) },
            onFailure = { callback(false) }
        )
    }
}