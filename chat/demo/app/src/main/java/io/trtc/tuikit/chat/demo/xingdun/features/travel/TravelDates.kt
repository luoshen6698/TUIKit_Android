package io.trtc.tuikit.chat.demo.xingdun.features.travel

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** java.time is unavailable on the project's API 23-25 devices. Formatters are request-local. */
internal object TravelDates {
    private fun parse(value: String, pattern: String): Date? {
        val position=ParsePosition(0)
        val result=SimpleDateFormat(pattern,Locale.ROOT).apply{isLenient=false;timeZone=TimeZone.getTimeZone("UTC")}.parse(value,position)
        return result.takeIf{position.index==value.length}
    }
    fun validDate(value:String):Boolean = value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && parse(value,"yyyy-MM-dd")!=null
    fun utcTimestamp(value:String):Date? = if(!value.endsWith("Z")) null else parse(value,"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") ?: parse(value,"yyyy-MM-dd'T'HH:mm:ss'Z'")
    fun todayKey():String = Calendar.getInstance().let{"${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}"}
    fun hour():Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    fun monthDay(date:Date):String = Calendar.getInstance().apply{time=date}.let{"${it.get(Calendar.MONTH)+1}/${it.get(Calendar.DAY_OF_MONTH)}"}
}
