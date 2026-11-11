import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val releaseKeystore = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }
android {
    namespace = "com.a3322505a.guitarlearning"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.a3322505a.guitarlearning"
        manifestPlaceholders["appLabel"] = "吉他练习"
        minSdk = 26
        targetSdk = 35
        versionCode = 124
        versionName = "1.4.14"
    }
    signingConfigs {
        if (hasReleaseSigning) create("official") {
            storeFile = file(releaseKeystore!!)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "吉他练习·调试"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("official")
        }
        create("compare") {
            initWith(getByName("release"))
            signingConfig = null
            applicationIdSuffix = ".compare"
            versionNameSuffix = "-compare"
            manifestPlaceholders["appLabel"] = "吉他练习·对照"
            isDebuggable = false
            matchingFallbacks += "release"
        }
        create("standalone") {
            initWith(getByName("release"))
            signingConfig = null
            applicationIdSuffix = ".standalone"
            versionNameSuffix = "-standalone"
            manifestPlaceholders["appLabel"] = "吉他练习·新版"
            isDebuggable = false
            matchingFallbacks += "release"
        }

    }
    applicationVariants.all {
        if (buildType.name == "release") outputs.all {
            (this as BaseVariantOutputImpl).outputFileName =
                "GuitarPractice-${versionName}${if (hasReleaseSigning) "" else "-unsigned"}.apk"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            it.testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation("net.alphatab:alphaTab:1.8.4")
    constraints {
        implementation("androidx.annotation:annotation:1.9.1")
        listOf("lifecycle-common", "lifecycle-common-java8", "lifecycle-runtime", "lifecycle-runtime-ktx", "lifecycle-runtime-compose", "lifecycle-viewmodel", "lifecycle-viewmodel-ktx", "lifecycle-viewmodel-savedstate", "lifecycle-process").forEach {
            implementation("androidx.lifecycle:$it:2.8.7")
        }
    }
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
