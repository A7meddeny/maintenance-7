package com.masar.maintenance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.masar.maintenance.data.Net
import com.masar.maintenance.data.Noti
import com.masar.maintenance.data.Outcome
import com.masar.maintenance.ui.Labels
import com.masar.maintenance.ui.components.*
import com.masar.maintenance.ui.theme.*
import kotlinx.coroutines.launch

private data class HomeItem(val label: String, val route: String, val icon: String, val desc: String)

@Composable
fun HomeScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    val role = Net.session.userRole
    val name = Net.session.userName

    val items = remember(role) { buildItems(role) }
    var notis by remember { mutableStateOf<List<Noti>>(emptyList()) }
    var showNotis by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        when (val r = Net.repo.notifications()) { is Outcome.Ok -> notis = r.data; is Outcome.Err -> {} }
    }

    MasarScaffold(
        title = "نظام مسار للصيانة",
        actions = {
            Box {
                val bellAnim = rememberInfiniteTransition(label = "bell")
                val angle by bellAnim.animateFloat(
                    initialValue = 0f, targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 1500
                            0f at 0; -14f at 90; 14f at 180; -10f at 270; 10f at 360; 0f at 450; 0f at 1500
                        },
                        repeatMode = RepeatMode.Restart
                    ), label = "angle"
                )
                val shake = if (notis.isNotEmpty()) angle else 0f
                IconButton(onClick = { showNotis = true }) {
                    Icon(
                        Icons.Filled.Notifications, contentDescription = "الإشعارات", tint = Txt,
                        modifier = Modifier.graphicsLayer { rotationZ = shake }
                    )
                }
                if (notis.isNotEmpty()) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp)
                            .size(18.dp).clip(CircleShape).background(Red),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (notis.size > 9) "9+" else notis.size.toString(), color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = {
                scope.launch {
                    Net.repo.logout()
                    nav.navigate("login") { popUpTo("home") { inclusive = true } }
                }
            }) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "خروج", tint = Red) }
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MasarCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val photo = Net.session.userPhoto
                        if (!photo.isNullOrBlank()) {
                            RemoteImage(
                                photo,
                                modifier = Modifier.size(44.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                            )
                        } else {
                            Avatar(name.take(1).ifBlank { "م" })
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name.ifBlank { "مستخدم" }, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Txt)
                            Text(Labels.role(role), color = Muted)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            items(items) { it -> HomeRow(it) { nav.navigate(it.route) } }
        }
    }

    if (showNotis) {
        AlertDialog(
            onDismissRequest = { showNotis = false },
            containerColor = Ink2,
            title = { Text("الإشعارات (${notis.size})", color = Txt) },
            text = {
                if (notis.isEmpty()) Text("لا توجد تنبيهات حالياً ✓", color = Muted)
                else LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notis) { n ->
                        val c = when (n.color) { "green" -> Green; "yellow" -> Yellow; else -> RedStatus }
                        Surface(
                            color = Panel, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Line),
                            modifier = Modifier.fillMaxWidth().clickable {
                                showNotis = false
                                if (n.requestId > 0) nav.navigate("request/${n.requestId}")
                            }
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(n.icon, fontSize = 20.sp)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(n.title, color = c, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    if (n.sub.isNotBlank()) Text(n.sub, color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNotis = false }) { Text("إغلاق", color = Red) } }
        )
    }
}

@Composable
private fun HomeRow(item: HomeItem, onClick: () -> Unit) {
    Surface(
        color = Panel, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.icon, fontSize = 24.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Txt)
                Text(item.desc, color = Muted, fontSize = 13.sp)
            }
            Text("‹", color = Muted, fontSize = 22.sp)
        }
    }
}

private fun buildItems(role: String): List<HomeItem> {
    val list = mutableListOf<HomeItem>()
    list += HomeItem("متابعة الطلبات", "requests?scope=", "⇄", "كل طلبات الصيانة وحالتها")
    list += HomeItem("سجل السيارات", "cars", "⛍", "بحث وعرض السيارات")
    list += HomeItem("مسح رمز سيارة (QR)", "scan", "🔳", "افتح ملف السيارة بمسح الرمز الملصق عليها")
    when (role) {
        "office" -> list += HomeItem("إنشاء طلب صيانة", "newRequest", "＋", "إدخال عطل سيارة وإسناده للصيانة")
        "maintenance" -> list += HomeItem("صيانة دورية", "periodic", "🛢", "غيار زيت/كفرات/بطارية — تُغلق من الصيانة")
        "purchasing" -> list += HomeItem("متابعة المشتريات", "requests?scope=purchasing_followup", "₪", "الطلبات المنتظرة للشراء")
        "admin" -> {
            list += HomeItem("إنشاء طلب صيانة", "newRequest", "＋", "إدخال عطل سيارة وإسناده للصيانة")
            list += HomeItem("لوحة المعلومات", "dashboard", "▣", "المؤشرات والتنبيهات")
            list += HomeItem("متابعة المشتريات", "requests?scope=purchasing_followup", "₪", "الطلبات المنتظرة للشراء")
            list += HomeItem("متابعة الموظفين", "staff", "☖", "طلبات كل موظف وتأخيره")
            list += HomeItem("الموظفون", "employees", "⛁", "إدارة حسابات الموظفين")
            list += HomeItem("الشركات المورّدة", "companies", "⌂", "شركات قطع الغيار")
        }
    }
    return list.distinctBy { it.route + it.label }
}
