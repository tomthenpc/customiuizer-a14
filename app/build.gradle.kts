import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropertiesFile = rootProject.file("../keystore.properties")
val officialRelease = (project.findProperty("officialRelease")?.toString()?.toBoolean() ?: false)

val keystoreProperties = Properties()
if (officialRelease) {
    if (!keystorePropertiesFile.isFile) {
        throw GradleException("officialRelease=true but ../keystore.properties was not found")
    }
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
    val required = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val missing = required.filter { keystoreProperties.getProperty(it).isNullOrEmpty() }
    if (missing.isNotEmpty()) {
        throw GradleException("officialRelease=true but ../keystore.properties is missing: ${missing.joinToString(", ")}")
    }
    val storeFileProp = keystoreProperties.getProperty("storeFile")!!
    val storeFile = file(storeFileProp)
    if (!storeFile.isFile) {
        throw GradleException("officialRelease=true but keystore file does not exist: $storeFile")
    }
}

val lastVersion = 192
val lastVersionName = "r14.16.1"
val buildRevision = providers.exec {
    workingDir(rootDir)
    commandLine("git", "rev-parse", "--short=8", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { output ->
    output.trim().takeIf { it.matches(Regex("[0-9a-fA-F]{8}")) } ?: "unknown"
}.getOrElse("unknown")
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
        if (officialRelease) {
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
        buildConfigField("String", "BUILD_REVISION", "\"$buildRevision\"")
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    val officialSigning = if (officialRelease) signingConfigs.getByName("v2") else null
    buildTypes {
        create("develop") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = true
            signingConfig = officialSigning
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
            signingConfig = officialSigning
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            versionNameSuffix = "-debug"
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
            val suffix = when (variant.name) {
                "debug" -> "-debug"
                "release" -> if (officialRelease) "" else "-unsigned"
                "develop" -> if (officialRelease) "-develop" else "-develop-unsigned"
                else -> ""
            }
            output.outputFileName.set("CustoMIUIzer-A14-$lastVersionName$suffix.apk")
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
