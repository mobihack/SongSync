import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.parcelize)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "pl.lambada.songsync"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.lambada.songsync"
        minSdk = 21
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 433
        versionName = "4.3.3"

        vectorDrawables {
            useSupportLibrary = true
        }

        // Optional provider credentials, read from local.properties (not committed).
        // Providers whose credential is blank are hidden in the app.
        val localProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "GENIUS_TOKEN", "\"${localProps.getProperty("genius.token", "")}\"")
        buildConfigField("String", "PETITLYRICS_CLIENT_ID", "\"${localProps.getProperty("petitlyrics.clientId", "")}\"")
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
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    composeCompiler {
        // Default from Kotlin 2.0.20; lets composables with unstable params (e.g. Uri) skip.
        enableStrongSkippingMode = true
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
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.compose.ui:ui:1.0.5")
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.material3)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.coil.compose)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.preference)
    implementation(libs.ktor.core)
    implementation(libs.ktor.cio)
    implementation(libs.taglib)
    implementation(libs.kotlin.onetimepassword)
    implementation(libs.datastore.preferences)
    implementation(libs.ui.tooling) //NOT RECOMMENDED
    implementation(libs.ui.tooling.preview) //NOT RECOMMENDED
    implementation("io.ktor:ktor-client-core:2.3.4")
    implementation("io.ktor:ktor-client-cio:2.3.4")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.4")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.4")

    testImplementation(libs.bundles.unit.test)
    // Compose UI tests on the JVM (Robolectric), no device needed
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.compose.ui.test.manifest)
}
