package io.trtc.tuikit.chat.demo.xingdun.launch

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration

internal object XingDunAuthUiSupport {
    fun displayName(activity: Activity, bootstrap: XingDunBootstrapConfiguration): String =
        bootstrap.platform?.platformName?.trim()?.takeIf(String::isNotEmpty)
            ?: bootstrap.company?.name?.trim()?.takeIf(String::isNotEmpty)
            ?: activity.getString(R.string.demo_app_name)

    fun logoUrl(bootstrap: XingDunBootstrapConfiguration): String? {
        val raw = bootstrap.platform?.platformLogo?.trim()?.takeIf(String::isNotEmpty)
            ?: bootstrap.company?.logoUrl?.trim()?.takeIf(String::isNotEmpty)
            ?: return null
        val candidate = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        if (!candidate.scheme.isNullOrBlank()) return raw
        val baseValue = bootstrap.apiBaseUrl?.trim()?.takeIf(String::isNotEmpty)
            ?: bootstrap.company?.apiBaseUrl?.trim()?.takeIf(String::isNotEmpty)
            ?: return null
        val base = runCatching { Uri.parse(baseValue) }.getOrNull() ?: return null
        if (!base.scheme.equals("https", ignoreCase = true) || base.host.isNullOrBlank()) return null
        return Uri.Builder()
            .scheme("https")
            .encodedAuthority(base.encodedAuthority)
            .encodedPath(if (raw.startsWith('/')) raw else "/$raw")
            .build()
            .toString()
    }

    fun installLegalLinks(
        activity: Activity,
        bootstrap: XingDunBootstrapConfiguration,
        target: TextView,
        onUnavailable: () -> Unit
    ) {
        val prefix = activity.getString(R.string.xingdun_agreement_prefix)
        val agreement = activity.getString(R.string.xingdun_user_agreement_link)
        val connector = activity.getString(R.string.xingdun_agreement_connector)
        val privacy = activity.getString(R.string.xingdun_privacy_policy_link)
        target.text = SpannableStringBuilder().apply {
            append(prefix)
            val agreementStart = length
            append(agreement)
            setSpan(legalLink(activity, bootstrap.privacy.userAgreementUrl, onUnavailable), agreementStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(connector)
            val privacyStart = length
            append(privacy)
            setSpan(legalLink(activity, bootstrap.privacy.privacyUrl, onUnavailable), privacyStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        target.movementMethod = LinkMovementMethod.getInstance()
        target.highlightColor = Color.TRANSPARENT
    }

    private fun legalLink(
        activity: Activity,
        value: String,
        onUnavailable: () -> Unit
    ): ClickableSpan = object : ClickableSpan() {
        override fun onClick(widget: View) {
            val uri = runCatching { Uri.parse(value) }.getOrNull()
            if (uri?.scheme != "https" || uri.host.isNullOrBlank()) {
                onUnavailable()
                return
            }
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }

        override fun updateDrawState(ds: TextPaint) {
            ds.color = Color.rgb(23, 154, 132)
            ds.isUnderlineText = false
            ds.isFakeBoldText = true
        }
    }
}
