package ru.hiro.manager

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HiroAuthMethod(
    val type: String,
    @SerialName("login_endpoint") val loginEndpoint: String? = null
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
    val password: String
)

@Serializable
data class HiroLoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("expires_at") val expiresAt: String,
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
    val expiresAt: String,
    val user: HiroUser
)
