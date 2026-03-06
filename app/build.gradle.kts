plugins {
    id("com.android.application")
}

android {
    namespace = "com.omersusin.storagefixer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.omersusin.storagefixer"
        minSdk = 34
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
