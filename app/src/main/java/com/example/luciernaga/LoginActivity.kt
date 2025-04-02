package com.example.luciernaga

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.registerForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    // Instancias de Firebase
    private lateinit var auth: FirebaseAuth // Para autenticación
    private lateinit var firestore: FirebaseFirestore // Para base de datos Firestore
    private lateinit var oneTapClient: SignInClient // Cliente para inicio de sesión con Google
    private lateinit var signInRequest: BeginSignInRequest // Solicitud de inicio de sesión
    private lateinit var sharedPreferences: SharedPreferences // Para guardar preferencias

    // Códigos de solicitud
    companion object {
        private const val TAG = "LoginActivity" // Etiqueta para logs
    }

    // Registro de actividad para manejar el resultado del inicio de sesión
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            // Obtener credenciales del intent resultante
            val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
            val idToken = credential.googleIdToken
            when {
                idToken != null -> authenticateWithFirebase(idToken) // Autenticar con Firebase si hay token
                else -> {
                    Log.w(TAG, "No se encontró token de ID")
                    showError("No se pudo obtener el token de Google")
                }
            }
        } catch (e: ApiException) {
            Log.w(TAG, "Error en inicio de sesión con un toque", e)
            showError("Error de inicio de sesión: ${e.statusCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inicializar Shared Preferences
        sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        // Inicializar componentes de Firebase
        initializeFirebase()
        setupGoogleSignIn()
        setupSignInButton()

        // Verificar si ya está autenticado para auto-login
        checkForAutoLogin()
    }

    // Inicializar componentes de Firebase
    private fun initializeFirebase() {
        auth = Firebase.auth // Instancia de autenticación
        firestore = Firebase.firestore // Instancia de Firestore
    }

    // Configurar inicio de sesión con Google
    private fun setupGoogleSignIn() {
        oneTapClient = Identity.getSignInClient(this) // Cliente de Google
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(   // Solicita token ID
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build())
            .setAutoSelectEnabled(true)  // Auto-selección si hay una sola cuenta
            .build()
    }

    // Configurar botón de inicio de sesión
    private fun setupSignInButton() {
        val signInButton = findViewById<com.google.android.gms.common.SignInButton>(R.id.btnGoogleSignIn)
        signInButton.setSize(SignInButton.SIZE_STANDARD)
        signInButton.setColorScheme(SignInButton.COLOR_LIGHT)
        signInButton.setOnClickListener {
            initiateGoogleSignIn()
        }
    }

    // Iniciar proceso de inicio de sesión con Google
    private fun initiateGoogleSignIn() {
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener { result ->
                try {
                    // Lanzar la actividad de inicio de sesión
                    signInLauncher.launch(IntentSenderRequest.Builder(result.pendingIntent.intentSender).build())
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo iniciar la UI de un toque", e)
                    showError("Error de inicio de sesión")
                }
            }
            .addOnFailureListener { e ->
                Log.d(TAG, "No hay cuentas guardadas", e)
                showError("No se encontraron cuentas guardadas")
            }
    }

    // Autenticar con Firebase usando el token de Google
    private fun authenticateWithFirebase(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    handleAuthSuccess(task.result?.user)
                } else {
                    showError("Autenticación fallida: ${task.exception?.message}")
                }
            }
    }

    // Manejar éxito en autenticación
    private fun handleAuthSuccess(user: FirebaseUser?) {
        user?.let {
            // Guardar estado de autenticación en SharedPreferences
            sharedPreferences.edit().apply {
                putBoolean("is_authenticated", true)
                putString("user_email", user.email)
                apply()
            }

            if (isNewUser(it)) {
                createUserProfile(it)
                showToast("¡Bienvenido nuevo usuario!")
            } else {
                showToast("Inicio de sesión exitoso")
                updateLastLogin(it)
            }
            fetchUserConfiguration()
            navigateToMainActivity()
        } ?: showError("Usuario no disponible")
    }

    // Verificar si es un usuario nuevo
    private fun isNewUser(user: FirebaseUser): Boolean {
        val metadata = user.metadata
        return metadata?.creationTimestamp == metadata?.lastSignInTimestamp
    }

    // Actualizar último inicio de sesión en Firestore
    private fun updateLastLogin(user: FirebaseUser) {
        firestore.collection("users").document(user.uid)
            .update("lastLogin", System.currentTimeMillis())
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al actualizar último login", e)
            }
    }

    // Crear perfil de usuario en Firestore
    private fun createUserProfile(user: FirebaseUser) {
        val userDoc = firestore.collection("users").document(user.uid)
        val userData = hashMapOf(
            "uid" to user.uid,
            "name" to user.displayName,
            "email" to user.email,
            "photoUrl" to user.photoUrl?.toString(),
            "esp32_ip" to "",
            "esp32_id" to "",
            "createdAt" to System.currentTimeMillis(),
            "lastLogin" to System.currentTimeMillis()
        )

        userDoc.set(userData)
            .addOnSuccessListener {
                Log.d(TAG, "Perfil de usuario creado exitosamente")
            }
            .addOnFailureListener { e ->
                showError("Error al crear perfil: ${e.message}")
            }
    }

    // Obtener configuración del usuario desde Firestore
    private fun fetchUserConfiguration() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            firestore.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Guardar IP del ESP32 si existe
                        document.getString("esp32_ip")?.let { ip ->
                            if (ip.isNotEmpty()) {
                                sharedPreferences.edit().putString("esp32_ip", ip).apply()
                                Log.d(TAG, "IP recuperada de Firebase: $ip")
                            }
                        }
                        // Guardar ID del dispositivo si existe
                        document.getString("esp32_id")?.let { id ->
                            if (id.isNotEmpty()) {
                                sharedPreferences.edit().putString("esp32_id", id).apply()
                                Log.d(TAG, "ID de dispositivo recuperado de Firebase: $id")
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error al recuperar configuración de Firebase", e)
                }
        }
    }

    // Navegar a la actividad principal
    private fun navigateToMainActivity() {
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }.also { startActivity(it) }
        finish()// Cierra la pantalla de login
    }

    // Verificar si ya está autenticado para hacer auto-login
    private fun checkForAutoLogin() {
        auth.currentUser?.let {
            fetchUserConfiguration()
            navigateToMainActivity()
        }
    }

    // Mostrar mensaje Toast corto
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Mostrar mensaje de error Toast largo
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}