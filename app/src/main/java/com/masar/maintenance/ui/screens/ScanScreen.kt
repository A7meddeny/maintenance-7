package com.masar.maintenance.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.masar.maintenance.data.Car
import com.masar.maintenance.data.Net
import com.masar.maintenance.data.Outcome
import com.masar.maintenance.ui.components.*
import com.masar.maintenance.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ScanScreen(nav: NavController) {
    val scope = rememberCoroutineScope()
    var car by remember { mutableStateOf<Car?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var launched by remember { mutableStateOf(false) }
    var showOdo by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ScanContract()) { res ->
        val token = res.contents
        if (token.isNullOrBlank()) {
            nav.popBackStack()
        } else {
            loading = true; error = null
            scope.launch {
                when (val r = Net.repo.carByQr(token)) {
                    is Outcome.Ok -> { loading = false; car = r.data }
                    is Outcome.Err -> { loading = false; error = r.message }
                }
            }
        }
    }

    fun startScan() {
        launcher.launch(ScanOptions().apply {
            setPrompt("وجّه الكاميرا نحو رمز QR للسيارة")
            setBeepEnabled(true)
            setOrientationLocked(true)
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setCaptureActivity(com.masar.maintenance.SquareCaptureActivity::class.java)
        })
    }

    LaunchedEffect(Unit) { if (!launched) { launched = true; startScan() } }

    MasarScaffold(title = "مسح رمز السيارة", onBack = { nav.popBackStack() }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                loading -> LoadingBox()
                error != null -> Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(error!!, color = RedStatus, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("مسح مرة أخرى") { error = null; startScan() }
                }
                car != null -> CarResult(car!!, nav, onUpdateOdo = { showOdo = true }, onRescan = { car = null; startScan() })
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("جارٍ فتح الكاميرا…", color = Muted)
                }
            }
        }
    }

    if (showOdo && car != null) {
        OdoQuickDialog(car!!, onDismiss = { showOdo = false }, onSaved = { newOdo ->
            showOdo = false
            car = car!!.copy(odometer = newOdo)
        })
    }
}

@Composable
private fun CarResult(c: Car, nav: NavController, onUpdateOdo: () -> Unit, onRescan: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Surface(color = Panel, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteImage(c.photo, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = Txt, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(c.plateFull ?: "—", color = Muted, fontSize = 13.sp)
                    }
                    Badge(
                        if (c.state == "available") "متاحة" else "قيد الصيانة",
                        if (c.state == "available") Green else Yellow
                    )
                }
                Spacer(Modifier.height(12.dp))
                InfoLine("الكود", c.carCode ?: "—")
                InfoLine("الموديل", c.model ?: "—")
                InfoLine("العداد", c.odometer?.let { fmtNum(it.toDouble()) + " كم" } ?: "—")
                InfoLine("غيار الزيت القادم", c.nextOilChangeKm?.let { fmtNum(it.toDouble()) + " كم" } ?: "—")
                InfoLine("تغيير الكفرات القادم", c.nextTireChangeKm?.let { fmtNum(it.toDouble()) + " كم" } ?: "—")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (c.state == "under_maintenance" && !c.openSerial.isNullOrBlank()) {
            Surface(color = Yellow.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Yellow), modifier = Modifier.fillMaxWidth()) {
                Text("هذه السيارة حالياً في الصيانة (${c.openSerial}).", color = Yellow, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        PrimaryButton("عرض ملف السيارة الكامل") {
            nav.navigate("carHistory/${c.id}")
        }
        if (c.canUpdateOdo == true) {
            Spacer(Modifier.height(10.dp))
            GhostButton("تحديث / تصحيح العداد", onClick = onUpdateOdo, modifier = Modifier.fillMaxWidth())
        }
        if (c.canPeriodic == true) {
            Spacer(Modifier.height(10.dp))
            GhostButton("بدء صيانة دورية", onClick = { nav.navigate("periodic") }, modifier = Modifier.fillMaxWidth())
        }
        if (c.canNewRequest == true) {
            Spacer(Modifier.height(10.dp))
            GhostButton("إنشاء طلب صيانة", onClick = { nav.navigate("newRequest") }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("مسح سيارة أخرى", onClick = onRescan, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.width(140.dp))
        Text(value, color = Txt, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OdoQuickDialog(c: Car, onDismiss: () -> Unit, onSaved: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var odo by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink2,
        title = { Text("تصحيح عداد: ${c.name}", color = Txt, fontSize = 16.sp) },
        text = {
            Column {
                Text("العداد المسجّل: ${c.odometer?.let { fmtNum(it.toDouble()) } ?: "—"} كم", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                MasarField(odo, { odo = it }, "القراءة الحالية للعداد", keyboard = KeyboardType.Number)
                err?.let { Spacer(Modifier.height(6.dp)); Text(it, color = RedStatus, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                if (odo.isBlank()) { err = "اكتب قراءة العداد"; return@TextButton }
                saving = true; err = null
                scope.launch {
                    when (val r = Net.repo.updateOdometer(c.id, odo)) {
                        is Outcome.Ok -> { saving = false; onSaved(odo.toIntOrNull() ?: (c.odometer ?: 0)) }
                        is Outcome.Err -> { saving = false; err = r.message }
                    }
                }
            }) { Text(if (saving) "جارٍ الحفظ…" else "حفظ", color = Red) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", color = Muted) } }
    )
}
