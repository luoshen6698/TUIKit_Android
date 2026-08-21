pluginManagement {
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application",
                "com.android.library" -> useVersion("8.12.2")
                "org.jetbrains.kotlin.android" -> useVersion("1.9.22")
            }
        }
    }

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.tencent.com/repository/maven/liteavsdk")
        maven("https://developer.huawei.com/repo/")
        maven("https://developer.hihonor.com/repo")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        maven("https://mirrors.tencent.com/repository/maven/liteavsdk")
        maven("https://developer.huawei.com/repo/")
        maven("https://developer.hihonor.com/repo")
        mavenCentral()
    }
}

rootProject.name = "Chat"

include(":app")

include(":uikit")
project(":uikit").projectDir = file("${settingsDir.path}/../uikit")

include(":atomic_x")
project(":atomic_x").projectDir = file("${settingsDir.path}/../../atomic_x")

include(":tuicallkit-kt")
project(":tuicallkit-kt").projectDir = file("${settingsDir.path}/../../call/tuicallkit-kt")
