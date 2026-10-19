import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val firebaseEnabled = providers.gradleProperty("VG_FIREBASE_ENABLED").orElse("false").get().toBooleanStrict()
if (firebaseEnabled) {
    check(file("google-services.json").isFile) { "Supply private google-services.json before enabling accounts" }
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Keep this aligned with the public cloud endpoint used by the PWA in site/app.js.
// Override with -PVG_API_BASE_URL=https://your-service.onrender.com for a different deployment.
// The API is mounted at the service root (/api/formats, /api/download-jobs); do not append /api/v1.
val configuredApi = providers.gradleProperty("VG_API_BASE_URL").orElse("https://vidgravity.onrender.com").get()
val safeApi = configuredApi.trim().trimEnd('/').replace("\\", "\\\\").replace("\"", "\\\"")
android {
    namespace = "com.vidgravity.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vidgravity.app"
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        targetSdk = 35
        versionCode = 188
        versionName = "1.9.0-sprint10"
        buildConfigField("String", "VG_API_BASE_URL", "\"$safeApi\"")
    }
    sourceSets.getByName("main").java.srcDir(if (firebaseEnabled) "src/firebase/java" else "src/guest/java")
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    if (firebaseEnabled) {
        // Reuse #64's validated Kotlin 2.0.21 compatibility baseline; SDK upgrades require a toolchain audit.
        implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
        implementation("com.google.firebase:firebase-auth")
        implementation("com.google.firebase:firebase-analytics")
        implementation("com.google.firebase:firebase-crashlytics")
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.6")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.6")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.runtime:runtime-livedata:1.7.6")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-video:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.material:material:1.12.0")
}
// Resolved guest/configured runtime coordinates, including transitive dependencies.
// License metadata is reported separately; absent/inherited POM licenses remain unknown.
tasks.register("writeRuntimeCoordinates") {
    doLast {
        val coordinates=configurations.getByName("debugRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
            .map {it.moduleVersion.id.toString()}.distinct().sorted()
        val out=layout.buildDirectory.file("reports/dependencies/runtime-coordinates.txt").get().asFile
        out.parentFile.mkdirs();out.writeText(coordinates.joinToString("\n",postfix="\n"))
        val components=configurations.getByName("debugRuntimeClasspath").incoming.resolutionResult.allComponents
            .map {it.id}.filterIsInstance<ModuleComponentIdentifier>()
        val poms=dependencies.createArtifactResolutionQuery().forComponents(components)
            .withArtifacts(MavenModule::class.java,MavenPomArtifact::class.java).execute()
        poms.resolvedComponents.forEach {component ->
            val id=component.id as? ModuleComponentIdentifier ?: return@forEach
            component.getArtifacts(MavenPomArtifact::class.java).filterIsInstance<ResolvedArtifactResult>().forEach {artifact ->
                val target=layout.buildDirectory.file("reports/dependencies/poms/${id.group}/${id.module}/${id.version}/resolved/${id.module}.pom").get().asFile
                target.parentFile.mkdirs();artifact.file.copyTo(target,overwrite=true)
            }
        }
    }
}
