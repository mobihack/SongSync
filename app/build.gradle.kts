import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.serialization)
    alias(libs.plugins.parcelize)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "pl.lambada.songsync"
    compileSdk = 37

    defaultConfig {
        applicationId = "me.mobihack.songsyncplus"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 434
        versionName = "4.3.4"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Optional provider credentials, read from local.properties (not committed), or from
        // environment variables in CI. Providers whose credential is blank are hidden in the app.
        val localProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        fun credential(property: String, env: String) =
            localProps.getProperty(property)?.takeIf { it.isNotBlank() } ?: System.getenv(env).orEmpty()
        buildConfigField("String", "GENIUS_TOKEN", "\"${credential("genius.token", "PROVIDER_GENIUS_TOKEN")}\"")
        buildConfigField("String", "PETITLYRICS_CLIENT_ID", "\"${credential("petitlyrics.clientId", "PROVIDER_PETITLYRICS_CLIENT_ID")}\"")
    }
    androidResources {
        generateLocaleConfig = true
    }
    signingConfigs {
        create("release") {
            if (System.getenv("RELEASE_STORE_FILE") != null) {
                storeFile = file(System.getenv("RELEASE_STORE_FILE"))
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        // Own package id, so a debug build installs beside the release one instead of clashing
        // with its signature
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (System.getenv("RELEASE_STORE_FILE") != null) {
                signingConfig = signingConfigs["release"]
            }
        }
        // Release-like build (minified, not debuggable) signed with the debug key, so it can be
        // installed over a debug build to judge real scrolling performance.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            matchingFallbacks += listOf("release")
            isDebuggable = false
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
    testOptions {
        unitTests.all { it.testLogging { events("passed", "skipped", "failed") } }
        // android.util.Log etc. become no-ops instead of throwing in JVM unit tests
        unitTests.isReturnDefaultValues = true
        // Robolectric Compose UI tests read the app's strings and theme
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.coil.compose)
    implementation(libs.haze)
    implementation(libs.haze.materials)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.preference)
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.taglib)
    implementation(libs.kotlin.onetimepassword)
    implementation(libs.datastore.preferences)
    implementation(libs.ui.tooling) //NOT RECOMMENDED
    implementation(libs.ui.tooling.preview) //NOT RECOMMENDED

    testImplementation(libs.bundles.unit.test)
    // Compose UI tests on the JVM (Robolectric), no device needed
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.compose.ui.test.manifest)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}
