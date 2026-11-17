plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------
// Lee el archivo .env de la raíz del proyecto (si existe).
// Formato: CLAVE=valor  (una por línea, # para comentarios).
// Se lee a mano para que las rutas de Windows con "\" funcionen.
// ---------------------------------------------------------------
val archivoEnv = rootProject.file(".env")
val env: Map<String, String> =
    if (!archivoEnv.exists()) emptyMap()
    else archivoEnv.readLines(Charsets.UTF_8)
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate {
            val i = it.indexOf('=')
            it.substring(0, i).trim() to it.substring(i + 1).trim().removeSurrounding("\"")
        }

fun envOVacio(clave: String): String = env[clave].orEmpty()

val firmaLista = listOf("FIRMA_ARCHIVO", "FIRMA_CLAVE_ALMACEN", "FIRMA_ALIAS", "FIRMA_CLAVE_LLAVE")
    .all { envOVacio(it).isNotEmpty() } && file(envOVacio("FIRMA_ARCHIVO")).exists()

android {
    namespace = "com.mystreamapp.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mystreamapp.app"
        minSdk = 30
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Clave de TMDB (opcional). Si no está en .env, se puede poner dentro de la app en Ajustes.
        val tmdb = envOVacio("TMDB_API_KEY").replace("\\", "").replace("\"", "")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdb\"")
    }

    signingConfigs {
        create("release") {
            if (firmaLista) {
                storeFile = file(envOVacio("FIRMA_ARCHIVO"))
                storePassword = envOVacio("FIRMA_CLAVE_ALMACEN")
                keyAlias = envOVacio("FIRMA_ALIAS")
                keyPassword = envOVacio("FIRMA_CLAVE_LLAVE")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            // Si no hay .env con la firma, compila sin firmar.
            if (firmaLista) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Reproductor de video (HLS, DASH, MP4)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
