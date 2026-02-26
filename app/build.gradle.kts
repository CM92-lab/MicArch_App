// build.gradle.kts
plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.example.cryobank"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.cryobank"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/versions/**"
        }
    }
}

kotlin {

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.stdlib.v190)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")

    // AndroidX & Material
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.core.ktx.v1170)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.activity:activity-ktx:1.12.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    // Barcode & ML Kit
    implementation(libs.core)
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation(libs.camera.mlkit.vision)

    // Apache POI für Excel
    implementation("org.apache.poi:poi:5.5.1")
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("org.apache.xmlbeans:xmlbeans:5.3.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.apache.commons:commons-collections4:4.5.0")

    // CSV
    implementation(libs.opencsv.v5120)
    implementation(libs.opencsv)

    // Logging
    implementation(libs.timber)

    // OpenCV Module
    implementation(fileTree("dir" to "libs", "include" to listOf("*.jar")))
    implementation(project(":opencv"))

    // Multidex
    implementation(libs.androidx.multidex)

    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")

    // CameraX
    implementation("androidx.camera:camera-lifecycle:1.5.2")
    implementation("androidx.camera:camera-view:1.5.2")
    implementation("androidx.camera:camera-core:1.5.2")
    implementation("androidx.camera:camera-camera2:1.5.2")
    implementation("androidx.camera:camera-extensions:1.5.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Accompanist Permissions
    implementation(libs.accompanistPermissions)
}
