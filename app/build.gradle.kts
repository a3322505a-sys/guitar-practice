plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val tabletReview = providers.gradleProperty("tabletReview").map { it.toBoolean() }.getOrElse(false)
android {
    namespace = "com.a3322505a.guitarlearning"
    compileSdk = 36
    defaultConfig {
        applicationId = if (tabletReview) "com.a3322505a.guitarlearning.tablet98" else "com.a3322505a.guitarlearning"
        manifestPlaceholders["appLabel"] = if (tabletReview) "吉他练习·联动验收" else "吉他练习"
        minSdk = 26
        targetSdk = 35
        versionCode = 103
        versionName = "1.0.1"
    }
    buildTypes {
        release { isMinifyEnabled = false; isShrinkResources = false }
        create("p0") {
            initWith(getByName("release"))
            isDebuggable = true
            matchingFallbacks += "release"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("debug").assets.srcDir(rootProject.file("assets/soundfont-source"))
    sourceSets.getByName("p0").assets.srcDir(rootProject.file("work/p0-assets"))
    testOptions { unitTests.isIncludeAndroidResources = true }
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
