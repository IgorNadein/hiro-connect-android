package ru.hiro.manager

import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HiroServerException(val code: String, val userMessage: String) : Exception(userMessage)

class HiroServerClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun normalizeServerUrl(rawValue: String): String {
        val raw = rawValue.trim().trimEnd('/')
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
