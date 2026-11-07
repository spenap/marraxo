plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Collects every dependency's licence at build time, for the About screen (offline).
    alias(libs.plugins.aboutlibraries)
}

// Version from git, so every build says exactly what it was built from.
// versionCode must grow with each install: the commit count does.
fun git(vararg args: String) = providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim()

android {
    namespace = "com.simonpena.makofi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.simonpena.makofi"
        minSdk = 27 // Nexus 4 on LineageOS 15.1 (Android 8.1)
        targetSdk = 35
        versionCode = git("rev-list", "--count", "HEAD").toInt()
        versionName = git("describe", "--tags", "--always", "--dirty")
        // The commit's date, not the build time, so rebuilding gives the same APK.
        testInstrumentationRunner = "com.simonpena.makofi.AwakeRunner"
        manifestPlaceholders["appLabel"] = "Makofi"
        buildConfigField("String", "COMMIT_DATE", "\"${git("log", "-1", "--format=%cs")}\"")
    }

    // Tagged releases only (scripts/release.sh): signed with the release key
    // when MAKOFI_KEYSTORE is set. Otherwise release builds use the debug key,
    // as the dev phones expect.
    val keystore = System.getenv("MAKOFI_KEYSTORE")?.let(::file)
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = keystore
            storePassword = System.getenv("MAKOFI_KEYSTORE_PASSWORD")
            keyAlias = "makofi"
            keyPassword = System.getenv("MAKOFI_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // A separate app next to the real one: instrumented tests install
            // and then uninstall it, which must never wipe the real app's
            // play history, queue or hidden list.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Makofi debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // The fake speaker and TV, for JVM and device tests alike.
    sourceSets {
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
    }
    testOptions {
        // Robolectric runs Android classes (Uri, Bundle, SQLite) on the JVM.
        unitTests.isIncludeAndroidResources = true
        // scripts/station.sh: the simulator's options, and its printout.
        unitTests.all { test ->
            project.properties.filterKeys { it.startsWith("station.") }.forEach { (k, v) -> test.systemProperty(k, v.toString()) }
            if (project.hasProperty("station.artist")) test.testLogging.showStandardStreams = true
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.palette)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.guava)
    implementation(libs.aboutlibraries.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}

