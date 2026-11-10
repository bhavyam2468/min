plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.bhavyam.min"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.bhavyam.min"
        // 31: RenderEffect (launch blur) and WindowInsetsAnimation (keyboard sync) are framework APIs from here on.
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        // Fonts are memory-mapped straight out of the APK.
        noCompress += "ttf"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
