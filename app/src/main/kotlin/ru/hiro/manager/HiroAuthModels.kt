package ru.hiro.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HiroAuthMethod(
    val type: String,
    val name: String? = null,
    @SerialName("login_endpoint") val loginEndpoint: String? = null,
    @SerialName("start_endpoint") val startEndpoint: String? = null,
    @SerialName("ticket_endpoint") val ticketEndpoint: String? = null,
    @SerialName("refresh_endpoint") val refreshEndpoint: String? = null,
    @SerialName("logout_endpoint") val logoutEndpoint: String? = null,
    @SerialName("persistent_session") val persistentSession: Boolean = false
)

@Serializable
data class HiroDiscovery(
    @SerialName("api_base") val apiBase: String,
    val authentication: List<HiroAuthMethod>
)

data class HiroLoginOptions(
    val passwordEnabled: Boolean,
    val oidcEnabled: Boolean
)

@Serializable
data class HiroUser(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val role: String
)

@Serializable
data class HiroLoginRequest(
    val username: String,
    val password: String,
    @SerialName("device_name") val deviceName: String
)

@Serializable
data class HiroRefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class HiroOIDCTicketRequest(
    val ticket: String,
    @SerialName("code_verifier") val codeVerifier: String
)

@Serializable
data class HiroLoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("session_id") val sessionId: String,
    val user: HiroUser
)

@Serializable
data class HiroMeResponse(val user: HiroUser)

@Serializable
data class HiroApiErrorEnvelope(val error: HiroApiError? = null)

@Serializable
data class HiroApiError(val code: String, val message: String)

@Serializable
data class HiroSession(
    val serverUrl: String,
    val apiBaseUrl: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val sessionId: String,
    val refreshEndpointUrl: String,
    val logoutEndpointUrl: String,
    val user: HiroUser
)

@Serializable
data class HiroSmsMessage(
    val id: Long,
    @SerialName("platform_id") val platformId: Long? = null,
    @SerialName("thread_id") val threadId: Long? = null,
    val address: String? = null,
    val text: String? = null,
    val timestamp: Long? = null,
    @SerialName("sent_at") val sentAt: Long? = null,
    val read: Boolean? = null,
    val direction: String? = null,
    val status: String? = null,
    @SerialName("status_code") val statusCode: Int? = null,
    @SerialName("error_code") val errorCode: Int? = null,
    @SerialName("subscription_id") val subscriptionId: Long? = null,
    val kind: String = "sms"
)

@Serializable
data class HiroSmsMessagesResponse(
    val items: List<HiroSmsMessage>,
    @SerialName("next_after_id") val nextAfterId: Long = 0
)

@Serializable
data class HiroSmsSendRequest(
    @SerialName("client_message_id") val clientMessageId: String,
    val address: String,
    val text: String,
    @SerialName("subscription_id") val subscriptionId: Long? = null
)

@Serializable
data class HiroSmsSendResponse(
    @SerialName("client_message_id") val clientMessageId: String,
    @SerialName("local_message_id") val localMessageId: Long? = null,
    val status: String,
    val reason: String? = null,
    val duplicate: Boolean = false
)

@Serializable
data class HiroSmsOutboxMessage(
    @SerialName("client_message_id") val clientMessageId: String,
    val address: String,
    val text: String,
    @SerialName("subscription_id") val subscriptionId: Long? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val attempts: Int = 0,
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("local_message_id") val localMessageId: Long? = null,
    @SerialName("submitted_at") val submittedAt: String? = null
)

@Serializable
data class HiroSmsOutboxResponse(val items: List<HiroSmsOutboxMessage>)

@Serializable
data class HiroGatewayStatus(
    val state: String = "idle",
    val bluetooth: HiroBluetoothStatus = HiroBluetoothStatus(),
    val usb: HiroUsbStatus = HiroUsbStatus(),
    val assignments: List<HiroDeviceAssignment> = emptyList()
)

@Serializable
data class HiroBluetoothStatus(
    val connected: List<HiroBluetoothDevice> = emptyList(),
    val gateway: HiroMobileGateway = HiroMobileGateway()
)

@Serializable
data class HiroBluetoothDevice(
    val address: String = "",
    val name: String = ""
)

@Serializable
data class HiroMobileGateway(
    val available: Boolean = false,
    val connected: Boolean = false,
    val deviceId: String = ""
)

@Serializable
data class HiroUsbStatus(
    val devices: List<HiroAdbDevice> = emptyList(),
    val ready: Boolean = false
)

@Serializable
data class HiroAdbDevice(
    val serial: String = "",
    val state: String = "",
    val model: String = ""
)

@Serializable
data class HiroDeviceAssignment(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val adbSerial: String = "",
    val bluetoothAddress: String = ""
)

@Serializable
data class HiroPendingOIDC(
    val serverUrl: String,
    val apiBaseUrl: String,
    val startUrl: String,
    val ticketEndpointUrl: String,
    val refreshEndpointUrl: String,
    val logoutEndpointUrl: String,
    val codeVerifier: String
)
