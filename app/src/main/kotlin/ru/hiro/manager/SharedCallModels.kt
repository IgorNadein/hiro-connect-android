package ru.hiro.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.util.Locale
import java.time.format.DateTimeFormatter

@Serializable
data class SharedCall(
    val id:String, val number:String="", val name:String="", val direction:String="unknown", val status:String="archived",
    @SerialName("started_at") val startedAt:String,
    @SerialName("ended_at") val endedAt:String?=null,
    @SerialName("duration_seconds") val duration:Long=0,
    @SerialName("talk_seconds") val talk:Long=0,
    val line:String="", @SerialName("gateway_type") val gatewayType:String="",
    val operators:List<String> = emptyList(),
    @SerialName("recording_available") val recording:Boolean=false,
    val source:String="", val note:String="",
    @SerialName("note_revision") val noteRevision:Long=0
)
@Serializable data class CallCapabilities(val recordings:Boolean=false,val notes:Boolean=false,val join:Boolean=false)
@Serializable data class SharedCallPage(val items:List<SharedCall>,@SerialName("next_cursor") val nextCursor:String="",val capabilities:CallCapabilities=CallCapabilities(),val problem:String="")
@Serializable data class SharedCallDetail(val call:SharedCall,val capabilities:CallCapabilities=CallCapabilities())
@Serializable data class LiveTeamCall(val id:String,val number:String="",val direction:String="unknown",val state:String="waiting",@SerialName("duration_seconds")val duration:Long=0,@SerialName("gateway_type")val gatewayType:String="",val operators:List<String> = emptyList())
@Serializable data class LiveTeamCalls(val items:List<LiveTeamCall>,val ready:Boolean=false,val problem:String="",val capabilities:CallCapabilities=CallCapabilities())
@Serializable data class SharedCallNote(val text:String)
@Serializable data class JoinCallRequest(val transport:String="native")

internal fun callTitle(call:SharedCall)=call.name.ifBlank{call.number.ifBlank{"Архивная запись"}}
internal fun callDate(raw: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    runCatching { Instant.parse(raw).atZone(zone).toLocalDate() }.getOrNull()
internal fun callDayLabel(date: LocalDate?, today: LocalDate = LocalDate.now()): String = when (date) {
    null -> "Дата не указана"
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMMM" else "d MMMM yyyy", Locale("ru")))
}
internal fun callClockTime(raw: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(Instant.parse(raw)) }.getOrDefault("—")
internal fun callTime(raw:String):String=runCatching{DateTimeFormatter.ofPattern("dd MMM · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(raw))}.getOrDefault("Дата не указана")
internal fun callDuration(seconds:Long):String { val safe=seconds.coerceAtLeast(0);return "%d:%02d".format(safe/60,safe%60) }
internal fun callStatus(call:SharedCall):String=when(call.status){"missed"->"Пропущенный";"busy"->"Занято";"failed"->"Не состоялся";"answered"->if(call.direction=="outbound")"Исходящий" else "Входящий";else->"Архив"}
internal fun callCanDial(number:String)=number.matches(Regex("\\+?[0-9]{3,20}"))
internal fun callGatewayLabel(gateway:String)=when(gateway){"mobile"->"Телефон-шлюз";"novofon"->"NOVOFON";"mango"->"MANGO OFFICE";"sip"->"SIP";else->gateway}

internal interface SharedCallRepository {
    suspend fun history(query:String,missed:Boolean,before:String=""):SharedCallPage
    suspend fun live():LiveTeamCalls
    suspend fun detail(id:String):SharedCallDetail
    suspend fun note(id:String,text:String,revision:Long):SharedCallDetail
    suspend fun recording(id:String,destination:File)
    suspend fun join(id:String)
}
internal class AuthenticatedCallRepository(private val auth:HiroAuthViewModel):SharedCallRepository {
    override suspend fun history(query:String,missed:Boolean,before:String)=auth.authenticatedRequest{c,s->c.sharedCalls(s,query,missed,before)}
    override suspend fun live()=auth.authenticatedRequest{c,s->c.liveTeamCalls(s)}
    override suspend fun detail(id:String)=auth.authenticatedRequest{c,s->c.sharedCall(s,id)}
    override suspend fun note(id:String,text:String,revision:Long)=auth.authenticatedRequest{c,s->c.saveCallNote(s,id,text,revision)}
    override suspend fun recording(id:String,destination:File){auth.authenticatedRequest{c,s->c.downloadCallRecording(s,id,destination)}}
    override suspend fun join(id:String){auth.authenticatedRequest{c,s->c.joinTeamCall(s,id)}}
}
