package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunContactDetail
import org.junit.Assert.*
import org.junit.Test

class XingDunAccountTypeTest {
    @Test fun `missing legacy field defaults to normal and invalid classification remains unknown`() {
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        assertEquals(R.string.xingdun_account_type_normal, XingDunAccountType.label(gson.fromJson("{}", XingDunContactDetail::class.java).accountType))
        assertEquals(R.string.xingdun_account_type_internal, XingDunAccountType.label(gson.fromJson("""{"account_type":"internal"}""", XingDunContactDetail::class.java).accountType))
        assertEquals(R.string.xingdun_account_type_unknown, XingDunAccountType.label("administrator"))
        assertEquals(R.string.xingdun_account_type_unknown, XingDunAccountType.label(null))
    }
}
