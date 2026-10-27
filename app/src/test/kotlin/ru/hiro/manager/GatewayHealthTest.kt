package ru.hiro.manager

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GatewayHealthTest {
    @Test fun providerOnlyStatusDoesNotRequirePhoneHardware() {
        val status = Json { ignoreUnknownKeys = true }.decodeFromString<HiroGatewayStatus>("""
            {"state":"degraded","assignments":[],"bluetooth":{"connected":[],
             "gateway":{"available":false,"connected":false}},"usb":{"devices":[],"ready":false}}
        """.trimIndent())
        assertFalse(status.hasConfiguredPhoneGateway())
    }

    @Test fun disconnectedAssignedPhoneStillRequiresWarning() {
        assertTrue(HiroGatewayStatus(assignments = listOf(
            HiroDeviceAssignment(id = "office", role = "gateway", adbSerial = "device")
        )).hasConfiguredPhoneGateway())
    }

    @Test fun legacyDisconnectedMobileDeviceStillRequiresWarning() {
        assertTrue(HiroGatewayStatus(bluetooth = HiroBluetoothStatus(
            gateway = HiroMobileGateway(available = true, deviceId = "phone")
        )).hasConfiguredPhoneGateway())
    }

    @Test fun smsPollingDependsOnCapabilityAndKeepsOldServerCompatibility() {
        assertFalse(HiroGatewayStatus(components = HiroServiceComponents(sms = "not_configured")).shouldSyncSms("manager"))
        assertTrue(HiroGatewayStatus(components = HiroServiceComponents(sms = "configured")).shouldSyncSms("manager"))
        assertTrue(HiroGatewayStatus().shouldSyncSms("manager"))
        assertFalse(HiroGatewayStatus().shouldSyncSms("viewer"))
    }
}
