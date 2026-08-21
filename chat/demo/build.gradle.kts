buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        maven("https://developer.huawei.com/repo/")
        maven("https://developer.hihonor.com/repo")
    }

    dependencies {
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.8.20")
        classpath("com.android.tools.build:gradle:8.12.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        classpath("com.huawei.agconnect:agcp:1.9.1.301")
        classpath("com.hihonor.mcs:asplugin:2.0.1.300")
    }
}

plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("com.android.library") apply false
}
