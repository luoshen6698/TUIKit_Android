package io.trtc.tuikit.chat.demo.xingdun.features

import com.google.gson.JsonParser
import io.trtc.tuikit.chat.uikit.components.conversationlist.ui.JourneyConversationCategory
import org.junit.Assert.*
import org.junit.Test

class JourneyDirectoryPolicyTest {
    @Test fun categoriesDistinguishPinnedAndGroupIndependently() {
        val fixtures=listOf(false to false,true to false,false to true,true to true)
        assertEquals(4,fixtures.count { JourneyConversationCategory.ALL.accepts(it.first,it.second) })
        assertEquals(listOf(true to false,true to true),fixtures.filter { JourneyConversationCategory.PINNED.accepts(it.first,it.second) })
        assertEquals(listOf(false to true,true to true),fixtures.filter { JourneyConversationCategory.TEAM.accepts(it.first,it.second) })
    }
    @Test fun missingOrPrivateSignatureNeverLeaksCachedField() {
        listOf("{}","{\"signature\":\"private\"}","{\"signature\":\"private\",\"profile_visibility\":{\"signature\":false}}","{\"signature\":\"private\",\"profile_visibility\":{\"signature\":\"true\"}}").forEach {
            assertNull(directorySignature(JsonParser.parseString(it).asJsonObject))
        }
    }
    @Test fun publicSignaturePreservesUserTextAndNormalizesWhitespace() {
        val profile=JsonParser.parseString("""{"signature":"  一起看山海  ","profile_visibility":{"signature":true}}""").asJsonObject
        assertEquals("一起看山海",directorySignature(profile))
        profile.addProperty("signature","  ");assertNull(directorySignature(profile))
    }
}
