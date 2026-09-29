plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "io.github.nytka_app.pendant"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

detekt {
    config.setFrom(rootProject.file("config/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
}
