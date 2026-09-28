plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.englishquiz.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.englishquiz.app"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Supabase Edge Function URL. It is deployment configuration, not a secret, and stays
        // out of the repository: pass -PaiEndpoint=... or set it in a local gradle.properties.
        // An empty value leaves the AI features switched off instead of calling a wrong host.
        val aiEndpoint = providers.gradleProperty("aiEndpoint").getOrElse("")
        val endpointPattern = Regex("""^https://[A-Za-z0-9.-]+(:\d+)?(/[A-Za-z0-9._~/-]*)?$""")
        require(aiEndpoint.isEmpty() || endpointPattern.matches(aiEndpoint)) {
            "aiEndpoint must be a plain https URL with no query string: a token in the URL would " +
                "ship inside the APK. Secrets belong in the Edge Function, never in the app."
        }
        buildConfigField("String", "AI_ENDPOINT", "\"$aiEndpoint\"")
    }

    // CI signs the debug APK with the PC's own debug keystore (passed as -PdebugKeystore=...) so a
    // build from GitHub installs over the one on the phone instead of failing on a signature
    // mismatch. Without the property the build type keeps Gradle's default debug key.
    providers.gradleProperty("debugKeystore").orNull?.let { keystore ->
        signingConfigs.getByName("debug") {
            storeFile = file(keystore)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
        compose = true
    }

    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    constraints {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json") {
            version { strictly("1.8.1") }
        }
    }
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.room:room-ktx:2.8.5")
    implementation("androidx.room:room-runtime:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
