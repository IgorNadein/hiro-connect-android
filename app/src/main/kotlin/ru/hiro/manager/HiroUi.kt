package ru.hiro.manager

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Product components, shared by real screens and native render tests. No API,
 * contact lookup, microphone access or SIP side effects live in this layer. */
internal enum class HiroTone { Neutral, Brand, Good, Warning, Error }

/** Compact list metadata; the call/message detail keeps its complete timestamp. */
internal fun hiroListTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val zone = java.time.ZoneId.systemDefault()
    val date = java.time.Instant.ofEpochMilli(timestamp).atZone(zone)
    val pattern = if (date.toLocalDate() == java.time.LocalDate.now(zone)) "HH:mm" else "dd.MM.yy"
    return date.format(java.time.format.DateTimeFormatter.ofPattern(pattern))
}

@Composable internal fun hiroTone(tone: HiroTone): Pair<Color,Color> = when(tone) {
    HiroTone.Brand -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
    HiroTone.Good -> (if(BaresipService.darkTheme.value)Color(0xFF21392F) else Color(0xFFEAF7EF)) to
        (if(BaresipService.darkTheme.value)Color(0xFF9BD6B9) else Color(0xFF176348))
    HiroTone.Warning -> (if(BaresipService.darkTheme.value)Color(0xFF3C3321) else Color(0xFFFFF6E2)) to
        (if(BaresipService.darkTheme.value)Color(0xFFF2D089) else Color(0xFF79540C))
    HiroTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun hiroInitials(name: String): String = name.trim().split(Regex("\\s+")).filter{it.isNotEmpty()}
    .let { parts -> if(parts.isEmpty() || parts.first().firstOrNull()?.isDigit()==true || name.startsWith("+")) "" else parts.take(2).joinToString(""){it.take(1).uppercase()} }

@Composable internal fun HiroAvatar(name:String,size:Dp=42.dp,icon:ImageVector=Icons.Outlined.Person) {
    Surface(shape=RoundedCornerShape(size*.32f),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(size)) {
        Box(contentAlignment=Alignment.Center) {
            val initials=hiroInitials(name)
            if(initials.isEmpty())Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(size*.47f))
            else Text(initials,color=MaterialTheme.colorScheme.primary,fontSize=(size.value*.33f).sp,fontWeight=FontWeight.Medium)
        }
    }
}

@Composable internal fun HiroBadge(text:String,tone:HiroTone=HiroTone.Brand,icon:ImageVector?=null) {
    val (background,foreground)=hiroTone(tone)
    Surface(color=background,shape=RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal=9.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            if(icon!=null)Icon(icon,null,modifier=Modifier.size(13.dp),tint=foreground)
            Text(text,color=foreground,style=MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable internal fun HiroSection(title:String,detail:String="") {
    Row(Modifier.fillMaxWidth().padding(top=2.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=.8.sp)
        if(detail.isNotEmpty())Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun HiroSegments(labels:List<String>,selected:Int,onSelect:(Int)->Unit) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLowest,shape=RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { i,label ->
                val active=i==selected
                Box(Modifier.weight(1f).background(if(active)MaterialTheme.colorScheme.primaryContainer else Color.Transparent,RoundedCornerShape(10.dp))
                    .selectable(active,role=Role.Tab,onClick={onSelect(i)}).heightIn(min=44.dp).padding(horizontal=8.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                    Text(label,style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable internal fun HiroEmpty(icon:ImageVector,title:String,description:String,actionLabel:String="",onAction:()->Unit={}) {
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.primaryContainer) { Icon(icon,null,Modifier.padding(18.dp).size(28.dp),tint=MaterialTheme.colorScheme.primary) }
        Text(title,style=MaterialTheme.typography.titleLarge,textAlign=TextAlign.Center)
        Text(description,style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(actionLabel.isNotBlank())TextButton(onClick=onAction){Text(actionLabel)}
    }
}

@Composable internal fun HiroPersonRow(name:String,detail:String,annotation:String="",trailing:String="",icon:ImageVector=Icons.Outlined.Person,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        HiroAvatar(name,icon=icon)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(annotation.isNotEmpty())Text(annotation,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        if(trailing.isNotBlank())Text(trailing,Modifier.widthIn(max=74.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,textAlign=TextAlign.End,overflow=TextOverflow.Ellipsis)
    }
}

@Composable internal fun HiroContactHero(name:String,number:String,label:String,shared:Boolean=false) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLowest,shape=RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            HiroAvatar(name,76.dp)
            Text(name,style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center)
            if(number!=name)Text(number,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HiroBadge(label,icon=if(shared)Icons.Outlined.People else Icons.Outlined.Lock)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun HiroKeypad(number:String,onChange:(String)->Unit,enabled:Boolean=true) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(number,onChange,modifier=Modifier.fillMaxWidth(),placeholder={Text("Номер телефона")},singleLine=true,
            shape=RoundedCornerShape(16.dp),textStyle=MaterialTheme.typography.headlineSmall.copy(textAlign=TextAlign.Center),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),enabled=enabled,
            trailingIcon={if(number.isNotEmpty())IconButton(onClick={onChange(number.dropLast(1))},enabled=enabled){Icon(Icons.AutoMirrored.Outlined.Backspace,"Удалить последнюю цифру")}},
            colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest,focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLowest))
        val letters=listOf("","ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ","","+","")
        listOf("123","456","789","*0#").forEachIndexed { row,digits ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                digits.forEachIndexed { column,digit ->
                    Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainerLowest,modifier=Modifier.weight(1f)
                        .combinedClickable(enabled=enabled,role=Role.Button,onClick={onChange(number+digit)},onLongClick=if(digit=='0')({onChange(number+"+")})else null)
                        .semantics{contentDescription="Цифра $digit"}) {
                        Column(Modifier.heightIn(min=62.dp).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Text("$digit",fontSize=27.sp,lineHeight=31.sp,fontWeight=FontWeight.Normal)
                            Text(letters[row*3+column],style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,letterSpacing=1.sp)
                        }
                    }
                }
            }
        }
        if(number.isEmpty())TextButton(onClick={onChange("+")},enabled=enabled,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Добавить код страны +")}
    }
}

@Composable internal fun HiroCallIdentity(name:String,number:String,status:String,connected:Boolean=false) {
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        HiroBadge(status,if(connected)HiroTone.Good else HiroTone.Brand,if(connected)Icons.Outlined.GraphicEq else Icons.Outlined.Phone)
        HiroAvatar(name,82.dp)
        Text(name,style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center)
        if(number!=name)Text(number,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
    }
}

@Composable internal fun HiroCallControl(label:String,icon:ImageVector,selected:Boolean,enabled:Boolean=true,modifier:Modifier=Modifier,onClick:()->Unit) {
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(17.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier=modifier.semantics(mergeDescendants=true){}.heightIn(min=84.dp)) {
        Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Icon(icon,null,tint=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,modifier=Modifier.size(23.dp))
            Text(label,style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center,color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable internal fun HiroConversationPad(muted:Boolean,speaker:Boolean,held:Boolean,enabled:Boolean,dtmfEnabled:Boolean,onMute:()->Unit,onSpeaker:()->Unit,onHold:()->Unit,onKeypad:()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            HiroCallControl(if(muted)"Включить микрофон" else "Выключить микрофон",if(muted)Icons.Outlined.MicOff else Icons.Outlined.Mic,muted,enabled,Modifier.weight(1f),onMute)
            HiroCallControl(if(speaker)"Выключить динамик" else "Громкая связь",Icons.Outlined.VolumeUp,speaker,enabled,Modifier.weight(1f),onSpeaker)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            HiroCallControl(if(held)"Продолжить" else "Удержать",if(held)Icons.Outlined.PlayArrow else Icons.Outlined.Pause,held,enabled,Modifier.weight(1f),onHold)
            HiroCallControl("Клавиатура",Icons.Outlined.Dialpad,false,enabled&&dtmfEnabled,Modifier.weight(1f),onKeypad)
        }
    }
}
