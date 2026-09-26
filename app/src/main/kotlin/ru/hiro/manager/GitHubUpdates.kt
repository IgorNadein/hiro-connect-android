package ru.hiro.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal object ConnectProjectLinks {
    const val repository = "https://github.com/IgorNadein/hiro-connect-android"
    const val releases = "$repository/releases/latest"
}

internal data class ConnectGitHubRelease(
    val versionName: String,
    val versionCode: Long,
    val pageUrl: String,
    val apkUrl: String,
    val size: Long,
    val digest: String?
)

internal data class ConnectUpdateState(
    val checking: Boolean = false,
    val checked: Boolean = false,
    val release: ConnectGitHubRelease? = null,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val apkPath: String? = null,
    val error: String? = null
)

internal object ConnectGitHubUpdates {
    private const val MAX_APK_BYTES = 250L * 1024 * 1024
    private const val API = "https://api.github.com/repos/IgorNadein/hiro-connect-android/releases/latest"

    fun currentVersion(context: Context): Pair<String, Long> =
        context.packageManager.getPackageInfo(context.packageName, 0).let {
            it.versionName.orEmpty() to versionCode(it)
        }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    fun parseRelease(json: JSONObject): ConnectGitHubRelease {
        require(!json.optBoolean("draft") && !json.optBoolean("prerelease")) {
            "Release is not stable"
        }
        val versionName = json.getString("tag_name").removePrefix("v")
        require(versionName.isNotBlank()) { "Release version is missing" }
        val versionCode = Regex("(?im)^Version code:\\s*(\\d+)\\s*$")
            .find(json.optString("body"))
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
        require(versionCode != null && versionCode in 1..Int.MAX_VALUE.toLong()) {
            "Release build number is missing"
        }

        val pageUrl = json.getString("html_url")
        require(pageUrl.startsWith("${ConnectProjectLinks.repository}/releases/tag/")) {
            "Invalid release page"
        }
        val assets = json.getJSONArray("assets")
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .singleOrNull {
                it.optString("name").startsWith("HI-RO-Connect-") &&
                    it.optString("name").endsWith(".apk")
            } ?: error("Release must contain exactly one HI-RO Connect APK")
        val apkUrl = apk.getString("browser_download_url")
        require(apkUrl.startsWith("${ConnectProjectLinks.repository}/releases/download/")) {
            "Invalid APK address"
        }
        val size = apk.getLong("size")
        require(size in 1..MAX_APK_BYTES) { "Invalid APK size" }
        val digest = apk.optString("digest")
            .takeIf { it.startsWith("sha256:") }
            ?.removePrefix("sha256:")
        require(digest == null || digest.matches(Regex("[a-fA-F0-9]{64}"))) {
            "Invalid APK checksum"
        }
        return ConnectGitHubRelease(versionName, versionCode, pageUrl, apkUrl, size, digest)
    }

    suspend fun latest(): ConnectGitHubRelease = withContext(Dispatchers.IO) {
        val connection = connect(API, "application/vnd.github+json")
        try {
            when (val responseCode = connection.responseCode) {
                404 -> error("No public release is available yet")
                403, 429 -> error("GitHub temporarily limited update checks")
                !in 200..299 -> error("GitHub is unavailable (HTTP $responseCode)")
            }
            val bytes = connection.inputStream.use { it.readBytesLimited(2 * 1024 * 1024) }
            parseRelease(JSONObject(bytes.toString(Charsets.UTF_8)))
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(
        context: Context,
        release: ConnectGitHubRelease,
        progress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "HI-RO-Connect-${release.versionCode}.apk")
        val partial = File(directory, "HI-RO-Connect-${release.versionCode}.part")
        val connection = connect(release.apkUrl, "application/octet-stream")
        try {
            require(connection.responseCode in 200..299) {
                "Unable to download APK (HTTP ${connection.responseCode})"
            }
            val hash = MessageDigest.getInstance("SHA-256")
            var total = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val length = input.read(buffer)
                        if (length < 0) break
                        total += length
                        require(total <= release.size && total <= MAX_APK_BYTES) {
                            "Downloaded APK size does not match the release"
                        }
                        hash.update(buffer, 0, length)
                        output.write(buffer, 0, length)
                        val percent = (total * 100 / release.size).toInt()
                        if (percent != lastPercent) {
                            progress(percent / 100f)
                            lastPercent = percent
                        }
                    }
                }
            }
            require(total == release.size) { "APK download is incomplete" }
            val actualHash = hash.digest().joinToString("") { "%02x".format(it) }
            require(release.digest == null || actualHash.equals(release.digest, true)) {
                "APK checksum does not match"
            }
            validateApk(context, partial, release.versionCode)
            if (target.exists()) target.delete()
            require(partial.renameTo(target)) { "Unable to store the update" }
            directory.listFiles()?.filter { it != target }?.forEach { it.delete() }
            target
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    @Suppress("DEPRECATION")
    fun validateApk(context: Context, file: File, expectedVersionCode: Long? = null) {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val candidate = context.packageManager.getPackageArchiveInfo(file.path, flags)
            ?: error("Downloaded file is not an APK")
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        validateIdentity(installed, candidate, expectedVersionCode)
    }

    @Suppress("DEPRECATION")
    internal fun validateIdentity(
        installed: PackageInfo,
        candidate: PackageInfo,
        expectedVersionCode: Long? = null
    ) {
        require(candidate.packageName == installed.packageName) {
            "APK belongs to another application"
        }
        require(versionCode(candidate) > versionCode(installed)) {
            "This version is already installed or outdated"
        }
        require(expectedVersionCode == null || versionCode(candidate) == expectedVersionCode) {
            "APK version does not match the release"
        }
        fun signers(info: PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) {
                info.signingInfo?.apkContentsSigners
            } else {
                info.signatures
            }).orEmpty().map { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.toSet()
        val installedSigners = signers(installed)
        require(installedSigners.isNotEmpty() && installedSigners == signers(candidate)) {
            "APK signature differs from the installed application"
        }
    }

    /** Returns false if Android must first allow installs from this application. */
    fun install(context: Context, file: File): Boolean {
        validateApk(context, file)
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    private fun connect(address: String, accept: String): HttpURLConnection =
        (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "HI-RO-Connect-Android-Updater")
        }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val length = read(buffer)
            if (length < 0) break
            require(output.size() + length <= limit) { "GitHub response is too large" }
            output.write(buffer, 0, length)
        }
        return output.toByteArray()
    }
}

internal class ConnectAppUpdates(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(ConnectUpdateState())
    val state = mutableState.asStateFlow()
    private var downloadJob: Job? = null

    fun check() {
        if (mutableState.value.checking || mutableState.value.downloading) return
        mutableState.update { it.copy(checking = true, error = null) }
        scope.launch {
            try {
                val release = ConnectGitHubUpdates.latest()
                mutableState.update { previous ->
                    previous.copy(
                        checking = false,
                        checked = true,
                        release = release,
                        apkPath = previous.apkPath?.takeIf { path ->
                            previous.release?.versionCode == release.versionCode && File(path).exists()
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        checking = false,
                        checked = true,
                        error = error.message ?: "Unable to check for updates"
                    )
                }
            }
        }
    }

    fun download() {
        val release = mutableState.value.release ?: return
        if (mutableState.value.downloading) return
        mutableState.update { it.copy(downloading = true, progress = 0f, error = null) }
        downloadJob = scope.launch {
            try {
                val file = ConnectGitHubUpdates.download(context, release) { progress ->
                    mutableState.update { it.copy(progress = progress) }
                }
                mutableState.update { it.copy(apkPath = file.path) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(error = error.message ?: "Unable to download update")
                }
            } finally {
                mutableState.update { it.copy(downloading = false) }
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
    }
}
