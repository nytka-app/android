plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

/** 1.2.3 becomes 10203; release-please keeps versionName current. */
fun versionCodeOf(version: String): Int {
    val (major, minor, patch) = version.split(".").map { it.toInt() }
    return (major * 10_000 + minor * 100 + patch).coerceAtLeast(1)
}

val appVersion = "0.26.3" // x-release-please-version

android {
    namespace = "io.github.nytka_app"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.nytka_app"
        minSdk = 31
        targetSdk = 37
        versionName = appVersion
        versionCode = versionCodeOf(appVersion)
    }

    // The signing-block blob that AGP adds by default is an opaque binary to F-Droid's scanner and
    // IzzyOnDroid, and it breaks byte-for-byte reproducible builds.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    flavorDimensions += "store"
    productFlavors {
        create("oss") { dimension = "store" }
        create("play") {
            dimension = "store"
            // Only the on-device recogniser is native. Phones are arm64; other ABIs install without it.
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    // Release signing comes from the environment (CI decodes the keystore from a secret);
    // without it, release builds stay unsigned and cannot be installed, which is the point.
    val keystore = System.getenv("NYTKA_KEYSTORE_FILE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("NYTKA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NYTKA_KEY_ALIAS")
                keyPassword = System.getenv("NYTKA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    // sherpa-onnx's JNI library needs only ONNX Runtime; its C and C++ APIs are for other callers (about 5 MB).
    packaging {
        jniLibs { excludes += setOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so") }
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(project(":pendant"))
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.work.runtime)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.zxing.android.embedded)
    // A prebuilt native library, which F-Droid does not build from source: the play flavor only (README).
    "playImplementation"(libs.sherpa.onnx)
    // Activity transitions come from Google Play services, which F-Droid does not ship: the play flavor only.
    "playImplementation"(libs.play.services.location)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.glance.appwidget.testing)
}

// F-Droid and IzzyOnDroid refuse Google's closed-source libraries. The oss flavor must resolve none of them;
// scripts/check-oss-apk.py checks the built APK as well.
val checkOssNoGoogleServices =
    tasks.register("checkOssNoGoogleServices") {
        group = "verification"
        description = "Fails if the oss release runtime classpath holds Google Play services, Firebase or similar."
        val ossRuntime = configurations.named("ossReleaseRuntimeClasspath")
        val root = ossRuntime.flatMap { it.incoming.resolutionResult.rootComponent }
        val banned =
            listOf(
                "com.google.android.gms",
                "com.google.android.play",
                "com.google.firebase",
                "com.google.mlkit",
                "com.android.billingclient",
            )
        doLast {
            val seen = mutableSetOf<ResolvedComponentResult>()

            fun walk(c: ResolvedComponentResult) {
                if (!seen.add(c)) return
                c.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { walk(it.selected) }
            }
            walk(root.get())
            val hits = seen.mapNotNull { it.moduleVersion }.filter { m -> banned.any { m.group.startsWith(it) } }
            check(hits.isEmpty()) { "The oss flavor depends on Google services: ${hits.joinToString()}" }
        }
    }

tasks.named("check") { dependsOn(checkOssNoGoogleServices) }
