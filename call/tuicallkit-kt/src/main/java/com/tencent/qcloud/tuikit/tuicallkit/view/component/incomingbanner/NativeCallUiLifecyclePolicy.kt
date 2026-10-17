package com.tencent.qcloud.tuikit.tuicallkit.view.component.incomingbanner

/** A terminal SDK state must close its UI even when the one-shot end event was missed. */
internal object NativeCallUiLifecyclePolicy {
    fun canPresentIncoming(
        expected: NativeCallIdentity,
        current: NativeCallIdentity,
        sameMediaType: Boolean,
        isCalleeWaiting: Boolean,
        isForeground: Boolean,
    ): Boolean = expected.callId.isNotBlank() && expected.sdkAppId > 0 &&
        expected.selfId.isNotBlank() && expected == current && sameMediaType &&
        isCalleeWaiting && isForeground

    fun shouldFinishIdleCall(
        expected: NativeCallIdentity?,
        displayed: NativeCallIdentity?,
        sdkIsIdle: Boolean,
    ): Boolean = sdkIsIdle && expected == displayed

    fun hasDifferentActiveCall(
        displayed: NativeCallIdentity,
        current: NativeCallIdentity,
        sdkIsIdle: Boolean,
    ): Boolean = !sdkIsIdle && current.callId.isNotBlank() && current.sdkAppId > 0 &&
        current.selfId.isNotBlank() && current != displayed
}
