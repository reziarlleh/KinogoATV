import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stableSigningFile = rootProject.file(
    providers.gradleProperty("KINOGO_SIGNING_STORE_FILE")
        .orElse(".signing/kinogo-tv-dev.keystore")
        .get(),
)
val stableSigningAvailable = stableSigningFile.isFile
val packagedUpdateManifestUrls = providers.gradleProperty("KINOGO_UPDATE_MANIFEST_URLS")
    .orElse("")
    .get()
val escapedPackagedUpdateManifestUrls = packagedUpdateManifestUrls
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "\\r")
    .replace("\n", "\\n")
    .replace("\t", "\\t")

if (!stableSigningAvailable) {
    logger.warn(
        "Stable Kinogo signing key is unavailable. Debug builds will use the standard " +
            "Android debug key and cannot update stable-signed installations.",
    )
}

android {
    namespace = "com.kinogo.atv"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.kinogo.atv"
        minSdk {
            version = release(28)
        }
        targetSdk {
            version = release(37)
        }
        versionCode = 15
        versionName = "0.5.1"

        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URLS",
            "\"$escapedPackagedUpdateManifestUrls\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    val stableSigningConfig = if (stableSigningAvailable) {
        signingConfigs.create("stable") {
            storeFile = stableSigningFile
            storePassword = providers.gradleProperty("KINOGO_SIGNING_STORE_PASSWORD")
                .orElse("android")
                .get()
            keyAlias = providers.gradleProperty("KINOGO_SIGNING_KEY_ALIAS")
                .orElse("androiddebugkey")
                .get()
            keyPassword = providers.gradleProperty("KINOGO_SIGNING_KEY_PASSWORD")
                .orElse("android")
                .get()
        }
    } else {
        null
    }

    buildTypes {
        debug {
            stableSigningConfig?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            stableSigningConfig?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "256m"
    jvmArgs("-Xss256k", "-XX:ActiveProcessorCount=1")
}

if (!stableSigningAvailable) {
    tasks.configureEach {
        if (name in setOf("packageRelease", "assembleRelease", "bundleRelease", "installRelease")) {
            doFirst {
                throw GradleException(
                    "A release requires the stable Kinogo signing key. Restore it from a secure " +
                        "backup or set KINOGO_SIGNING_STORE_FILE.",
                )
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation("androidx.paging:paging-runtime-ktx:3.5.0")
    implementation("androidx.paging:paging-compose:3.5.0")

    implementation("androidx.media3:media3-common:1.10.1")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.media3:media3-ui-compose-material3:1.10.1")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("org.jsoup:jsoup:1.22.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.paging:paging-common:3.5.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
