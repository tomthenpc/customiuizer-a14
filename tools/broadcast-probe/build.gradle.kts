plugins {
    id("com.android.application")
}

android {
    namespace = "tv.withaibuild.customiuzer.broadcastprobe"
    compileSdk = 37

    defaultConfig {
        applicationId = "tv.withaibuild.customiuizer.broadcastprobe"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}
