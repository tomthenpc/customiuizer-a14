pluginManagement {
    repositories {
        maven("https://mirrors.huaweicloud.com/repository/maven/")
        maven("https://maven.aliyun.com/repository/google/")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // Local cache only for libxposed snapshots; other artifacts must come from
        // their declared upstream repositories.
        mavenLocal {
            content { includeGroup("io.github.libxposed") }
        }
        // JitPack only for GitHub-backed artifacts (com.github.* / io.github.*).
        maven("https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.github\\..*")
                includeGroupByRegex("io\\.github\\..*")
            }
        }
        // Android-specific repositories for AndroidX / Google / core frameworks.
        google()
        maven("https://maven.aliyun.com/repository/google/")
        // General mirrors and Maven Central. They are listed after the scoped
        // repositories above so scoped artifacts never fall through to them.
        maven("https://mirrors.huaweicloud.com/repository/maven/")
        mavenCentral()
    }
}

rootProject.name = "CustoMIUIzer-A14"
include(":app")
