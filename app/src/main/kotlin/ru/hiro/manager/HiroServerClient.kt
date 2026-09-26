package ru.hiro.manager

import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HiroServerException(val code: String, val userMessage: String) : Exception(userMessage)

class HiroServerClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun normalizeServerUrl(rawValue: String): String {
        val entered = rawValue.trim().trimEnd('/')
        val raw = if ("://" in entered) entered else "https://$entered"
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            throw HiroServerException("invalid_server_url", "Некорректный адрес сервера")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null || uri.path !in setOf("", "/")) {
            throw HiroServerException("invalid_server_url", "Укажите полный адрес сервера, например https://hiro.example")
        }
        if (scheme == "http" && !BuildConfig.DEBUG) {
            throw HiroServerException("https_required", "Для рабочего сервера требуется HTTPS")
        }
        return URI(scheme, null, uri.host.lowercase(), uri.port, "", null, null).toString().trimEnd('/')
    }

    fun loginOptions(rawServerUrl: String): Pair<String, HiroLoginOptions> {
        val serverUrl = normalizeServerUrl(rawServerUrl)
        val discovery = decode<HiroDiscovery>(request(resolve(serverUrl, "/.well-known/hiro/client")))
        val options = HiroLoginOptions(
            passwordEnabled = discovery.authentication.any { it.type == "password" },
            oidcEnabled = discovery.authentication.any { it.type == "oidc" }
        )
        if (!options.passwordEnabled && !options.oidcEnabled) {
            throw HiroServerException("authentication_unsupported", "Сервер не сообщил поддерживаемый способ входа")
        }
        return serverUrl to options
    }

    fun login(rawServerUrl: String, username: String, password: String): HiroSession {
        val serverUrl = normalizeServerUrl(rawServerUrl)
        val discoveryUrl = resolve(serverUrl, "/.well-known/hiro/client")
        val discovery = decode<HiroDiscovery>(request(discoveryUrl))
        val passwordMethod = discovery.authentication.firstOrNull { it.type == "password" }
            ?: throw HiroServerException("password_unsupported", "Этот сервер не поддерживает вход по паролю")
        val endpoint = passwordMethod.loginEndpoint
            ?: throw HiroServerException("missing_login_endpoint", "Сервер не сообщил адрес входа")
        val refreshEndpoint = passwordMethod.refreshEndpoint
            ?: throw HiroServerException("persistent_session_unsupported", "Сервер не поддерживает постоянные сессии устройств")
        val logoutEndpoint = passwordMethod.logoutEndpoint
            ?: throw HiroServerException("persistent_session_unsupported", "Сервер не поддерживает отзыв сессии")
        if (!passwordMethod.persistentSession) {
            throw HiroServerException("persistent_session_unsupported", "Сервер не поддерживает постоянные сессии устройств")
        }
        val loginUrl = resolve(serverUrl, endpoint)
        val refreshUrl = resolve(serverUrl, refreshEndpoint)
        val logoutUrl = resolve(serverUrl, logoutEndpoint)
        requireSameOrigin(serverUrl, loginUrl, "Сервер попытался перенаправить пароль на другой адрес")
        requireSameOrigin(serverUrl, refreshUrl, "Сервер указал небезопасный адрес обновления сессии")
        requireSameOrigin(serverUrl, logoutUrl, "Сервер указал небезопасный адрес выхода")

        val apiBaseUrl = resolve(serverUrl, discovery.apiBase)
        requireSameOrigin(serverUrl, apiBaseUrl, "API сервера находится на другом адресе")
        val loginResponse = decode<HiroLoginResponse>(
            request(
                loginUrl,
                method = "POST",
                body = json.encodeToString(HiroLoginRequest(username.trim(), password, deviceName()))
            )
        )
        return sessionFromLoginResponse(serverUrl, apiBaseUrl, refreshUrl, logoutUrl, loginResponse)
    }

    fun prepareOIDC(rawServerUrl: String): HiroPendingOIDC {
        val serverUrl = normalizeServerUrl(rawServerUrl)
        val discovery = decode<HiroDiscovery>(request(resolve(serverUrl, "/.well-known/hiro/client")))
        val method = discovery.authentication.firstOrNull { it.type == "oidc" }
            ?: throw HiroServerException("oidc_unsupported", "Этот сервер не поддерживает единый вход")
        if (!method.persistentSession) {
            throw HiroServerException("persistent_session_unsupported", "Сервер не поддерживает постоянные сессии устройств")
        }
        val startUrl = resolve(serverUrl, method.startEndpoint
            ?: throw HiroServerException("missing_oidc_endpoint", "Сервер не сообщил адрес единого входа"))
        val ticketUrl = resolve(serverUrl, method.ticketEndpoint
            ?: throw HiroServerException("missing_oidc_endpoint", "Сервер не сообщил адрес завершения входа"))
        val refreshUrl = resolve(serverUrl, method.refreshEndpoint
            ?: throw HiroServerException("persistent_session_unsupported", "Сервер не сообщил адрес обновления сессии"))
        val logoutUrl = resolve(serverUrl, method.logoutEndpoint
            ?: throw HiroServerException("persistent_session_unsupported", "Сервер не сообщил адрес выхода"))
        val apiBaseUrl = resolve(serverUrl, discovery.apiBase)
        listOf(startUrl, ticketUrl, refreshUrl, logoutUrl, apiBaseUrl).forEach {
            requireSameOrigin(serverUrl, it, "Сервер указал небезопасный адрес авторизации")
        }
        val verifierBytes = ByteArray(48).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifierBytes)
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val separator = if (startUrl.contains('?')) "&" else "?"
        val authorizationUrl = startUrl + separator + "client=android&code_challenge=" +
            URLEncoder.encode(challenge, Charsets.UTF_8.name())
        return HiroPendingOIDC(
            serverUrl = serverUrl,
            apiBaseUrl = apiBaseUrl,
            startUrl = authorizationUrl,
            ticketEndpointUrl = ticketUrl,
            refreshEndpointUrl = refreshUrl,
            logoutEndpointUrl = logoutUrl,
            codeVerifier = verifier
        )
    }

    fun redeemOIDC(pending: HiroPendingOIDC, ticket: String): HiroSession {
        requireSameOrigin(pending.serverUrl, pending.ticketEndpointUrl, "Некорректный адрес завершения входа")
        val response = decode<HiroLoginResponse>(
            request(
                pending.ticketEndpointUrl,
                method = "POST",
                body = json.encodeToString(HiroOIDCTicketRequest(ticket.trim(), pending.codeVerifier))
            )
        )
        return sessionFromLoginResponse(
            pending.serverUrl,
            pending.apiBaseUrl,
            pending.refreshEndpointUrl,
            pending.logoutEndpointUrl,
            response
        )
    }

    private fun sessionFromLoginResponse(
        serverUrl: String,
        apiBaseUrl: String,
        refreshUrl: String,
        logoutUrl: String,
        loginResponse: HiroLoginResponse
    ): HiroSession {
        if (!loginResponse.tokenType.equals("Bearer", ignoreCase = true) ||
            loginResponse.accessToken.isBlank() || loginResponse.refreshToken.isBlank() || loginResponse.sessionId.isBlank()
        ) {
            throw HiroServerException("invalid_session", "Сервер вернул неподдерживаемую сессию")
        }
        val expiresAt = runCatching { Instant.parse(loginResponse.expiresAt) }.getOrNull()
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw HiroServerException("invalid_session", "Сервер вернул некорректный срок сессии")
        }
        val session = HiroSession(
            serverUrl = serverUrl,
            apiBaseUrl = apiBaseUrl,
            accessToken = loginResponse.accessToken,
            refreshToken = loginResponse.refreshToken,
            expiresAt = loginResponse.expiresAt,
            sessionId = loginResponse.sessionId,
            refreshEndpointUrl = refreshUrl,
            logoutEndpointUrl = logoutUrl,
            user = loginResponse.user
        )
        return validate(session)
    }

    fun validate(session: HiroSession): HiroSession {
        val meUrl = resolve(session.apiBaseUrl + "/", "auth/me")
        val response = decode<HiroMeResponse>(request(meUrl, bearerToken = session.accessToken))
        return session.copy(user = response.user)
    }

    fun refresh(session: HiroSession): HiroSession {
        requireSameOrigin(session.serverUrl, session.refreshEndpointUrl, "Некорректный адрес обновления сессии")
        val response = decode<HiroLoginResponse>(
            request(
                session.refreshEndpointUrl,
                method = "POST",
                body = json.encodeToString(HiroRefreshRequest(session.refreshToken))
            )
        )
        if (!response.tokenType.equals("Bearer", ignoreCase = true) ||
            response.accessToken.isBlank() || response.refreshToken.isBlank() || response.sessionId != session.sessionId
        ) {
            throw HiroServerException("invalid_session", "Сервер вернул неподдерживаемую сессию")
        }
        val expiresAt = runCatching { Instant.parse(response.expiresAt) }.getOrNull()
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            throw HiroServerException("invalid_session", "Сервер вернул некорректный срок токена")
        }
        return session.copy(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAt = response.expiresAt,
            user = response.user
        )
    }

    fun logout(session: HiroSession) {
        requireSameOrigin(session.serverUrl, session.logoutEndpointUrl, "Некорректный адрес выхода")
        request(
            session.logoutEndpointUrl,
            method = "POST",
            body = json.encodeToString(HiroRefreshRequest(session.refreshToken))
        )
    }

    fun smsMessages(session: HiroSession, afterId: Long = 0, limit: Int = 500): HiroSmsMessagesResponse {
        val safeLimit = limit.coerceIn(1, 500)
        var cursor = afterId.coerceAtLeast(0)
        val items = mutableListOf<HiroSmsMessage>()
        while (true) {
            val endpoint = resolve(session.apiBaseUrl + "/", "sms/messages?after_id=$cursor&limit=$safeLimit")
            requireSameOrigin(session.serverUrl, endpoint, "Некорректный адрес SMS API")
            val page = decode<HiroSmsMessagesResponse>(request(endpoint, bearerToken = session.accessToken))
            items += page.items
            if (page.items.size < safeLimit || page.nextAfterId <= cursor) {
                return HiroSmsMessagesResponse(items, page.nextAfterId.coerceAtLeast(cursor))
            }
            cursor = page.nextAfterId
        }
    }

    fun gatewayStatus(session: HiroSession): HiroGatewayStatus {
        val endpoint = resolve(session.apiBaseUrl + "/", "status")
        requireSameOrigin(session.serverUrl, endpoint, "Некорректный адрес API состояния")
        return decode(request(endpoint, bearerToken = session.accessToken))
    }

    fun smsOutbox(session: HiroSession): HiroSmsOutboxResponse {
        val endpoint = resolve(session.apiBaseUrl + "/", "sms/outbox")
        requireSameOrigin(session.serverUrl, endpoint, "Некорректный адрес SMS API")
        return decode(request(endpoint, bearerToken = session.accessToken))
    }

    fun sendSms(
        session: HiroSession,
        clientMessageId: String,
        address: String,
        text: String,
        subscriptionId: Long? = null
    ): HiroSmsSendResponse {
        val endpoint = resolve(session.apiBaseUrl + "/", "sms/messages")
        requireSameOrigin(session.serverUrl, endpoint, "Некорректный адрес SMS API")
        return decode(
            request(
                endpoint,
                method = "POST",
                body = json.encodeToString(
                    HiroSmsSendRequest(clientMessageId, address.trim(), text, subscriptionId)
                ),
                bearerToken = session.accessToken
            )
        )
    }

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString(body)
    } catch (_: Exception) {
        throw HiroServerException("invalid_response", "Сервер вернул ответ неизвестного формата")
    }

    private fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        bearerToken: String? = null
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 7_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HI-RO Connect/${BuildConfig.VERSION_NAME}")
            if (bearerToken != null) setRequestProperty("Authorization", "Bearer $bearerToken")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toByteArray(Charsets.UTF_8)
                setFixedLengthStreamingMode(bytes.size)
                outputStream.use { it.write(bytes) }
            }
        }
        return try {
            val status = connection.responseCode
            val response = readLimited(if (status in 200..299) connection.inputStream else connection.errorStream)
            if (status !in 200..299) {
                val serverMessage = runCatching { json.decodeFromString<HiroApiErrorEnvelope>(response).error?.message }.getOrNull()
                val message = when (status) {
                    401 -> if (bearerToken == null) "Неверный логин или пароль" else "Сессия завершена"
                    429 -> "Слишком много попыток. Попробуйте позже"
                    else -> serverMessage ?: "Сервер вернул ошибку $status"
                }
                throw HiroServerException(if (status == 401) "unauthorized" else "http_error", message)
            }
            response
        } catch (error: HiroServerException) {
            throw error
        } catch (_: java.net.SocketTimeoutException) {
            throw HiroServerException("network", "Сервер не ответил вовремя")
        } catch (_: javax.net.ssl.SSLException) {
            throw HiroServerException("tls", "Не удалось проверить защищённое соединение с сервером")
        } catch (_: Exception) {
            throw HiroServerException("network", "Не удалось подключиться к серверу")
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream?): String {
        if (input == null) return ""
        val output = ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_RESPONSE_BYTES) {
                    throw HiroServerException("response_too_large", "Ответ сервера слишком большой")
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun resolve(base: String, path: String): String {
        val baseUri = URI(if (base.endsWith('/')) base else "$base/")
        return baseUri.resolve(path).toString().trimEnd('/')
    }

    private fun requireSameOrigin(serverUrl: String, targetUrl: String, message: String) {
        val server = URI(serverUrl)
        val target = URI(targetUrl)
        val serverPort = effectivePort(server)
        val targetPort = effectivePort(target)
        if (!server.scheme.equals(target.scheme, true) || !server.host.equals(target.host, true) || serverPort != targetPort) {
            throw HiroServerException("cross_origin", message)
        }
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", true) -> 443
        else -> 80
    }

    private fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.trim()
        val model = Build.MODEL.trim()
        return listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").take(120)
            .ifBlank { "Android device" }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 1024 * 1024
    }
}
