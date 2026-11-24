import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// VERSION_NAME lives in gradle.properties and is bumped by scripts/release.sh.
val appVersionName: String = providers.gradleProperty("VERSION_NAME").get()
val appVersionCode: Int = run {
    val match = Regex("""(\d+)\.(\d{1,2})\.(\d{1,2})""").matchEntire(appVersionName)
        ?: error("VERSION_NAME must be X.Y.Z with minor and patch 0-99, was '$appVersionName'")
    val (major, minor, patch) = match.destructured
    major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
}

// Release signing: PM_* environment variables (CI) or a gitignored keystore.properties (local).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}
fun signingValue(key: String): String? =
    providers.environmentVariable(key).orNull?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }
val releaseKeystore: String? = signingValue("PM_KEYSTORE_FILE")

android {
    namespace = "cz.filiprojek.phonemirror"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "cz.filiprojek.phonemirror"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("PM_KEYSTORE_PASSWORD")
                keyAlias = signingValue("PM_KEY_ALIAS")
                keyPassword = signingValue("PM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

// README screenshots (scripts/screenshots.sh) only render with -Pshowcase; normal test runs skip them.
val showcase = providers.gradleProperty("showcase").isPresent
val showcaseDir = rootProject.layout.projectDirectory.dir("docs/screenshots").asFile.absolutePath
tasks.withType<Test>().configureEach {
    if (showcase) {
        filter.includeTestsMatching("*ShowcaseScreenshots*")
        systemProperty("roborazzi.test.record", "true")
        systemProperty("showcase.dir", showcaseDir)
    } else {
        filter.excludeTestsMatching("*ShowcaseScreenshots*")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
