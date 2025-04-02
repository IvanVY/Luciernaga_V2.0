import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application) // Plugin de aplicación Android
    alias(libs.plugins.kotlin.android) // Plugin de Kotlin para Android
    alias(libs.plugins.kotlin.compose) // Plugin de Compose (si es necesario)
    id("com.google.gms.google-services") // Plugin de Google Services para Firebase
}

android {
    namespace = "com.example.luciernaga" // Espacio de nombres único para la aplicación
    compileSdk = 35 // Versión de SDK para compilación

    defaultConfig {
        applicationId = "com.example.luciernaga" // ID único de la aplicación
        minSdk = 23 // Versión mínima de SDK compatible
        targetSdk = 35 // Versión de SDK objetivo
        versionCode = 2 // Código de versión interno
        versionName = "2.0" // Nombre de versión visible al usuario

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" // Runner para pruebas
    }

    buildTypes {
        release {
            isMinifyEnabled = false // Desactiva la ofuscación y minimización en release
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), // Archivo Proguard predeterminado
                "proguard-rules.pro" // Archivo Proguard personalizado
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11 // Compatibilidad con Java 11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11" // Target de JVM para Kotlin
    }

    buildFeatures {
        compose = true // Habilita Jetpack Compose
        viewBinding = true // Habilita ViewBinding
        viewBinding = true
    }
}

dependencies {
    // Dependencias básicas de Android y Kotlin
    implementation(libs.androidx.core.ktx) // Core de Android con extensiones de Kotlin
    implementation(libs.androidx.appcompat) // AppCompat para compatibilidad
    implementation(libs.androidx.constraintlayout) // ConstraintLayout
    implementation(libs.material) // Material Design Components

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom)) // BOM de Compose para gestionar versiones
    implementation(libs.androidx.activity.compose) // Actividad con soporte para Compose
    implementation(libs.androidx.ui) // UI de Compose
    implementation(libs.androidx.ui.graphics) // Gráficos de Compose
    implementation(libs.androidx.ui.tooling.preview) // Herramientas de previsualización de Compose
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity) // Material 3 para Compose
    debugImplementation(libs.androidx.ui.tooling) // Herramientas de debug para Compose
    debugImplementation(libs.androidx.ui.test.manifest) // Manifesto de prueba para Compose

    // Navigation Component
    implementation(libs.androidx.navigation.fragment.ktx) // Navegación con Fragmentos
    implementation(libs.androidx.navigation.ui.ktx) // Navegación con UI

    // Firebase
    implementation(platform(libs.firebase.bom)) // BOM de Firebase para gestionar versiones
    implementation(libs.firebase.firestore) // Firestore de Firebase
    implementation ("com.google.android.gms:play-services-auth:21.3.0") // Versión más reciente

    // Testing
    testImplementation(libs.junit) // Pruebas unitarias con JUnit
    androidTestImplementation(libs.androidx.junit) // Pruebas de instrumentación con JUnit
    androidTestImplementation(libs.androidx.espresso.core) // Pruebas de UI con Espresso
    androidTestImplementation(libs.androidx.ui.test.junit4) // Pruebas de UI de Compose


    // Firebase Authentication
    implementation(libs.firebase.auth)

    // Google Sign-In
    implementation(libs.play.services.auth) // Para GoogleSignIn y GoogleSignInOptions


    implementation(libs.okhttp.v4120)

    implementation(libs.androidx.activity.ktx)

    implementation ("com.github.bumptech.glide:glide:4.13.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.13.0")

    // Firebase Core

    implementation ("com.google.firebase:firebase-database-ktx")

    // Otras dependencias existentes
    implementation ("com.google.firebase:firebase-auth-ktx")

    // Asegúrate de tener también la dependencia de Material Components
    implementation ("com.google.android.material:material:1.6.0")

    implementation (libs.androidx.core.ktx.v180) // Para SplashScreen API
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4") // Para corrutinas
}