package io.trtc.tuikit.chat.demo.xingdun.routing

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XingDunNotificationIntentReaderTest {
    @Test fun keepsAuthoritativeExtension() {
        val payload = """{"ChatKey":"C2Cxd_xc2026_269"}"""
        assertEquals(payload, XingDunNotificationIntentReader.payloadFromExtras(mapOf("ext" to payload)))
    }

    @Test fun preservesOPPOBusinessEntityForTheSharedParser() {
        val payload = XingDunNotificationIntentReader.payloadFromExtras(
            mapOf("entity" to """{"sender":"xd_xc2026_269","chatType":1}"""),
        )
        assertEquals("xd_xc2026_269", JsonParser.parseString(payload).asJsonObject.get("sender").asString)
    }

    @Test fun preservesChatKeyAndEntityTogetherForConflictValidation() {
        val payload = XingDunNotificationIntentReader.payloadFromExtras(mapOf(
            "entity" to """{"sender":"xd_xc2026_269","chatType":1}""",
            "ChatKey" to "C2Cxd_xc2026_269",
        ))
        val json = JsonParser.parseString(payload).asJsonObject
        assertEquals(1, json.get("chatType").asInt)
        assertNull(XingDunNotificationIntentReader.payloadFromExtras(mapOf(
            "entity" to """{"sender":"xd_xc2026_269","chatType":1}""",
            "ChatKey" to "C2Cxd_xc2026_266",
        )))
    }

    @Test fun preservesCompleteLegacyExtensionInOPPOEntityExtra() {
        val entity = """{"entity":{"sender":"xd_xc2026_269","chatType":1,"action":1}}"""
        assertEquals(entity, XingDunNotificationIntentReader.payloadFromExtras(mapOf("entity" to entity)))
        assertEquals(XingDunNotificationDestination.Conversation("c2c_xd_xc2026_269"),
            XingDunNotificationPayloadParser.parse(entity))
    }

    @Test fun doesNotFallBackFromMalformedAuthoritativeExtension() {
        assertNull(XingDunNotificationIntentReader.payloadFromExtras(mapOf("ext" to "not-json", "ChatKey" to "C2Cxd_xc2026_269")))
    }

    @Test fun preservesEntityTextForStrictDuplicateKeyValidation() {
        val entity = """{"sender":"xd_xc2026_269","sender":"xd_other_269","chatType":1}"""
        val payload = XingDunNotificationIntentReader.payloadFromExtras(mapOf("entity" to entity))
        assertNull(payload)
        assertNull(XingDunNotificationPayloadParser.parse(payload))
    }

    @Test fun ignoresMissingMalformedAndOversizedInput() {
        assertNull(XingDunNotificationIntentReader.payloadFromExtras(emptyMap()))
        assertNull(XingDunNotificationIntentReader.payloadFromExtras(mapOf("entity" to "[]")))
        assertNull(XingDunNotificationIntentReader.payloadFromExtras(mapOf("ext" to "x".repeat(1025))))
    }

    @Test fun doesNotBridgeCallSignallingIntoOrdinaryChatNavigation() {
        assertNull(XingDunNotificationIntentReader.payloadFromExtras(mapOf("ext" to """{"entity":{"sender":"xd_xc2026_269","chatType":1,"action":2}}""")))
        assertNull(XingDunNotificationIntentReader.payloadFromExtras(mapOf("entity" to """{"sender":"xd_xc2026_269","chatType":1,"action":2}""")))
        assertNull(XingDunNotificationIntentReader.payloadFromExtras(mapOf("ext" to """{"xd_voip_version":1,"conversation_id":"c2c_xd_xc2026_269"}""")))
    }
}
