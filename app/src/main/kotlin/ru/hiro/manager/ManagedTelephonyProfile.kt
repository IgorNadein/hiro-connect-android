package ru.hiro.manager

import android.os.SystemClock

/** Creates and keeps the server-owned SIP account in sync with the active server session. */
object ManagedTelephonyProfile {
    private val unsafeValue = Regex("[\\r\\n\\\"<>]")
    private const val registrationRefreshIntervalMillis = 15_000L
    private val lastRegistrationAttempt = mutableMapOf<Long, Long>()

    fun ensure(profile: HiroTelephonyProfile): Boolean {
        validate(profile)
        var existing = synchronized(BaresipService.uas) {
            BaresipService.uas.value.firstOrNull { Utils.uriMatch(it.account.aor, profile.aor) }
        }
        // The server address can change when a deployment moves from LAN to a
        // public edge. Remove only idle profiles that were previously issued
        // under the same managed identity; otherwise the UI keeps selecting
        // the obsolete account and shows a permanent "connecting" state.
        val stale = synchronized(BaresipService.uas) {
            BaresipService.uas.value.filter {
                !Utils.uriMatch(it.account.aor, profile.aor) &&
                    !it.account.isMobile &&
                    shouldRetireManagedProfile(
                        it.account.aor, it.account.displayName, it.account.authUser,
                        it.account.outbound.firstOrNull().orEmpty(), it.account.managedProfile, profile
                    ) &&
                    it.calls().isEmpty()
            }
        }
        stale.forEach { userAgent ->
            Api.ua_unregister(userAgent.uap)
            userAgent.remove()
            Api.ua_destroy(userAgent.uap)
            Log.i(TAG, "Removed obsolete managed telephony profile ${userAgent.account.aor}")
        }
        if (stale.isNotEmpty()) {
            Account.saveAccounts()
            existing = synchronized(BaresipService.uas) {
                BaresipService.uas.value.firstOrNull { Utils.uriMatch(it.account.aor, profile.aor) }
            }
        }
        if (existing == null) {
            val spec = buildString {
                append('"').append(profile.displayName).append("\" <").append(profile.aor).append('>')
                append(";auth_user=\"").append(profile.authUsername).append('"')
                append(";auth_pass=\"").append(profile.authPassword).append('"')
                append(";outbound=\"").append(profile.outboundProxy).append('"')
                append(";sipnat=outbound;check_origin=no;mwi=no;regq=0.5;pubint=0")
                append(";regint=").append(profile.registrationInterval)
                append(";extra=\"nickname=HI-RO Gateway;managed_profile=")
                    .append(profile.id).append(";last=empty\"")
            }
            val pointer = UserAgent.uaAlloc(spec)
            check(pointer != 0L) { "Не удалось создать телефонный профиль" }
            val userAgent = UserAgent(pointer)
            userAgent.account.nickName = profile.displayName
            userAgent.account.configuredRegInt = profile.registrationInterval
            userAgent.add()
            Account.saveAccounts()
            Api.ua_register(pointer)
            Log.i(TAG, "Created managed telephony profile ${profile.id}")
            return true
        }

        val account = existing.account
        var changed = false
        if (account.managedProfile != profile.id) {
            account.managedProfile = profile.id
            changed = true
        }
        if (account.displayName != profile.displayName) {
            check(Api.account_set_display_name(account.accp, profile.displayName) == 0)
            account.displayName = profile.displayName
            changed = true
        }
        if (account.authUser != profile.authUsername) {
            check(Api.account_set_auth_user(account.accp, profile.authUsername) == 0)
            account.authUser = profile.authUsername
            changed = true
        }
        if (account.authPass != profile.authPassword) {
            check(Api.account_set_auth_pass(account.accp, profile.authPassword) == 0)
            account.authPass = profile.authPassword
            changed = true
        }
        if (account.outbound.firstOrNull() != profile.outboundProxy) {
            check(Api.account_set_outbound(account.accp, profile.outboundProxy, 0) == 0)
            account.outbound = arrayListOf(profile.outboundProxy)
            changed = true
        }
        if (account.regint != profile.registrationInterval ||
            account.configuredRegInt != profile.registrationInterval
        ) {
            check(Api.account_set_regint(account.accp, profile.registrationInterval) == 0)
            account.regint = profile.registrationInterval
            account.configuredRegInt = profile.registrationInterval
            changed = true
        }
        if (account.nickName != profile.displayName) {
            account.nickName = profile.displayName
            changed = true
        }
        if (changed) {
            Account.saveAccounts()
            existing.reRegister()
            lastRegistrationAttempt[existing.uap] = SystemClock.elapsedRealtime()
            Log.i(TAG, "Updated managed telephony profile ${profile.id}")
        } else if (account.regint > 0 && !Api.ua_isregistered(existing.uap)) {
            val now = SystemClock.elapsedRealtime()
            val lastAttempt = lastRegistrationAttempt[existing.uap] ?: 0L
            if (now - lastAttempt >= registrationRefreshIntervalMillis) {
                lastRegistrationAttempt[existing.uap] = now
                // Native baresip already refreshes a healthy registration.
                // Re-register only after it reports a failure: forcing a new
                // REGISTER every sync cycle briefly changes the account to
                // "registering" and makes the workspace reject valid calls.
                existing.reRegister()
                Log.i(TAG, "Recovering managed telephony profile ${profile.id}")
            }
        }
        return changed
    }

    /** Stop future registration on local logout without interrupting a call. */
    fun suspendManagedRegistrations() {
        if (!BaresipService.isServiceRunning || !BaresipService.isNativeReady) return
        val managed = synchronized(BaresipService.uas) {
            BaresipService.uas.value.filter { !it.account.isMobile && it.account.managedProfile.isNotEmpty() }
        }
        var changed = false
        managed.forEach { ua ->
            if (ua.account.regint != 0) {
                Api.ua_unregister(ua.uap)
                if (Api.account_set_regint(ua.account.accp, 0) == 0) {
                    ua.account.regint = 0
                    changed = true
                }
            }
        }
        if (changed) Account.saveAccounts()
    }

    private fun validate(profile: HiroTelephonyProfile) {
        require(profile.id.isNotBlank() && !unsafeValue.containsMatchIn(profile.id))
        require(profile.displayName.isNotBlank() && !unsafeValue.containsMatchIn(profile.displayName))
        require(profile.aor.startsWith("sip:") && !unsafeValue.containsMatchIn(profile.aor))
        require(profile.authUsername.isNotBlank() && !unsafeValue.containsMatchIn(profile.authUsername))
        require(profile.authPassword.length >= 16 && !unsafeValue.containsMatchIn(profile.authPassword))
        require(profile.outboundProxy.startsWith("sip:") && !unsafeValue.containsMatchIn(profile.outboundProxy))
        require(profile.transport in setOf("tcp", "tls", "udp"))
        require(profile.registrationInterval in 60..86_400)
    }
}

// A change of authenticated session changes the SIP username. Retire only an
// idle server-owned slot at this same outbound proxy, never another server or
// arbitrary manually configured SIP account. Old versions lost their marker on
// save: the narrowly matched original 1001 template is the migration exception.
internal fun shouldRetireManagedProfile(
    oldAor: String, oldDisplayName: String, oldAuthUser: String,
    oldProxy: String, oldManagedId: String, profile: HiroTelephonyProfile
): Boolean {
    if (oldProxy != profile.outboundProxy || oldAor == profile.aor) return false
    val managed = oldManagedId == "manager-default" || oldManagedId.startsWith("personal-hmn")
    val legacy = oldAuthUser == "1001" && oldDisplayName == "HI-RO Gateway" &&
        oldAor.substringBefore('@') == "sip:1001" &&
        oldAor.substringAfter('@', "") == profile.aor.substringAfter('@', "")
    val oldSameIdentity = oldDisplayName == profile.displayName && oldAuthUser == profile.authUsername
    return managed || legacy || oldSameIdentity
}

/** Manual accounts stay available, but a server-managed account is selectable
 * only after it has been reconciled with the currently authenticated session. */
internal fun isCurrentTelephonyProfile(managedId: String, activeManagedId: String): Boolean =
    managedId.isEmpty() || (activeManagedId.isNotEmpty() && managedId == activeManagedId)
