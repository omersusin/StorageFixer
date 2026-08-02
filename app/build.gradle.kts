plugins {
    id("com.android.application")
}

/*
 * Semantic Versioning — single source of truth is the Git tag.
 *
 * Local builds:
 *   versionName = "0.0.0-dev"
 *   versionCode = 1
 *
 * CI release builds (tag vX.Y.Z):
 *   versionName = "X.Y.Z"   (from GITHUB_REF)
 *   versionCode = GITHUB_RUN_NUMBER
 */

val semVerRegex = Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+$")

fun resolveVersionName(): String {
    val ref = System.getenv("GITHUB_REF") ?: return "0.0.0-dev"
    // GITHUB_REF looks like "refs/tags/v1.2.3"
    val tag = ref.substringAfterLast("/")
    return if (semVerRegex.matches(tag)) tag.removePrefix("v") else "0.0.0-dev"
}

fun resolveVersionCode(): Int {
    val runNumber = System.getenv("GITHUB_RUN_NUMBER")
    return runNumber?.toIntOrNull() ?: 1
}

android {
    namespace = "com.omersusin.storagefixer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omersusin.storagefixer"
        minSdk = 34
        targetSdk = 35
        versionCode = resolveVersionCode()
        versionName = resolveVersionName()
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("SIGNING_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this
            if (output is com.android.build.gradle.api.ApkVariantOutput) {
                val appName = "StorageFixer"
                val verName = variant.versionName
                output.outputFileName = "${appName}-v${verName}.apk"
            }
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    compileOnly("de.robv.android.xposed:api:82")
}
