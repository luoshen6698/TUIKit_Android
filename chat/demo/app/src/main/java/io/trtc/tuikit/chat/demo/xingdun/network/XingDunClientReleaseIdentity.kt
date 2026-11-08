package io.trtc.tuikit.chat.demo.xingdun.network

import io.trtc.tuikit.chat.app.BuildConfig

/** Public build identity. Distribution channel is never inferred from the device manufacturer. */
internal object XingDunClientReleaseIdentity {
    fun current(): Map<String, String> = parameters(BuildConfig.XINGDUN_APP_CODE,
        BuildConfig.XINGDUN_RELEASE_CHANNEL, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
        BuildConfig.APPLICATION_ID)

    fun parameters(app: String, channel: String, version: String, build: Int, packageName: String): Map<String, String> {
        require(Regex("^[a-z][a-z0-9_-]{0,31}$").matches(app))
        require(Regex("^[a-z][a-z0-9_-]{0,31}$").matches(channel))
        require(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?$").matches(version))
        require(build > 0)
        return mapOf("app" to app, "client_platform" to "android", "release_channel" to channel,
            "app_version" to version, "app_build" to build.toString(), "client_package" to packageName)
    }
}
