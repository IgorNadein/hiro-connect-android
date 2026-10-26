package ru.hiro.manager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class HiroCallLogItem(val key:String,val name:String,val number:String,val time:String,val incoming:Boolean,val missed:Boolean)

/** Presentation only. Authorization, registration and dialing remain with the caller. */
@Composable
internal fun HiroDialer(
    number: String,
    onNumber: (String) -> Unit,
    lineOptions: List<Pair<String, String>>,
    line: String,
    onLine: (String) -> Unit,
    sipOptions: List<Pair<String, String>>,
    aor: String,
    onAor: (String) -> Unit,
    canCall: Boolean,
    error: String,
    employees: List<HiroEmployee>,
    currentUserId: String,
    onEmployee: (HiroEmployee) -> Unit,
    onCall: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { WorkspaceHeader("Набрать номер", "Выберите линию до начала вызова", onBack = onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Button(
                        onClick = onCall,
                        enabled = canCall,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(Icons.Outlined.Phone, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Позвонить")
                    }
                    Text("Явный выбор линии действует только на этот звонок.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (error.isNotEmpty()) item { WorkspaceInfo(error, true) }
            item { RoutingChoice("Линия для этого звонка", lineOptions, line, onLine) }
            if (sipOptions.size > 1) item { RoutingChoice("Рабочее SIP-подключение", sipOptions, aor, onAor) }
            item { HiroKeypad(number, onNumber) }
            item { Text("Введите номер с кодом страны или внутренний номер сотрудника. 600 — локальная проверка звука.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (employees.isNotEmpty()) {
                item { HiroSection("Сотрудники", "${employees.size}") }
                itemsIndexed(employees, key = { _, employee -> employee.id }) { _, employee ->
                    Surface(onClick = { onEmployee(employee) }, color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(16.dp)) {
                        HiroPersonRow(
                            employee.displayName,
                            employee.extension,
                            when {
                                employee.id == currentUserId -> "Ваш внутренний номер"
                                employee.available -> "Доступен для внутреннего звонка"
                                else -> "Сейчас не подключён"
                            }
                        ) { onEmployee(employee) }
                    }
                }
            }
        }
    }
}

@Composable internal fun HiroCallsFeed(
    padding:PaddingValues,registered:Boolean,rows:List<HiroCallLogItem>,query:String,missedOnly:Boolean,
    onQuery:(String)->Unit,onFilter:(Boolean)->Unit,onDial:()->Unit,onEmployees:()->Unit,onHelp:()->Unit,onOpen:(String)->Unit
) {
    val filtered=rows.filter{(!missedOnly || it.missed) && (it.name.contains(query,true)||it.number.contains(query))}
    LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        HiroAvatar("",icon=Icons.Outlined.Headset)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(if(registered)"Телефония подключена" else "Нет регистрации в телефонии",style=MaterialTheme.typography.titleMedium)
                            Text(if(registered)"Ваше рабочее подключение" else "Проверьте интернет и соединение",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if(registered) Button(onClick=onDial,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)) {
                        Icon(Icons.Outlined.Dialpad,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Набрать номер")
                    } else Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick=onHelp,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Проверить связь")}
                        OutlinedButton(onClick=onDial,modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text("Набрать номер")}
                    }
                    TextButton(onClick=onEmployees,modifier=Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Badge,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text("Сотрудники и внутренние номера")
                    }
                }
            }
        }
        item { WorkspaceSearch(query,onQuery,"Имя или номер") }
        item { HiroSegments(listOf("Все звонки","Пропущенные"),if(missedOnly)1 else 0){onFilter(it==1)} }
        item { HiroSection("Журнал этого смартфона",if(filtered.isEmpty())"" else "${filtered.size}") }
        if(filtered.isEmpty())item { HiroEmpty(Icons.Outlined.History,if(query.isNotBlank())"Ничего не найдено" else if(missedOnly)"Нет пропущенных" else "Звонков пока нет",
            if(query.isNotBlank())"Попробуйте другое имя или номер." else if(missedOnly)"Здесь будут звонки, на которые вы не ответили." else "Входящие и исходящие звонки появятся здесь после разговора.") }
        itemsIndexed(filtered,key={_,row->row.key}){i,row ->
            Surface(color=MaterialTheme.colorScheme.surfaceContainerLowest,shape=RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(start=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(if(row.missed)Icons.Outlined.CallMissed else if(row.incoming)Icons.Outlined.CallReceived else Icons.Outlined.CallMade,null,
                        tint=if(row.missed)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(18.dp))
                    Box(Modifier.weight(1f)){HiroPersonRow(row.name,row.number,if(row.missed)"Пропущенный" else if(row.incoming)"Входящий" else "Исходящий",row.time){onOpen(row.key)}}
                }
            }
        }
        if(rows.isNotEmpty())item{Text("Личная история на этом устройстве",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

internal data class HiroDirectoryPerson(val number:String,val name:String,val annotation:String)

@Composable internal fun HiroDirectoryList(
    padding:PaddingValues,rows:List<HiroDirectoryPerson>,personal:Boolean,query:String,loaded:Boolean,busy:Boolean,
    privateReady:Boolean,syncEnabled:Boolean,isAdmin:Boolean,error:String,
    onPersonal:(Boolean)->Unit,onQuery:(String)->Unit,onSync:()->Unit,onOpen:(String)->Unit,onAdd:()->Unit,onImport:()->Unit,onExternal:()->Unit,onReload:()->Unit
) {
    val filtered=rows.filter{it.name.contains(query,true)||it.number.contains(query)}.sortedWith(compareBy<HiroDirectoryPerson>{it.name.lowercase()}.thenBy{it.number})
    LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item{HiroSegments(listOf("Клиенты команды","Мои контакты"),if(personal)1 else 0){onPersonal(it==1)}}
        item{Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(7.dp)){
            Icon(if(personal)Icons.Outlined.Lock else Icons.Outlined.People,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(personal)"Ваша адресная книга. Другие сотрудники её не видят." else "Общие клиенты организации. Видны всей команде.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        item{WorkspaceSearch(query,onQuery,"Имя или номер")}
        if(error.isNotBlank())item{WorkspaceInfo(error,true);TextButton(onClick=onReload,enabled=!busy){Text("Повторить загрузку")}}
        if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
        if(personal)item{WorkspaceRow("Синхронизация контактов",if(syncEnabled)"Включена · только в вашу книгу" else "Добавляйте вручную или включите синхронизацию",Icons.Outlined.Sync){onSync()}}
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){HiroSection(if(personal)"Личные контакты" else "Клиенты","${filtered.size}")}
            if(privateReady)TextButton(onClick=onAdd,enabled=!busy){Icon(Icons.Outlined.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(4.dp));Text("Добавить")}
        }}
        if(loaded && filtered.isEmpty())item{HiroEmpty(if(personal)Icons.Outlined.Contacts else Icons.Outlined.People,
            if(query.isNotBlank())"Контакт не найден" else if(personal)"Ваша книга пока пуста" else "Клиентов пока нет",
            if(query.isNotBlank())"Попробуйте другое имя или номер." else if(personal)"Добавьте контакт вручную или выберите его из телефона." else "Откройте личный контакт и сделайте его общим клиентом — только после подтверждения.")}
        itemsIndexed(filtered,key={_,row->row.number}){_,person ->
            Surface(color=MaterialTheme.colorScheme.surfaceContainerLowest,shape=RoundedCornerShape(16.dp)){
                HiroPersonRow(person.name.ifBlank{person.number},person.number,person.annotation){onOpen(person.number)}
            }
        }
        if(privateReady)item{OutlinedButton(onClick=onImport,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(13.dp)){
            Icon(Icons.Outlined.Contacts,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text("Выбрать из контактов телефона")
        }}
        if(!privateReady && loaded)item{WorkspaceInfo("Личная адресная книга недоступна на этой версии сервера. Общие клиенты остаются доступны.")}
        if(isAdmin)item{TextButton(onClick=onExternal,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Добавить клиента из другого источника")}}
    }
}
