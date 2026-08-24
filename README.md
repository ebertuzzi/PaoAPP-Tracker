# PaoPR Tracker v1.0

Proyecto Android nativo Kotlin/Jetpack Compose.

## Archivos de build corregidos
- `build.gradle.kts`: versiones de Android Gradle Plugin y Kotlin.
- `app/build.gradle.kts`: SDK, Java/Kotlin 17 y dependencias, incluyendo Material Icons.
- `settings.gradle.kts`: repositorios y módulo `app`.
- `gradle.properties`: AndroidX/Gradle settings.
- `app/src/main/AndroidManifest.xml`: aplicación y actividad launcher correctamente declaradas.
- `gradle/wrapper/gradle-wrapper.properties`: versión Gradle 8.7 para entornos que usen wrapper.

## Nota sobre el warning del wrapper
El ZIP no incluye `gradle-wrapper.jar` ni los scripts `gradlew/gradlew.bat`. Si la plataforma ofrece su propio Gradle, puede compilar sin ellos.

Application ID: `com.paopr.tracker`
