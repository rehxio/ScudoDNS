import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Clave de firma propia. Se guarda FUERA del repositorio (por defecto en ~/.scudodns/keystore.properties,
// que crea crear-clave.bat) para que nunca acabe en GitHub. Sin ella se firma con la clave de pruebas.
val releaseKey = Properties().apply {
    val path = System.getenv("SCUDO_KEYSTORE_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.scudodns/keystore.properties"
    val f = file(path)
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "es.rehxio.scudo"
    compileSdk = 35

    defaultConfig {
        applicationId = "es.rehxio.scudo"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "0.9.1"

        // Enlaces de "Acerca de". El de donaciones se configura en gradle.properties (scudo.donateUrl);
        // si está vacío, la sección de donativos no aparece en la app.
        buildConfigField("String", "REPO_URL", "\"https://github.com/rehxio/ScudoDNS\"")
        buildConfigField("String", "DONATE_URL", "\"${project.findProperty("scudo.donateUrl") ?: ""}\"")
    }

    signingConfigs {
        if (!releaseKey.isEmpty()) create("release") {
            storeFile = file(releaseKey.getProperty("storeFile"))
            storePassword = releaseKey.getProperty("storePassword")
            keyAlias = releaseKey.getProperty("keyAlias")
            keyPassword = releaseKey.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (!releaseKey.isEmpty()) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("AVISO: sin clave propia; el APK se firma con la clave de pruebas (ejecuta crear-clave.bat)")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
