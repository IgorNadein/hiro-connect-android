package ru.hiro.manager

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiroLoginScreen(
    serverUrl: String,
    error: String?,
    signingIn: Boolean,
    options: HiroLoginOptions?,
    onSelectServer: (String) -> Unit,
    onSignIn: (String, String, String) -> Unit,
    onOIDCSignIn: (String) -> Unit
) {
    var server by rememberSaveable(serverUrl) { mutableStateOf(serverUrl) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val activeOptions = options.takeIf {
        server.trim().trimEnd('/') == serverUrl.trim().trimEnd('/')
    }

    LaunchedEffect(error) {
        if (error != null) password = ""
    }

    fun submitPassword() {
        if (activeOptions?.passwordEnabled == true && username.isNotBlank() && password.isNotBlank() && !signingIn) {
            onSignIn(server, username, password)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.widthIn(max=460.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text("HI-RO",color=MaterialTheme.colorScheme.primary,fontSize=38.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1.5).sp)
                Text("Ваше рабочее\nпространство связи",style=MaterialTheme.typography.headlineSmall)
                Text("Звонки, сообщения и клиенты — в одном месте.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HiroAvatar("",icon=Icons.Outlined.Dns)
                    Column {
                        Text("Сервер организации",style=MaterialTheme.typography.titleMedium)
                        Text("Личный доступ к рабочей связи",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Адрес сервера") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(13.dp),
                    enabled = !signingIn,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (server.isNotBlank() && !signingIn) onSelectServer(server)
                    })
                )

                if (activeOptions == null) {
                    Button(
                        onClick = { onSelectServer(server) },
                        enabled = !signingIn && server.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min=50.dp),
                        shape=RoundedCornerShape(13.dp)
                    ) {
                        if (signingIn) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp),color=MaterialTheme.colorScheme.onPrimary,strokeWidth=2.dp)
                        } else {
                            Text("Продолжить")
                        }
                    }
                } else {
                    if (activeOptions.oidcEnabled) {
                        Button(
                            onClick = { onOIDCSignIn(server) },
                            enabled = !signingIn,
                            modifier = Modifier.fillMaxWidth().heightIn(min=50.dp),
                            shape=RoundedCornerShape(13.dp)
                        ) {
                            if (signingIn) {
                                CircularProgressIndicator(Modifier.size(22.dp), color=MaterialTheme.colorScheme.onPrimary, strokeWidth=2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else if (isCorpProvider(activeOptions.oidcProviderName)) {
                                Image(painterResource(R.drawable.corp_icon), contentDescription=null, modifier=Modifier.size(28.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(oidcButtonLabel(activeOptions.oidcProviderName))
                        }
                    }
                    if (activeOptions.oidcEnabled && activeOptions.passwordEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text("или локальная учётная запись", style = MaterialTheme.typography.labelSmall)
                            HorizontalDivider(Modifier.weight(1f))
                        }
                    }
                    if (activeOptions.passwordEnabled) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Логин") },
                            singleLine = true,
                            enabled = !signingIn,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Пароль") },
                            singleLine = true,
                            enabled = !signingIn,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitPassword() })
                        )
                    }
                }

                if (error != null) {
                    WorkspaceInfo(error,true)
                }
                if (activeOptions?.passwordEnabled == true) {
                    Button(
                        onClick = { submitPassword() },
                        enabled = !signingIn && username.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min=50.dp),
                        shape=RoundedCornerShape(13.dp)
                    ) {
                        Text("Войти")
                    }
                    Text(
                        "Пароль используется только для входа и не сохраняется. Токен сессии защищён Android Keystore.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.Top){
                Icon(Icons.Outlined.Shield,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("У каждой организации свой сервер и правила доступа. Используйте адрес, который вам предоставили.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun HiroAuthLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator()
            Text("Проверяем сессию…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
