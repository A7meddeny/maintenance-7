package com.masar.maintenance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.masar.maintenance.R
import com.masar.maintenance.data.*
import com.masar.maintenance.ui.components.*
import com.masar.maintenance.ui.theme.*
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

/* ====== بيانات الكفرات على الصورة (مواقع الإطارات الأربعة كنسب من أبعاد الصورة) ====== */
private data class TireSpot(val key: String, val label: String, val fx: Float, val fy: Float)
private val TIRE_SPOTS = listOf(
    TireSpot("fl", "أمامي يسار", 0.30f, 0.27f),
    TireSpot("fr", "أمامي يمين", 0.66f, 0.27f),
    TireSpot("rl", "خلفي يسار", 0.30f, 0.68f),
    TireSpot("rr", "خلفي يمين", 0.66f, 0.68f),
)

private fun derivePosition(sel: List<String>): String {
    if (sel.isEmpty()) return ""
    val anyFront = sel.any { it == "fl" || it == "fr" }
    val anyRear = sel.any { it == "rl" || it == "rr" }
    return when {
        anyFront && anyRear -> "both"
        anyFront -> "front"
        else -> "rear"
    }
}

/* بند صيانة دورية (حالة قابلة للتعديل) */
private class PerItem {
    var kind by mutableStateOf("oil_change")
    var name by mutableStateOf("")
    var supplierId by mutableStateOf(0)
    var amount by mutableStateOf("")
    var nextKm by mutableStateOf("")
    var donePhoto by mutableStateOf<UploadFile?>(null)
    var invoicePhoto by mutableStateOf<UploadFile?>(null)
    val tireSel = mutableStateListOf<String>()
}

private val KIND_OPTIONS = listOf(
    "oil_change" to "غيار زيت",
    "tire_change" to "تغيير كفرات",
    "battery" to "تغيير بطارية",
    "other_periodic" to "بند آخر"
)

@Composable
fun PeriodicScreen(nav: NavController, resumeId: Int = 0) {
    val scope = rememberCoroutineScope()

    var cars by remember { mutableStateOf<List<Car>>(emptyList()) }
    var suppliers by remember { mutableStateOf<List<Company>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }

    // حالة التدفّق
    var car by remember { mutableStateOf<Car?>(null) }
    var requestId by remember { mutableStateOf(0) }
    var opening by remember { mutableStateOf(false) }
    var showWarn by remember { mutableStateOf(false) }

    val itemsList = remember { mutableStateListOf(PerItem()) }
    var odometerOut by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var closePhoto by remember { mutableStateOf<UploadFile?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        loading = true
        val sr = Net.repo.companies()
        when (sr) { is Outcome.Ok -> suppliers = sr.data; is Outcome.Err -> {} }
        if (resumeId > 0) {
            // وضع الاستئناف: الطلب مفتوح مسبقاً — اجلب بيانات السيارة من تفاصيل الطلب
            when (val dr = Net.repo.requestDetail(resumeId)) {
                is Outcome.Ok -> {
                    val cb = dr.data.car
                    car = Car(
                        id = cb?.id ?: 0, name = cb?.name ?: "سيارة",
                        plateFull = cb?.plateFull, photo = cb?.photo, odometer = cb?.odometer
                    )
                    requestId = resumeId
                }
                is Outcome.Err -> loadError = dr.message
            }
        } else {
            when (val cr = Net.repo.cars(state = "available")) {
                is Outcome.Ok -> cars = cr.data
                is Outcome.Err -> loadError = cr.message
            }
        }
        loading = false
    }

    MasarScaffold(title = "صيانة دورية", onBack = { nav.popBackStack() }) { pad ->
        if (loading) { LoadingBox(); return@MasarScaffold }
        if (loadError != null) { ErrorBox(loadError!!) { reload++ }; return@MasarScaffold }

        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            error?.let {
                Surface(color = RedStatus.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(it, color = RedStatus, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
                Spacer(Modifier.height(12.dp))
            }

            // ====== (أ) تصحيح عداد سيارة (إجراء سريع مستقل) ======
            if (requestId == 0) {
                OdometerFixCard(cars, suppliers) { reload++ }
                Spacer(Modifier.height(16.dp))
            }

            // ====== (ب) اختيار السيارة لبدء صيانة دورية ======
            Surface(color = Panel, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("بدء صيانة دورية", color = Txt, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(10.dp))
                    PhotoCarSelector(cars, car, enabled = requestId == 0) { car = it }
                    if (car != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("العداد الحالي للسيارة: ${car!!.odometer?.let { fmtNum(it.toDouble()) } ?: "غير مسجّل"} كم", color = Muted, fontSize = 13.sp)
                    }
                    if (requestId == 0 && car != null) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("متابعة", loading = opening) { showWarn = true }
                    }
                    if (requestId != 0) {
                        Spacer(Modifier.height(8.dp))
                        Badge("السيارة محوّلة إلى حالة الصيانة الدورية", Yellow)
                    }
                }
            }

            // ====== (ج) البنود + الإغلاق ======
            if (requestId != 0) {
                Spacer(Modifier.height(16.dp))
                Text("بنود الصيانة الدورية", color = Txt, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                itemsList.forEachIndexed { i, it ->
                    PerItemCard(i, it, suppliers, car?.odometer, canDelete = itemsList.size > 1) { itemsList.removeAt(i) }
                    Spacer(Modifier.height(10.dp))
                }
                GhostButton("＋ بند آخر", onClick = { itemsList.add(PerItem()) }, modifier = Modifier.fillMaxWidth())

                Spacer(Modifier.height(16.dp))
                Surface(color = Panel, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("إغلاق وإعادة السيارة", color = Txt, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(10.dp))
                        // رسالة مراجعة + العداد الحالي
                        Surface(color = Yellow.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Yellow), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text("⚠ راجع قبل الإقفال: تأكد من قراءة عداد السيارة", color = Yellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("العداد المسجّل حالياً: ${car?.odometer?.let { fmtNum(it.toDouble()) } ?: "غير مسجّل"} كم", color = Txt, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        MasarField(odometerOut, { odometerOut = it }, "قراءة العداد عند الإنهاء (إجباري)", keyboard = KeyboardType.Number)
                        Spacer(Modifier.height(12.dp))
                        MasarField(note, { note = it }, "ملاحظة (اختياري)", singleLine = false)
                        Spacer(Modifier.height(12.dp))
                        PhotoPickerField("صورة الإغلاق (اختياري)", closePhoto, { closePhoto = it })
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton("إغلاق وإعادة السيارة للنظام", loading = submitting) {
                            error = null
                            if (odometerOut.isBlank()) { error = "اكتب قراءة العداد عند الإنهاء (إجباري)"; return@PrimaryButton }
                            // تحقق من بنود الزيت/الكفرات
                            val missingNext = itemsList.any { (it.kind == "oil_change" || it.kind == "tire_change") && it.nextKm.isBlank() }
                            if (missingNext) { error = "أدخل قراءة العداد القادم لبنود الزيت/الكفرات"; return@PrimaryButton }
                            val tireNoSel = itemsList.any { it.kind == "tire_change" && it.tireSel.isEmpty() }
                            if (tireNoSel) { error = "حدّد مواقع الكفرات على الصورة"; return@PrimaryButton }

                            submitting = true
                            scope.launch {
                                val arr = JsonArray()
                                val parts = mutableListOf<MultipartBody.Part>()
                                itemsList.forEachIndexed { i, it ->
                                    val o = JsonObject()
                                    o.addProperty("kind", it.kind)
                                    val posLabels = it.tireSel.mapNotNull { k -> TIRE_SPOTS.firstOrNull { s -> s.key == k }?.label }
                                    val baseName = it.name.ifBlank { KIND_OPTIONS.firstOrNull { k -> k.first == it.kind }?.second ?: "بند" }
                                    val finalName = if (it.kind == "tire_change" && posLabels.isNotEmpty())
                                        "$baseName (${posLabels.joinToString("، ")})" else baseName
                                    o.addProperty("name", finalName)
                                    if (it.supplierId > 0) o.addProperty("supplier_id", it.supplierId)
                                    if (it.amount.isNotBlank()) o.addProperty("invoice_amount", it.amount)
                                    car?.odometer?.let { od -> o.addProperty("odometer_current", od) }
                                    if (it.nextKm.isNotBlank()) o.addProperty("odometer_next", it.nextKm)
                                    if (it.kind == "tire_change" && it.tireSel.isNotEmpty()) {
                                        o.addProperty("tire_count", it.tireSel.size)
                                        o.addProperty("tire_position", derivePosition(it.tireSel))
                                        o.addProperty("tire_corners", it.tireSel.joinToString(","))
                                    }
                                    arr.add(o)
                                    it.donePhoto?.let { uf -> parts.add(Net.repo.quotePart("done_photo_$i", uf)) }
                                    it.invoicePhoto?.let { uf -> parts.add(Net.repo.quotePart("invoice_photo_$i", uf)) }
                                }
                                closePhoto?.let { uf -> parts.add(Net.repo.quotePart("completion_photo", uf)) }
                                when (val r = Net.repo.periodicComplete(requestId, arr.toString(), odometerOut, note, parts)) {
                                    is Outcome.Ok -> { submitting = false; nav.popBackStack() }
                                    is Outcome.Err -> { submitting = false; error = r.message }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    // تحذير التحويل لحالة الصيانة الدورية
    if (showWarn && car != null) {
        AlertDialog(
            onDismissRequest = { showWarn = false },
            containerColor = Ink2,
            title = { Text("تنبيه", color = Txt) },
            text = { Text("سيتم تحويل السيارة «${car!!.name}» إلى حالة الصيانة الدورية في النظام. متابعة؟", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    showWarn = false
                    opening = true
                    scope.launch {
                        when (val r = Net.repo.periodicSelf(car!!.id)) {
                            is Outcome.Ok -> { opening = false; requestId = r.data.get("id")?.asInt ?: 0 }
                            is Outcome.Err -> { opening = false; error = r.message }
                        }
                    }
                }) { Text("موافق", color = Red) }
            },
            dismissButton = { TextButton(onClick = { showWarn = false }) { Text("إلغاء", color = Muted) } }
        )
    }
}

/* ====== (أ) بطاقة تصحيح العداد ====== */
@Composable
private fun OdometerFixCard(cars: List<Car>, suppliers: List<Company>, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var car by remember { mutableStateOf<Car?>(null) }
    var newOdo by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf(false) }

    Surface(color = Panel, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تصحيح عداد سيارة", color = Txt, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { open = !open }) { Text(if (open) "إخفاء" else "فتح", color = Red) }
            }
            Text("تحديث قراءة العداد فقط — يظهر فوراً في سجل السيارة ويعيد حساب مواعيد الزيت/الكفرات.", color = Muted, fontSize = 12.sp)
            if (open) {
                Spacer(Modifier.height(12.dp))
                PhotoCarSelector(cars, car, enabled = true) { car = it; newOdo = ""; ok = false; msg = null }
                if (car != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("العداد الحالي المسجّل: ${car!!.odometer?.let { fmtNum(it.toDouble()) } ?: "غير مسجّل"} كم", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    MasarField(newOdo, { newOdo = it }, "القراءة الحالية للعداد", keyboard = KeyboardType.Number)
                    Spacer(Modifier.height(6.dp))
                    if (msg != null) {
                        Text(msg!!, color = if (ok) Green else RedStatus, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                    }
                    PrimaryButton("حفظ القراءة", loading = saving) {
                        msg = null
                        if (newOdo.isBlank()) { msg = "اكتب قراءة العداد"; ok = false; return@PrimaryButton }
                        saving = true
                        scope.launch {
                            when (val r = Net.repo.updateOdometer(car!!.id, newOdo)) {
                                is Outcome.Ok -> { saving = false; ok = true; msg = "تم تحديث العداد بنجاح"; onSaved() }
                                is Outcome.Err -> { saving = false; ok = false; msg = r.message }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ====== بطاقة بند دوري واحد ====== */
@Composable
private fun PerItemCard(index: Int, item: PerItem, suppliers: List<Company>, carOdo: Int?, canDelete: Boolean, onDelete: () -> Unit) {
    // تعبئة تلقائية للعداد القادم عند اختيار النوع
    LaunchedEffect(item.kind, carOdo) {
        if (item.nextKm.isBlank() && carOdo != null) {
            if (item.kind == "oil_change") item.nextKm = (carOdo + 10000).toString()
            if (item.kind == "tire_change") item.nextKm = (carOdo + 50000).toString()
        }
    }
    Surface(color = Panel, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("بند #${index + 1}", color = Txt, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (canDelete) TextButton(onClick = onDelete) { Text("حذف", color = RedStatus) }
            }
            LabeledDropdown("النوع", KIND_OPTIONS, item.kind) { item.kind = it }

            // غيار الزيت: العداد الحالي + العداد القادم (إجباري)
            if (item.kind == "oil_change") {
                Spacer(Modifier.height(8.dp))
                Text("العداد الحالي: ${carOdo?.let { fmtNum(it.toDouble()) } ?: "غير مسجّل"} كم", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                MasarField(item.nextKm, { item.nextKm = it }, "العداد القادم لغيار الزيت (إجباري)", keyboard = KeyboardType.Number)
                Text("المعتاد: كل 10,000 كم", color = Muted, fontSize = 11.sp)
            }

            // الكفرات: منتقي الصورة + العدد + الموقع (تلقائي) + العداد القادم
            if (item.kind == "tire_change") {
                Spacer(Modifier.height(10.dp))
                Text("حدّد مواقع الكفرات المُغيّرة (اضغط على الإطار):", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                TirePicker(item.tireSel)
                Spacer(Modifier.height(6.dp))
                val sel = item.tireSel.toList()
                Text(
                    "المختار: ${sel.size} إطار" + if (sel.isNotEmpty()) " — " +
                        sel.mapNotNull { k -> TIRE_SPOTS.firstOrNull { it.key == k }?.label }.joinToString("، ") else "",
                    color = if (sel.isEmpty()) RedStatus else Green, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                MasarField(item.nextKm, { item.nextKm = it }, "العداد القادم لتغيير الكفرات (إجباري)", keyboard = KeyboardType.Number)
                Text("المعتاد: كل 50,000 كم", color = Muted, fontSize = 11.sp)
            }

            Spacer(Modifier.height(10.dp))
            MasarField(item.name, { item.name = it }, "اسم/وصف البند (اختياري)")
            Spacer(Modifier.height(10.dp))
            LabeledDropdown(
                "المورّد / البنشر (لاحتساب المبلغ عليه)",
                listOf("0" to "— اختر المورّد —") + suppliers.map { it.id.toString() to it.name },
                item.supplierId.toString()
            ) { item.supplierId = it.toIntOrNull() ?: 0 }
            Spacer(Modifier.height(10.dp))
            MasarField(item.amount, { item.amount = it }, "مبلغ الفاتورة (ر.س)", keyboard = KeyboardType.Number)
            Spacer(Modifier.height(10.dp))
            PhotoPickerField("صورة التنفيذ", item.donePhoto, { item.donePhoto = it })
            Spacer(Modifier.height(10.dp))
            PhotoPickerField("صورة الفاتورة (اختياري)", item.invoicePhoto, { item.invoicePhoto = it }, allowPdf = true)
        }
    }
}

/* ====== منتقي الكفرات على صورة السيارة ====== */
@Composable
private fun TirePicker(selected: MutableList<String>) {
    val size = 240.dp
    Box(Modifier.size(size)) {
        Image(
            painter = painterResource(R.drawable.tire_layout),
            contentDescription = "مواقع الكفرات",
            modifier = Modifier.fillMaxSize()
        )
        TIRE_SPOTS.forEach { spot ->
            val checked = spot.key in selected
            val mk = 46.dp
            Box(
                Modifier
                    .offset(x = size * spot.fx - mk / 2, y = size * spot.fy - mk / 2)
                    .size(mk)
                    .clip(CircleShape)
                    .background(if (checked) Green.copy(alpha = 0.85f) else Red.copy(alpha = 0.18f))
                    .clickable {
                        if (checked) selected.remove(spot.key) else selected.add(spot.key)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(if (checked) "✓" else "+", color = if (checked) Ink else Txt, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
    }
}

/* ====== منتقي سيارة يعرض الصور + بحث ====== */
@Composable
private fun PhotoCarSelector(cars: List<Car>, selected: Car?, enabled: Boolean, onSelect: (Car) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (selected?.photo != null) {
            RemoteImage(selected.photo, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(10.dp))
        }
        GhostButton(
            text = selected?.let { "${it.name} — ${it.plateFull ?: ""}" } ?: "— اختر السيارة —",
            onClick = { if (enabled) open = true },
            modifier = Modifier.weight(1f)
        )
    }
    if (open) {
        var q by remember { mutableStateOf("") }
        val filtered = remember(q, cars) {
            if (q.isBlank()) cars
            else cars.filter {
                it.name.contains(q, true) || (it.plateFull ?: "").contains(q, true) ||
                (it.carCode ?: "").contains(q, true) || it.id.toString().contains(q)
            }
        }
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = Ink2,
            title = { Text("اختر السيارة", color = Txt) },
            text = {
                Column {
                    MasarField(q, { q = it }, "ابحث بالاسم أو اللوحة…")
                    Spacer(Modifier.height(10.dp))
                    if (filtered.isEmpty()) EmptyBox("لا توجد سيارات متاحة", "∅")
                    else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtered, key = { it.id }) { c ->
                            Surface(
                                color = Panel, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Line),
                                modifier = Modifier.fillMaxWidth().clickable { onSelect(c); open = false }
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RemoteImage(c.photo, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(c.name, color = Txt, fontWeight = FontWeight.Bold)
                                        Text(c.plateFull ?: "—", color = Muted, fontSize = 12.sp)
                                        Text("العداد: ${c.odometer?.let { fmtNum(it.toDouble()) } ?: "—"} كم", color = Muted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("إغلاق", color = Red) } }
        )
    }
}
