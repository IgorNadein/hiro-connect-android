package ru.hiro.manager

// A provider-only PBX does not need an Android phone connected by USB/Bluetooth.
// Explicit assignments still describe expected hardware even when it is offline.
fun HiroGatewayStatus.hasConfiguredPhoneGateway(): Boolean =
    assignments.any { it.role.equals("gateway", ignoreCase = true) } ||
        bluetooth.gateway.deviceId.isNotBlank() || bluetooth.gateway.connected ||
        bluetooth.connected.isNotEmpty() || usb.ready || usb.devices.isNotEmpty()
