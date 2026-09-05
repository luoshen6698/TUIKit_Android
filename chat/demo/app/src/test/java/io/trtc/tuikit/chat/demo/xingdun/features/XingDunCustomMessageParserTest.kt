package io.trtc.tuikit.chat.demo.xingdun.features

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XingDunCustomMessageParserTest {
    @Test
    fun refreshesPermissionsForBothMuteTransitionsAndNestedEnvelopes() {
        for (event in listOf("mute_all_enabled", "mute_all_disabled", "group_fields_updated")) {
            val message = requireNotNull(XingDunCustomMessageParser.parse(
                """{"type":"config_refresh","data":{"scope":"group","event":"$event"}}"""
            ))
            assertTrue(message.requiresGroupPermissionRefresh())
        }
        assertTrue(requireNotNull(XingDunCustomMessageParser.parse(
            """{"scope":"GROUP"}""", "XingDun:config_refresh"
        )).requiresGroupPermissionRefresh())
    }

    @Test
    fun ignoresUnrelatedAndMissingRefreshScopes() {
        for (data in listOf(
            """{"type":"config_refresh","scope":"user"}""",
            """{"type":"config_refresh"}""",
            """{"type":"redpacket","scope":"group"}"""
        )) {
            assertFalse(requireNotNull(XingDunCustomMessageParser.parse(data)).requiresGroupPermissionRefresh())
        }
        assertNull(XingDunCustomMessageParser.parse("invalid"))
    }

    @Test
    fun parsesNestedRedpacketAsDisplayOnlyBusinessMessage() {
        val message = XingDunCustomMessageParser.parse(
            """{"type":"redpacket","data":{"packet_no":"P001","greeting":"恭喜发财","scene":"c2c"}}"""
        )

        requireNotNull(message)
        assertEquals("redpacket", message.type)
        assertEquals("P001", message.values["packet_no"])
        assertEquals("恭喜发财", message.values["greeting"])
        assertFalse(message.isControl)
    }

    @Test
    fun hidesControlMessagesButKeepsGroupRefreshVisible() {
        assertTrue(
            requireNotNull(XingDunCustomMessageParser.parse("""{"type":"remote_delete","data":{"message_id":"M1"}}""")).isControl
        )
        assertTrue(
            requireNotNull(XingDunCustomMessageParser.parse("""{"type":"config_refresh","scope":"user"}""")).isControl
        )
        assertFalse(
            requireNotNull(XingDunCustomMessageParser.parse("""{"type":"config_refresh","scope":"group"}""")).isControl
        )
    }

    @Test
    fun resolvesBackendGroupInvitationNoticeForSystemPresentation() {
        val message = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"type":"config_refresh","scope":"group","event":"members_invited","actor_user_id":"xd_owner","actor_name":"c001","member_user_ids_json":["xd_b001","xd_cs"],"member_names_json":["b001","星盾客服"]}"""
            )
        )

        val notice = XingDunGroupConfigurationNoticeFormatter.resolve(message.values)

        assertEquals(XingDunGroupConfigurationNotice.Kind.MEMBERS_INVITED, notice.kind)
        assertEquals("c001", notice.actorName)
        assertEquals(listOf("b001", "星盾客服"), notice.memberNames)
    }

    @Test
    fun rejectsIncompleteInvitationDetailsInsteadOfShowingBrokenNames() {
        val notice = XingDunGroupConfigurationNoticeFormatter.resolve(
            mapOf(
                "event" to "members_invited",
                "actor_user_id" to "xd_owner",
                "actor_name" to "c001",
                "member_user_ids_json" to "[\"xd_b001\",\"xd_cs\"]",
                "member_names_json" to "[\"b001\"]",
            )
        )

        assertEquals(XingDunGroupConfigurationNotice.Kind.GENERIC, notice.kind)
    }

    @Test
    fun normalizesSupportedChangedGroupFields() {
        val notice = XingDunGroupConfigurationNoticeFormatter.resolve(
            mapOf("event" to "group_fields_updated", "changed_fields" to "name, mute_all, unknown, name")
        )

        assertEquals(XingDunGroupConfigurationNotice.Kind.GROUP_FIELDS_UPDATED, notice.kind)
        assertEquals(listOf("name", "mute_all"), notice.changedFields)
    }

    @Test
    fun exposesVersionedAutoDeleteConfigurationValues() {
        val message = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"type":"auto_delete_config","payload":{"ttl_seconds":604800,"version":5,"updated_at":"2026-08-27T10:00:00+08:00"}}"""
            )
        )
        assertTrue(message.isControl)
        assertEquals("604800", message.values["ttl_seconds"])
        assertEquals("5", message.values["version"])
    }

    @Test
    fun doesNotCaptureUnrelatedTencentCustomMessages() {
        assertNull(XingDunCustomMessageParser.parse("""{"businessID":"TUICallKit","version":1}"""))
    }

    @Test
    fun acceptsExplicitXingDunDescriptionEnvelope() {
        val message = XingDunCustomMessageParser.parse("{}", "XingDun:report_notice")
        assertEquals("report_notice", message?.type)
    }

    @Test
    fun dormantRedpacketCannotOpenButRemainsParsable() {
        val message = XingDunCustomMessageParser.parse(
            """{"type":"redpacket","data":{"packet_no":"P002","status":2}}"""
        )

        assertEquals("P002", message?.values?.get("packet_no"))
        assertFalse(XingDunRedpacketAccessPolicy.canOpen(featureEnabled = false))
        assertTrue(XingDunRedpacketAccessPolicy.canOpen(featureEnabled = true))
    }

    @Test
    fun parsesLegacySnakeCaseContactCardContract() {
        val card = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"type":"xingdun_contact_card","version":1,"data":{"user_id":"xd_xc2026_59","custom_id":"b002","display_name":"不会","avatar_url":"https://example.com/avatar.png","department":"产品部"}}"""
            )?.contactCard()
        )

        assertEquals("xd_xc2026_59", card.userID)
        assertEquals("b002", card.customID)
        assertEquals("不会", card.displayName)
        assertEquals("https://example.com/avatar.png", card.avatarURL)
        assertEquals("产品部", card.department)
    }

    @Test
    fun parsesCurrentIosContactCardContract() {
        val card = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"type":"contact_card","data":{"accid":"xd_xc2026_59","name":"不会","avatar":"https://example.com/ios-avatar.png","customId":"b002"}}"""
            )?.contactCard()
        )

        assertEquals("xd_xc2026_59", card.userID)
        assertEquals("b002", card.customID)
        assertEquals("不会", card.displayName)
        assertEquals("https://example.com/ios-avatar.png", card.avatarURL)
    }

    @Test
    fun parsesLegacyCamelCaseContactCardAndFallsBackToUserId() {
        val card = requireNotNull(
            XingDunCustomMessageParser.parse(
                """{"type":"contact_card","payload":{"timUserId":"xd_xc2026_270","nickname":"b002"}}"""
            )?.contactCard()
        )

        assertEquals("xd_xc2026_270", card.userID)
        assertEquals("b002", card.displayName)
        assertNull(card.customID)
    }

    @Test
    fun rejectsContactCardWithoutUserIdentity() {
        val message = XingDunCustomMessageParser.parse(
            """{"type":"xingdun_contact_card","display_name":"无效名片"}"""
        )

        assertNull(message?.contactCard())
    }
}
