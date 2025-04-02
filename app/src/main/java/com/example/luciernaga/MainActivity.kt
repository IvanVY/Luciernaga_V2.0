package com.example.luciernaga

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        if (auth.currentUser == null) {
            redirectToLogin()
            return
        }

        setContentView(R.layout.activity_main)
        initializeComponents()
        setupUI()
    }

    private fun initializeComponents() {
        sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupUI() {
        displayUserInfo()
        setupNavigationButtons()
        checkEsp32Configuration()
    }

    private fun displayUserInfo() {
        val currentUser = auth.currentUser ?: return

        findViewById<TextView>(R.id.txtUserName).text = currentUser.displayName ?: getString(R.string.default_username)

        currentUser.photoUrl?.let { uri ->
            Glide.with(this)
                .load(uri)
                .circleCrop()
                .placeholder(R.drawable.ic_baseline_account_circle_24)
                .error(R.drawable.ic_baseline_account_circle_24)
                .into(findViewById(R.id.imgUser))
        } ?: run {
            findViewById<ImageView>(R.id.imgUser).setImageResource(R.drawable.ic_baseline_account_circle_24)
        }
    }

    private fun setupNavigationButtons() {
        findViewById<MaterialButton>(R.id.btnRelayControl).setOnClickListener {
            navigateIfEsp32Configured(RelayControlActivity::class.java)
        }

        findViewById<MaterialButton>(R.id.btnEventLog).setOnClickListener {
            navigateIfEsp32Configured(EventLogActivity::class.java)
        }

        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener {
            if (isEsp32Configured()) {
                showConfirmationDialog()
            } else {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            performLogout()
        }
    }

    private fun navigateIfEsp32Configured(targetActivity: Class<*>) {
        if (isEsp32Configured()) {
            startActivity(Intent(this, targetActivity))
        } else {
            showConfigurationRequired()
        }
    }

    private fun isEsp32Configured(): Boolean {
        return sharedPreferences.getString("esp32_ip", null)?.isNotEmpty() == true
    }

    private fun showConfigurationRequired() {
        Toast.makeText(this, "Configura la IP del ESP32 primero", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, SettingsActivity::class.java).apply {
            putExtra("first_time_setup", true)
        })
    }

    private fun performLogout() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Estás seguro de que deseas cerrar sesión?")
            .setPositiveButton("Sí") { _, _ ->
                GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                    .addOnCompleteListener {
                        auth.signOut()
                        clearSessionData()
                        redirectToLogin()
                    }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun clearSessionData() {
        sharedPreferences.edit().clear().apply()
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun checkEsp32Configuration() {
        if (!isEsp32Configured()) {
            firestore.collection("users").document(auth.currentUser?.uid ?: return)
                .get()
                .addOnSuccessListener { document ->
                    document.getString("esp32_ip")?.takeIf { it.isNotEmpty() }?.let { ip ->
                        sharedPreferences.edit().putString("esp32_ip", ip).apply()
                    } ?: showConfigurationRequired()
                }
                .addOnFailureListener {
                    showConfigurationRequired()
                }
        }
    }

    private fun showConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cambiar configuración")
            .setMessage("Ya has configurado una IP. ¿Deseas cambiarla?")
            .setPositiveButton("Sí") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java).apply {
                    putExtra("force_config", true)
                })
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onBackPressed() {
        if (!isEsp32Configured()) {
            showConfigurationRequired()
        } else {
            super.onBackPressed()
        }
    }
}