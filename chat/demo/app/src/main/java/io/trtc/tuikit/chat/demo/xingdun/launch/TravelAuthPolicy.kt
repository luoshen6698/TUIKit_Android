package io.trtc.tuikit.chat.demo.xingdun.launch

import com.tencent.mmkv.MMKV
import io.trtc.tuikit.chat.demo.xingdun.network.XingDunBootstrapConfiguration
import java.util.Locale

internal object TravelAuthPolicy {
    fun showsCompanyCode(explicit:Boolean?, mode:String?):Boolean = explicit ?: !mode.equals("simple",true)
    fun validPassword(value:String):Boolean = value.length in 6..64
    fun normalizedCode(value:String):String? = value.trim().lowercase(Locale.ROOT).takeIf { Regex("^[a-z0-9]{4,20}$").matches(it) }
    fun selectionKey(value:XingDunBootstrapConfiguration):String = listOf(value.companyCode.lowercase(Locale.ROOT),value.company?.id,value.sdkAppId,value.apiBaseUrl?:value.company?.apiBaseUrl).joinToString("|")
}

internal object TravelCompanySelection {
    private const val KEY="xuyinfang.auth.selected-company.v1"
    fun matches(value:XingDunBootstrapConfiguration?)=value!=null && value.configured && MMKV.defaultMMKV().decodeString(KEY,"")==TravelAuthPolicy.selectionKey(value)
    fun record(value:XingDunBootstrapConfiguration){require(value.configured);MMKV.defaultMMKV().encode(KEY,TravelAuthPolicy.selectionKey(value))}
}
