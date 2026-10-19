package ru.hiro.manager

internal data class ClientPriorityResult(val rule: HiroPriorityRule?, val clientRule: Boolean, val score: List<Int>)
internal fun priorityScope(rule: HiroPriorityRule): Int = when {
    rule.lineId.isNotEmpty() -> 4
    rule.simId.isNotEmpty() -> 3
    rule.deviceId.isNotEmpty() -> 2
    rule.gatewayType.isNotEmpty() -> 1
    else -> 0
}
internal fun comparePriorityScores(a: List<Int>, b: List<Int>): Int {
    for(index in a.indices) if(a[index] != b[index]) return a[index].compareTo(b[index])
    return 0
}
// Matches the server's lexicographic Score: scope, client override, direction,
// then value. Comparing only numeric priorities across lines is incorrect.
internal fun resolveClientPriority(client: List<HiroPriorityRule>, organization: List<HiroPriorityRule>, line: HiroVoiceLine, direction: String): ClientPriorityResult {
    var best=ClientPriorityResult(null,false,listOf(-1,0,0,0))
    listOf(organization,client).forEachIndexed { owner,rules -> rules.forEach { r ->
        val match=(r.direction == "both" || r.direction == direction) && when {
            r.lineId.isNotEmpty() -> r.lineId == line.id
            r.deviceId.isNotEmpty() -> r.deviceId == line.deviceId && (r.simId.isEmpty() || line.simVerified && r.simId == line.simId)
            r.gatewayType.isNotEmpty() -> r.gatewayType == line.gatewayType
            else -> true
        }
        if(match) {
            val score=listOf(priorityScope(r),owner,if(r.direction == direction)1 else 0,r.priority)
            if(comparePriorityScores(score,best.score)>0)best=ClientPriorityResult(r,owner==1,score)
        }
    } }
    return best
}
internal fun automaticClientLine(client: List<HiroPriorityRule>, organization: List<HiroPriorityRule>, lines: List<HiroVoiceLine>): HiroVoiceLine? = lines.filter{it.enabled}.sortedWith { a,b ->
    val compare=comparePriorityScores(resolveClientPriority(client,organization,b,"outbound").score,resolveClientPriority(client,organization,a,"outbound").score)
    if(compare!=0)compare else (a.code.toIntOrNull()?:Int.MAX_VALUE).compareTo(b.code.toIntOrNull()?:Int.MAX_VALUE)
}.firstOrNull()
internal fun isBasePriority(rule: HiroPriorityRule)=priorityScope(rule)==0
internal fun basePriority(rules: List<HiroPriorityRule>,direction: String): Int? = rules.filter{isBasePriority(it)&&(it.direction==direction||it.direction=="both")}.maxWithOrNull(compareBy<HiroPriorityRule>{it.direction==direction}.thenBy{it.priority})?.priority
