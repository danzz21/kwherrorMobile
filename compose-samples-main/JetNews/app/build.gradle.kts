import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose)
    // ✅ BENAR - tidak perlu kapt
}

android {
    namespace = "com.danzz.kwhmeter"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.danzz.kwhmeter"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }

        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ✅ Tidak perlu composeOptions - BOM sudah handle

    packaging {
        resources {
            excludes += "/META-INF/AL2.0"
            excludes += "/META-INF/LGPL2.1"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    // ===== DESUGARING =====
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // ===== COMPOSE BOM =====
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // ===== COMPOSE CORE =====
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // ===== LIVE DATA =====
    implementation("androidx.compose.runtime:runtime-livedata")
    
    // ===== MATERIAL =====
    implementation("com.google.android.material:material:1.11.0")

    // ===== LIFECYCLE & VIEWMODEL =====
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // ===== CORE KTX =====
    implementation("androidx.core:core-ktx:1.12.0")

    // ===== DATA STORE =====
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ===== COROUTINES =====
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ===== JSON PARSING =====
    implementation("com.google.code.gson:gson:2.10.1")

    // ===== PERMISSIONS (OPTIONAL) =====
    // Untuk Android 13+ permissions handling yang lebih baik
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // ===== PDF & FILE SHARING =====
    // Native Android PDF sudah include, tidak perlu library tambahan
    // Tapi butuh untuk file provider
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // ===== TESTING =====
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    
    // ===== DEBUG =====
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}