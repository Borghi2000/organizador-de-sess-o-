import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// A chave de assinatura fica versionada no repositorio para que cada APK novo seja reconhecido
// pelo Android como continuacao do anterior, permitindo instalar por cima sem perder os dados.
val propriedadesDaChave = Properties().apply {
    val arquivo = rootProject.file("keystore/keystore.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

android {
    namespace = "br.com.borghi.estoquechocolate"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.borghi.estoquechocolate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (propriedadesDaChave.isNotEmpty()) {
            create("estoque") {
                storeFile = rootProject.file("keystore/${propriedadesDaChave.getProperty("storeFile")}")
                storePassword = propriedadesDaChave.getProperty("storePassword")
                keyAlias = propriedadesDaChave.getProperty("keyAlias")
                keyPassword = propriedadesDaChave.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("estoque")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("br.com.borghi.estoquechocolate:core:1.0.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Camera e reconhecimento rodam no aparelho: sem rede, sem custo por foto, sem latencia.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.texto)

    // So para o aviso diario de manha.
    implementation(libs.androidx.work.runtime)

    // Camada 2 do reconhecimento, opcional e desligada por padrao: so e usada se voce ligar a
    // ajuda por IA em Ajustes e colar sua propria chave.
    implementation(libs.anthropic.java)
}
