plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Only the Huawei distribution opts in to the home page at build time.
val homeEnabled = (findProperty("XINGDUN_HOME_ENABLED") as String?
    ?: System.getenv("XINGDUN_HOME_ENABLED") ?: "false").let {
    require(it == "true" || it == "false") { "XINGDUN_HOME_ENABLED must be true or false." }
    it.toBoolean()
}

// Offline push test/release builds opt in with -PXINGDUN_OFFLINE_PUSH_ENABLED=true.
val offlinePushEnabled = (findProperty("XINGDUN_OFFLINE_PUSH_ENABLED") as String?
    ?: System.getenv("XINGDUN_OFFLINE_PUSH_ENABLED") ?: "false").let {
    require(it == "true" || it == "false") { "XINGDUN_OFFLINE_PUSH_ENABLED must be true or false." }
    it.toBoolean()
}

if (offlinePushEnabled && file("agconnect-services.json").isFile) {
    apply(plugin = "com.huawei.agconnect")
}
if (offlinePushEnabled && file("mcs-services.json").isFile) {
    apply(plugin = "com.hihonor.mcs.asplugin")
}

val appVersionCode = (findProperty("VERSION_CODE") as String?
    ?: System.getenv("VERSION_CODE"))?.toIntOrNull() ?: 1
val appVersionName = (findProperty("VERSION_NAME") as String?
    ?: System.getenv("VERSION_NAME")) ?: "1.0.0"
val apiBaseUrl = (findProperty("XINGDUN_API_BASE_URL") as String?
    ?: System.getenv("XINGDUN_API_BASE_URL")) ?: "https://api.xingdunim.com/prod/im/v1"
val honorAppId = (findProperty("HONOR_APPID") as String?
    ?: System.getenv("HONOR_APPID")).orEmpty()
val vivoAppId = (findProperty("VIVO_APPID") as String?
    ?: System.getenv("VIVO_APPID")).orEmpty()
val vivoAppKey = (findProperty("VIVO_APPKEY") as String?
    ?: System.getenv("VIVO_APPKEY")).orEmpty()
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
val debugUseReleaseSigning = (findProperty("XINGDUN_DEBUG_USE_RELEASE_SIGNING") as String?
    ?: System.getenv("XINGDUN_DEBUG_USE_RELEASE_SIGNING") ?: "false").let {
    require(it == "true" || it == "false") { "XINGDUN_DEBUG_USE_RELEASE_SIGNING must be true or false." }
    it.toBoolean()
}

// Merge only push declarations into the main manifest; keep the existing debug overlay intact.
val offlinePushManifest = layout.buildDirectory.file("generated/offlinePush/AndroidManifest.xml")
val generateOfflinePushManifest by tasks.registering {
    val mainManifest = file("src/main/AndroidManifest.xml")
    val pushManifest = file("src/offlinePush/AndroidManifest.xml")
    inputs.files(mainManifest, pushManifest)
    outputs.file(offlinePushManifest)
    doLast {
        val pushDeclarations = pushManifest.readText()
            .substringAfter("<application>").substringBefore("</application>")
        val output = offlinePushManifest.get().asFile
        output.parentFile.mkdirs()
        output.writeText(mainManifest.readText().replace("</application>", "$pushDeclarations\n    </application>"))
    }
}

android {
    sourceSets.getByName("main").java.srcDir(
        if (offlinePushEnabled) "src/offlinePush/java" else "src/noOfflinePush/java"
    )
    if (offlinePushEnabled) {
        sourceSets.getByName("main").manifest.srcFile(offlinePushManifest.get().asFile)
    }
    namespace = "io.trtc.tuikit.chat.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.xingdunim.app"
        minSdk = 23
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("boolean", "XINGDUN_HOME_ENABLED", homeEnabled.toString())
        buildConfigField("String", "XINGDUN_API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "XINGDUN_ENVIRONMENT", "\"prod\"")
        // Vendor manifests require these placeholders. Unconfigured debug builds use non-routable
        // values; push-enabled release builds are blocked by verifyReleaseConfiguration.
        manifestPlaceholders["HONOR_APPID"] = honorAppId.ifBlank { "0" }
        manifestPlaceholders["VIVO_APPID"] = vivoAppId.ifBlank { "0" }
        manifestPlaceholders["VIVO_APPKEY"] = vivoAppKey.ifBlank { "0" }

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
        debug {
            // Authorized push test APKs retain the production application identity and signature.
            if (debugUseReleaseSigning) {
                check(releaseSigningReady && file(releaseStorePath).isFile) {
                    "Signed debug tests require complete XINGDUN_RELEASE signing configuration."
                }
                signingConfig = signingConfigs.getByName("release")
            }
        }
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
    description = "Verifies release signing and, when enabled, offline push configuration."
    doLast {
        check(releaseSigningReady && file(releaseStorePath).isFile) {
            "Release signing requires XINGDUN_RELEASE_STORE_FILE, XINGDUN_RELEASE_STORE_PASSWORD, " +
                "XINGDUN_RELEASE_KEY_ALIAS and XINGDUN_RELEASE_KEY_PASSWORD."
        }
        if (!offlinePushEnabled) {
            logger.lifecycle("Offline push disabled: TIMPush and vendor SDKs are excluded from this build.")
            return@doLast
        }
        val requiredFiles = listOf(
            file("src/main/assets/timpush-configs.json"),
            file("agconnect-services.json"),
            file("mcs-services.json")
        )
        val missingFiles = requiredFiles.filterNot { it.isFile }.map { it.relativeTo(projectDir).path }
        check(missingFiles.isEmpty()) {
            "Missing release vendor configuration: ${missingFiles.joinToString()}"
        }
        check(honorAppId.isNotBlank()) {
            "Release Honor push requires HONOR_APPID."
        }
        check(vivoAppId.isNotBlank() && vivoAppKey.isNotBlank()) {
            "Release vivo push requires VIVO_APPID and VIVO_APPKEY."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseConfiguration)
}
if (offlinePushEnabled) {
    tasks.matching { it.name.startsWith("process") && it.name.contains("Manifest") }.configureEach {
        dependsOn(generateOfflinePushManifest)
    }
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
    if (offlinePushEnabled) {
        implementation("com.tencent.timpush:timpush:9.0.7652")
        implementation("com.tencent.timpush:huawei:9.0.7652")
        implementation("com.tencent.timpush:honor:9.0.7652")
        implementation("com.tencent.timpush:xiaomi:9.0.7652")
        implementation("com.tencent.timpush:oppo:9.0.7652")
        implementation("com.tencent.timpush:vivo:9.0.7652")
    }
    implementation("com.tencent:mmkv:2.4.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    testImplementation("junit:junit:4.13.2")
}
