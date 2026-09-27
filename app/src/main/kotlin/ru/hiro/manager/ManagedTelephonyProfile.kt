package ru.hiro.manager

/** Creates and keeps the server-owned SIP account in sync with the active server session. */
object ManagedTelephonyProfile {
    private val unsafeValue = Regex("[\\r\\n\\\"<>]")

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
                    it.account.displayName == profile.displayName &&
                    it.account.authUser == profile.authUsername &&
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
            Log.i(TAG, "Updated managed telephony profile ${profile.id}")
        }
        return changed
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
