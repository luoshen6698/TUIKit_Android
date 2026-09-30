# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html
#
# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
#
# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable
#
# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep class com.tencent.qcloud.** { *; }
-keep class com.tencentcloud.tencentcloudcustomer.** { *; }
-keep class com.tencent.imsdk.** { *; }
# TRTC/LiteAV resolve Java classes and members from JNI/reflection at native load time.
-keep class com.tencent.** { *; }
# TIMPush and Tencent callbacks are discovered by reflection and Android components.
-keep class com.tencent.qcloud.** { *; }
-keep class com.tencent.timpush.** { *; }

# AtomicX's compiled models retain this compile-time annotation.
-dontwarn kotlinx.parcelize.Parcelize
# OkHttp probes these optional JVM TLS providers; Android uses its platform provider.
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Gson reads API/session models reflectively, including unannotated JSON field names.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keep class io.trtc.tuikit.chat.demo.xingdun.network.** { *; }
# Homepage models also use Gson's LOWER_CASE_WITH_UNDERSCORES field mapping.
-keep class io.trtc.tuikit.chat.demo.xingdun.features.home.XingDunArticle { *; }
-keep class io.trtc.tuikit.chat.demo.xingdun.features.home.XingDunArticleCategory { *; }
-keep class io.trtc.tuikit.chat.demo.xingdun.features.home.XingDunArticlePage { *; }
-keep class io.trtc.tuikit.chat.demo.xingdun.features.home.XingDunArticleHome { *; }
-keep class io.trtc.tuikit.chat.demo.xingdun.features.XingDunVerificationMessagesActivity$FriendApplicationPage { *; }
-keep class io.trtc.tuikit.chat.demo.xingdun.features.XingDunVerificationMessagesActivity$FriendApplication { *; }
-keep class io.trtc.tuikit.chat.demo.xingdun.features.XingDunVerificationMessagesActivity$ApplicationUser { *; }
-keep class io.trtc.tuikit.chat.demo.xingdun.features.XingDunVerificationMessagesActivity$ServerGroupInvitation { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
