package io.trtc.tuikit.chat.demo.xingdun.launch
import io.trtc.tuikit.chat.demo.xingdun.network.*
import org.junit.Assert.*
import org.junit.Test
class TravelAuthPolicyTest {
    @Test fun explicitServerFlagOverridesLegacyMode(){assertFalse(TravelAuthPolicy.showsCompanyCode(false,"enterprise"));assertTrue(TravelAuthPolicy.showsCompanyCode(true,"simple"))}
    @Test fun oldResponsesAndMissingPolicyFailClosed(){assertFalse(TravelAuthPolicy.showsCompanyCode(null,"simple"));assertTrue(TravelAuthPolicy.showsCompanyCode(null,"enterprise"));assertTrue(TravelAuthPolicy.showsCompanyCode(null,null))}
    @Test fun manualSelectionDoesNotCrossTenantOrSdk(){
        val first=XingDunBootstrapConfiguration(configured=true,companyCode="xc2026",sdkAppId=123,company=XingDunCompany(id=1,code="xc2026"),apiBaseUrl="https://api.example.test/prod/im/v1")
        assertNotEquals(TravelAuthPolicy.selectionKey(first),TravelAuthPolicy.selectionKey(first.copy(sdkAppId=456)))
        assertNotEquals(TravelAuthPolicy.selectionKey(first),TravelAuthPolicy.selectionKey(first.copy(company=first.company?.copy(id=2))))
        assertNotEquals(TravelAuthPolicy.selectionKey(first),TravelAuthPolicy.selectionKey(first.copy(apiBaseUrl="https://other.example.test")))
    }
    @Test fun referencePasswordAndCompanyInputBounds(){assertFalse(TravelAuthPolicy.validPassword("short"));assertTrue(TravelAuthPolicy.validPassword("simple"));assertTrue(TravelAuthPolicy.validPassword("a".repeat(64)));assertFalse(TravelAuthPolicy.validPassword("a".repeat(65)));assertEquals("xc2026",TravelAuthPolicy.normalizedCode(" XC2026 "));assertNull(TravelAuthPolicy.normalizedCode("https://example.test"));assertNull(TravelAuthPolicy.normalizedCode("企业1234"))}
}
