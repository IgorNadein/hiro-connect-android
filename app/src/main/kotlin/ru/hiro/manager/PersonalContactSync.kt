package ru.hiro.manager

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun personalContactAccountKey(session: HiroSession): String =
    MessageDigest.getInstance("SHA-256").digest((session.apiBaseUrl + "\n" + session.user.id).toByteArray())
        .joinToString("") { "%02x".format(it) }

// Uses the existing encrypted session owner. Only an explicit per-account opt-in
// uploads a private phone snapshot. Native permission alone is never consent.
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class PersonalContactSync(private val app: Application, private val auth: HiroAuthViewModel, private val scope: CoroutineScope) {
    private val preferences = app.getSharedPreferences("hiro_private_contacts", 0)
    private val changes = MutableStateFlow(0L)
    private val mutableStatus = MutableStateFlow("Синхронизация личных контактов выключена")
    val status: StateFlow<String> = mutableStatus
    val sourceId: String = preferences.getString("source_id", null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString("source_id", it).apply()
    }

    private fun session() = (auth.state.value as? HiroAuthState.SignedIn)?.session
    fun enabled(): Boolean = session()?.let { preferences.getBoolean("enabled_" + personalContactAccountKey(it), false) } ?: false
    fun promptNeeded(): Boolean = session()?.let { !preferences.getBoolean("asked_" + personalContactAccountKey(it), false) } ?: false
    fun markPromptSeen() { session()?.let { preferences.edit().putBoolean("asked_" + personalContactAccountKey(it), true).apply() } }
    fun setEnabled(enabled: Boolean) {
        val s = session() ?: return
        preferences.edit().putBoolean("enabled_" + personalContactAccountKey(s), enabled).apply()
        changes.value++
    }

    fun start() {
        scope.launch {
            combine(auth.state, changes) { state, version ->
                val s = (state as? HiroAuthState.SignedIn)?.session
                Triple(s?.let(::personalContactAccountKey), s?.sessionId, version)
            }.distinctUntilChanged().collectLatest { (account, _, _) ->
                mutableStatus.value = "Синхронизация личных контактов выключена"
                if (account == null || !enabled()) return@collectLatest
                if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    mutableStatus.value = "Для синхронизации разрешите чтение контактов Android"
                    return@collectLatest
                }
                var lastSnapshot: List<HiroPersonalContact>? = null
                callbackFlow<Unit> {
                    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) { trySend(Unit) }
                    }
                    try { app.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer) }
                    catch (_: SecurityException) { close(); return@callbackFlow }
                    val ticker = launch { while (isActive) { trySend(Unit); delay(60_000) } }
                    awaitClose { ticker.cancel(); app.contentResolver.unregisterContentObserver(observer) }
                }.debounce(800).collect {
                    try {
                        if (!enabled()) return@collect
                        val scanned = withContext(Dispatchers.IO) { scanPersonalPhoneContacts(app) }
                        if (scanned == lastSnapshot) return@collect
                        mutableStatus.value = "Синхронизация личных контактов…"
                        auth.authenticatedRequest { client, signed ->
                            // A scan started for account A must never be uploaded
                            // under account B after a fast sign-out/sign-in.
                            if (personalContactAccountKey(signed) != account || !enabled()) throw HiroServerException("unauthorized", "Учётная запись изменилась")
                            val previous = client.personalContacts(signed, sourceId)
                            if (previous.sourceContacts != scanned) client.syncPersonalContacts(signed, sourceId, scanned, previous.revision)
                        }
                        lastSnapshot = scanned
                        mutableStatus.value = "Личные контакты синхронизированы: ${scanned.size}"
                    } catch (e: CancellationException) { throw e }
                    catch (e: HiroServerException) { mutableStatus.value = "Контакты не синхронизированы: ${e.userMessage}" }
                    catch (_: SecurityException) { mutableStatus.value = "Разрешение на контакты отозвано. Серверные данные не удалены." }
                    catch (_: Exception) { mutableStatus.value = "Контакты не синхронизированы. Повторим при восстановлении связи." }
                }
            }
        }
    }
}

internal fun scanPersonalPhoneContacts(app: android.content.Context): List<HiroPersonalContact> {
    val contacts = mutableListOf<HiroPersonalContact>()
    val cursor = app.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)
        ?: throw IllegalStateException("Contacts provider unavailable")
    cursor.use { while (it.moveToNext()) {
        // Never replace the server book with a silently truncated phone scan.
        if (contacts.size >= 50000) throw IllegalStateException("Phone contact capacity reached")
        contacts += HiroPersonalContact(it.getString(0) ?: "", it.getString(1) ?: "")
    } }
    val normalized = canonicalPersonalContacts(contacts)
    if (contacts.mapNotNull { normalizeClientNumber(it.number) }.distinct().size > 10000) throw IllegalStateException("Contact capacity reached")
    return normalized
}
