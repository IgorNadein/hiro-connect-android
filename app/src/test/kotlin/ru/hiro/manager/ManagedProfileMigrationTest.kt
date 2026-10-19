package ru.hiro.manager

import org.junit.Assert.*
import org.junit.Test

class ManagedProfileMigrationTest {
    private val profile = HiroTelephonyProfile(
        id = "personal-hmn0123456789abcdef01234567", displayName = "HI-RO Gateway",
        aor = "sip:hmn0123456789abcdef01234567@gateway.example.test",
        authUsername = "hmn0123456789abcdef01234567", authPassword = "fixture-test-password-0123456789",
        outboundProxy = "sip:gateway.example.test:15060;transport=tcp", transport = "tcp",
        registrationInterval = 120
    )
    @Test fun legacySharedTemplateIsRetiredAtTheSameServer() {
        assertTrue(shouldRetireManagedProfile("sip:1001@gateway.example.test", "HI-RO Gateway", "1001", profile.outboundProxy, "", profile))
    }
    @Test fun personalSessionChangeRetiresPreviousManagedIdentity() {
        assertTrue(shouldRetireManagedProfile("sip:hmn-old@gateway.example.test", "HI-RO Gateway", "hmn-old", profile.outboundProxy, "personal-hmnold", profile))
    }
    @Test fun anotherServerAndManualAccountsArePreserved() {
        assertFalse(shouldRetireManagedProfile("sip:1001@other.test", "HI-RO Gateway", "1001", "sip:other.test:15060;transport=tcp", "manager-default", profile))
        assertFalse(shouldRetireManagedProfile("sip:office@gateway.example.test", "Office", "office", profile.outboundProxy, "", profile))
        assertFalse(shouldRetireManagedProfile("sip:1001@other.test", "HI-RO Gateway", "1001", profile.outboundProxy, "", profile))
    }
    @Test fun currentAccountIsNotRetired() {
        assertFalse(shouldRetireManagedProfile(profile.aor, profile.displayName, profile.authUsername, profile.outboundProxy, profile.id, profile))
    }
}
