plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

if (file("agconnect-services.json").isFile) {
    apply(plugin = "com.huawei.agconnect")
}
if (file("mcs-services.json").isFile) {
    apply(plugin = "com.hihonor.mcs.asplugin")
}

val appVersionCode = (findProperty("VERSION_CODE") as String?
    ?: System.getenv("VERSION_CODE"))?.toIntOrNull() ?: 1
val appVersionName = (findProperty("VERSION_NAME") as String?
    ?: System.getenv("VERSION_NAME")) ?: "1.0"
val apiBaseUrl = (findProperty("XINGDUN_API_BASE_URL") as String?
    ?: System.getenv("XINGDUN_API_BASE_URL")) ?: "https://api.xingdunim.com/prod/im/v1"
val honorAppId = (findProperty("HONOR_APPID") as String?
    ?: System.getenv("HONOR_APPID")).orEmpty()
val releaseStorePath = System.getenv("XINGDUN_RELEASE_STORE_FILE").orEmpty()
val releaseStorePassword = System.getenv("XINGDUN_RELEASE_STORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("XINGDUN_RELEASE_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("XINGDUN_RELEASE_KEY_PASSWORD").orEmpty()
val releaseSigningReady = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all(String::isNotBlank)

android {
    namespace = "io.trtc.tuikit.chat.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.xingdunim.app"
        minSdk = 23
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "XINGDUN_API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "XINGDUN_ENVIRONMENT", "\"prod\"")
        // The Honor TIMPush manifest always declares this placeholder. Debug builds intentionally
        // use a non-routable value; release builds are still blocked by verifyReleaseConfiguration.
        manifestPlaceholders["HONOR_APPID"] = honorAppId.ifBlank { "0" }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

val verifyReleaseConfiguration by tasks.registering {
    group = "verification"
    description = "Verifies XingDun signing and TIMPush vendor configuration before release builds."
    doLast {
        val requiredFiles = listOf(
            file("src/main/assets/timpush-configs.json"),
            file("agconnect-services.json"),
            file("mcs-services.json")
        )
        val missingFiles = requiredFiles.filterNot { it.isFile }.map { it.relativeTo(projectDir).path }
        check(missingFiles.isEmpty()) {
            "Missing release vendor configuration: ${missingFiles.joinToString()}"
        }
        check(releaseSigningReady && file(releaseStorePath).isFile) {
            "Release signing requires XINGDUN_RELEASE_STORE_FILE, XINGDUN_RELEASE_STORE_PASSWORD, " +
                "XINGDUN_RELEASE_KEY_ALIAS and XINGDUN_RELEASE_KEY_PASSWORD."
        }
        check(honorAppId.isNotBlank()) {
            "Release Honor push requires HONOR_APPID."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseConfiguration)
}

dependencies {
    implementation(project(":uikit"))
    implementation(project(":atomic_x"))
    implementation(project(":tuicallkit-kt"))
    implementation("com.tencent.imsdk:imsdk-plus:9.0.7652")
    implementation("com.tencent.imsdk:timquic-plugin:9.0.7652")
    implementation("com.tencent.liteav.tuikit:tuicore:9.0.7652") {
        exclude("com.tencent.imsdk", "imsdk-plus")
    }
    implementation("com.tencent.timpush:timpush:9.0.7652")
    implementation("com.tencent.timpush:huawei:9.0.7652")
    implementation("com.tencent.timpush:honor:9.0.7652")
    implementation("com.tencent:mmkv:2.4.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
