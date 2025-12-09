plugins {
    id("com.android.application")
}

android {
    namespace = "applane.knob"
    compileSdk = 36
    defaultConfig {
        applicationId = "applane.knob"
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = versionCode.toString()
        setProperty("archivesBaseName", "knob-$versionName")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.0.0")
    implementation("androidx.legacy:legacy-support-v13:1.0.0")
    implementation("androidx.media:media:1.7.0")
}