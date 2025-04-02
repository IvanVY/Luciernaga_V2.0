pluginManagement {
    repositories {
        google() // Repositorio de plugins de Google
        mavenCentral() // Repositorio central de Maven
        gradlePluginPortal() // Portal oficial de plugins de Gradle
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // Evita repositorios duplicados en módulos
    repositories {
        google() // Repositorio de dependencias de Google
        mavenCentral() // Repositorio central de Maven
    }
}

rootProject.name = "Luciernaga" // Nombre del proyecto
include(":app") // Incluye el módulo principal "app"