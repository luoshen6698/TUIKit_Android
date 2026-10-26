package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class XingDunProfileVisibilityTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    @Test fun `missing and malformed flags do not disclose profile fields`() {
        assertFalse(XingDunProfileVisibility.visible(null, "account"))
        val flags = json("""{"account":"true","phone":1,"email":null,"birthday":{},"signature":false}""")
        XingDunProfileVisibility.fields.forEach { assertFalse(XingDunProfileVisibility.visible(flags, it)) }
    }
    @Test fun `settings require all five boolean fields and boolean synced`() {
        val response = json("""{"visibility":{"account":false,"phone":true,"email":false,"birthday":true,"signature":false},"synced":false}""")
        val (visibility, synced) = XingDunProfileVisibility.parseSettings(response)
        assertFalse(synced)
        assertTrue(XingDunProfileVisibility.visible(visibility, "phone"))
        assertFalse(XingDunProfileVisibility.visible(visibility, "account"))
        response.getAsJsonObject("visibility").addProperty("phone", false)
        assertTrue(XingDunProfileVisibility.visible(visibility, "phone"))
    }
    @Test(expected = IllegalArgumentException::class) fun `partial server settings are rejected`() {
        XingDunProfileVisibility.parseSettings(json("""{"visibility":{"account":true},"synced":true}"""))
    }
    @Test(expected = IllegalArgumentException::class) fun `string synced is rejected`() {
        XingDunProfileVisibility.parseSettings(json("""{"visibility":{"account":true,"phone":true,"email":true,"birthday":true,"signature":true},"synced":"true"}"""))
    }
}
