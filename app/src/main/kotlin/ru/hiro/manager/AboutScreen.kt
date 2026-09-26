package ru.hiro.manager

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import java.io.File

fun NavGraphBuilder.aboutScreenRoute(navController: NavController) {
    composable("about") { AboutScreen(onBack = { navController.navigateUp() }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updates = remember(context.applicationContext, scope) {
        ConnectAppUpdates(context.applicationContext, scope)
    }
    val updateState by updates.state.collectAsState()
    val version = BuildConfig.VERSION_NAME
    val aboutText = stringResource(R.string.hiro_about_text, version)
    val errorColor = MaterialTheme.colorScheme.error
    val onBackground = MaterialTheme.colorScheme.onBackground
    val annotatedText = remember(aboutText, errorColor) {
        AnnotatedString.fromHtml(
            htmlString = aboutText,
            linkStyles = TextLinkStyles(SpanStyle(color = errorColor))
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                Spacer(Modifier.statusBarsPadding())
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.hiro_about_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    },
                    colors = hiroTopAppBarColors(),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UpdatesCard(
                state = updateState,
                onCheck = updates::check,
                onDownload = updates::download,
                onCancel = updates::cancel
            )
            Text(text = annotatedText, color = onBackground)
        }
    }
}

@Composable
private fun UpdatesCard(
    state: ConnectUpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val current = remember { ConnectGitHubUpdates.currentVersion(context) }
    val release = state.release
    val available = release != null && release.versionCode > current.second
    var installMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!state.checked && !state.checking) onCheck()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.updates_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when {
                    state.checking -> stringResource(R.string.updates_checking)
                    available -> stringResource(
                        R.string.updates_available,
                        requireNotNull(release).versionName
                    )
                    state.checked && state.error == null ->
                        stringResource(R.string.updates_latest)
                    else -> stringResource(R.string.updates_from_github)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.updates_current, current.first),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.checking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            installMessage?.let { message ->
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }

            when {
                state.downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(
                                R.string.updates_download_progress,
                                (state.progress * 100).toInt()
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                }
                available && state.apkPath != null -> {
                    Button(onClick = {
                        runCatching {
                            ConnectGitHubUpdates.install(context, File(state.apkPath))
                        }.onSuccess { installerOpened ->
                            installMessage = if (installerOpened) {
                                null
                            } else {
                                context.getString(R.string.updates_allow_install)
                            }
                        }.onFailure { error ->
                            installMessage = error.message
                                ?: context.getString(R.string.updates_install_failed)
                        }
                    }) {
                        Text(stringResource(R.string.updates_install))
                    }
                }
                available -> {
                    Button(onClick = onDownload, enabled = !state.checking) {
                        Text(stringResource(R.string.updates_download))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCheck,
                    enabled = !state.checking && !state.downloading
                ) {
                    Text(stringResource(R.string.updates_check))
                }
                TextButton(onClick = {
                    val uri = Uri.parse(release?.pageUrl ?: ConnectProjectLinks.releases)
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }.onFailure {
                        installMessage = context.getString(R.string.updates_browser_failed)
                    }
                }) {
                    Text(stringResource(R.string.updates_whats_new))
                }
            }
        }
    }
}
