package ru.hiro.manager

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
internal fun PersonalContactsConsent(auth: HiroAuthViewModel, session: HiroSession) {
    val context = LocalContext.current
    var visible by remember(session.apiBaseUrl, session.user.id) { mutableStateOf(auth.personalContactsPromptNeeded()) }
    var permissionAccount by remember { mutableStateOf<String?>(null) }
    fun sameAccount(expected: String?) = (auth.state.value as? HiroAuthState.SignedIn)?.session?.let(::personalContactAccountKey) == expected && expected != null
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (sameAccount(permissionAccount)) {
            auth.markPersonalContactsPromptSeen()
            auth.setPersonalContactsSyncEnabled(granted)
        }
        permissionAccount = null
        visible = false
    }
    if (visible) AlertDialog(
        onDismissRequest = { auth.markPersonalContactsPromptSeen(); visible = false },
        title = { Text("Синхронизировать личные контакты?") },
        text = { Text("Имена и номера из адресной книги будут сохранены на вашем сервере ${session.serverUrl} как ваши личные контакты. Другие менеджеры их не увидят. Клиентом для всех контакт станет только после вашей явной отметки. Синхронизацию можно выключить в разделе контактов.") },
        confirmButton = { TextButton(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                auth.markPersonalContactsPromptSeen(); auth.setPersonalContactsSyncEnabled(true); visible = false
            } else {
                permissionAccount = personalContactAccountKey(session)
                permission.launch(Manifest.permission.READ_CONTACTS)
            }
        }) { Text("Разрешить синхронизацию") } },
        dismissButton = { TextButton(onClick = { auth.markPersonalContactsPromptSeen(); visible = false }) { Text("Не сейчас") } }
    )
}
