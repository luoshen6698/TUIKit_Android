package io.trtc.tuikit.chat.demo.xingdun.session

import android.content.Context

internal data class XingDunAccountDeletionReceipt(
    val receipt: String,
    val purgeAfter: String?
)

internal object XingDunAccountDeletionReceiptStore {
    private const val PREFERENCES = "xingdun_account_deletion"
    private const val KEY_RECEIPT = "deletion_receipt"
    private const val KEY_PURGE_AFTER = "purge_after"

    fun save(context: Context, receipt: String?, purgeAfter: String?) {
        val normalizedReceipt = receipt?.trim().orEmpty()
        if (normalizedReceipt.isEmpty()) return
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(KEY_RECEIPT, normalizedReceipt)
            .putString(KEY_PURGE_AFTER, purgeAfter?.trim())
            .apply()
    }

    fun load(context: Context): XingDunAccountDeletionReceipt? {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val receipt = preferences.getString(KEY_RECEIPT, null)?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return XingDunAccountDeletionReceipt(receipt, preferences.getString(KEY_PURGE_AFTER, null))
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
