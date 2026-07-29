import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropertiesFile = rootProject.file("../keystore.properties")
val keystoreProperties = Properties()
val hasReleaseSigning = keystorePropertiesFile.isFile
if (hasReleaseSigning) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
} else {
    throw GradleException("Release signing is unavailable: ../keystore.properties was not found.")
}

val lastVersion = 185
val lastVersionName = "r14.13.7"
val supportedLocales = setOf(
    "ru-rRU",
    "zh-rCN",
    "zh-rTW",
    "ja-rJP",
    "vi-rVN",
    "cs-rCZ",
    "pt-rBR",
    "tr-rTR",
    "es-rES",
)

android {
    namespace = "tv.withaibuild.customiuizer"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    signingConfigs {
        if (hasReleaseSigning) {
            create("v2") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = false
                enableV2Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "tv.withaibuild.customiuizer.r14"
        minSdk = 34
        //noinspection OldTargetApi,ExpiredTargetSdkVersion
        targetSdk = 34
        versionCode = lastVersion
        versionName = lastVersionName
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    val releaseSigning = signingConfigs.getByName("v2")
    buildTypes {
        create("develop") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            signingConfig = releaseSigning
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo {
                // Keep release APKs reproducible; the Git revision is represented by the tag.
                include = false
            }
            signingConfig = releaseSigning
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/androidx.*.version",
                "**.kotlin_builtins",
                "**.kotlin_metadata",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        // Supported translations intentionally fall back to the base strings when incomplete.
        warning += "MissingTranslation"
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.androidResources.localeFilters.addAll(supportedLocales)
        variant.outputs.forEach { output ->
            output.outputFileName.set("CustoMIUIzer-A14-$lastVersionName.apk")
        }
    }
}

dependencies {
    compileOnly(files("lib/framework.jar"))
    compileOnly(libs.libxposed.api)
    testImplementation(libs.libxposed.api)

    implementation(libs.libxposed.service)
    implementation(libs.commons.lang3)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.appcompat)
    implementation(libs.dexkit)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit)
}
