plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val configuredApi = providers.gradleProperty("VG_API_BASE_URL").orElse("https://vidgravity.onrender.com").get()
val safeApi = configuredApi.trim().trimEnd('/').replace("\\", "\\\\").replace("\"", "\\\"")
android {
    namespace = "com.vidgravity.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vidgravity.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 174
        versionName = "1.0.4-p174"
        buildConfigField("String", "VG_API_BASE_URL", "\"$safeApi\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
