package ru.hiro.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HiroAuthMethod(
    val type: String,
    @SerialName("login_endpoint") val loginEndpoint: String? = null,
    @SerialName("refresh_endpoint") val refreshEndpoint: String? = null,
    @SerialName("logout_endpoint") val logoutEndpoint: String? = null,
    @SerialName("persistent_session") val persistentSession: Boolean = false
)

@Serializable
data class HiroDiscovery(
    @SerialName("api_base") val apiBase: String,
    val authentication: List<HiroAuthMethod>
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
