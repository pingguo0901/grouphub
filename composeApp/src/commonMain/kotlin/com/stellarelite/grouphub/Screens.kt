package com.stellarelite.grouphub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// ============ 2. 炙巷食铺 财务中心 ============
@Composable
fun ZhixiangFinanceScreen(token: String) {
    var inventory by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var payroll by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var sst by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(token) {
        inventory = SupabaseApi.fetchTable(token, "inventory_summary", "?select=*&business_industry=eq.F&B&limit=20")
        payroll = SupabaseApi.fetchTable(token, "staff_payroll", "?select=*&business_industry=eq.F&B&limit=20")
        sst = SupabaseApi.fetchTable(token, "sst_return", "?select=*&limit=20")
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("炙巷食铺 财务中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (loading) { Text("加载中...", color = Color.Gray); return@Column }

        SectionTitle("进销存（${inventory.size} 条）")
        inventory.take(10).forEach { r ->
            InfoRow(SupabaseApi.str(r, "item_name"), "变动 ${SupabaseApi.str(r, "movement_type")} · 数量 ${SupabaseApi.dbl(r, "quantity")}")
        }

        SectionTitle("薪资台账（${payroll.size} 条）")
        payroll.take(10).forEach { r ->
            InfoRow(SupabaseApi.str(r, "staff_name"), "实发 RM %.2f".format(SupabaseApi.dbl(r, "net_salary")))
        }

        SectionTitle("报税底稿（${sst.size} 条）")
        sst.take(10).forEach { r ->
            InfoRow("SST ${SupabaseApi.str(r, "tax_period")}", "应缴 RM %.2f".format(SupabaseApi.dbl(r, "sst_payable")))
        }
    }
}

// ============ 3. 星域臻旅 财务中心（双账本专区） ============
@Composable
fun XyzlFinanceScreen(token: String) {
    var official by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var private by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(token) {
        official = SupabaseApi.fetchTable(token, "cashflow_business", "?select=*&account_type=eq.official&business_industry=eq.ElectronicTrade&limit=20")
        private = SupabaseApi.fetchTable(token, "cashflow_business", "?select=*&account_type=eq.private&business_industry=eq.ElectronicTrade&limit=20")
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("星域臻旅 财务中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (loading) { Text("加载中...", color = Color.Gray); return@Column }

        Text("报税商贸账专区（电子零件）", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
        official.take(10).forEach { r ->
            InfoRow(SupabaseApi.str(r, "flow_type"), "RM %.2f · %s".format(SupabaseApi.dbl(r, "amount"), SupabaseApi.str(r, "category")))
        }
        Spacer(Modifier.height(12.dp))
        Text("真实包车经营账专区（内部）", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF9A825))
        private.take(10).forEach { r ->
            InfoRow(SupabaseApi.str(r, "flow_type"), "RM %.2f · %s".format(SupabaseApi.dbl(r, "amount"), SupabaseApi.str(r, "category")))
        }
    }
}

// ============ 4. 薪资人力合规中心 ============
@Composable
fun PayrollScreen(token: String) {
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var entities by remember { mutableStateOf<List<BusinessEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(token, refreshKey) {
        rows = SupabaseApi.fetchTable(token, "staff_payroll", "?select=*&limit=50")
        entities = SupabaseApi.fetchEntities(token)
        loading = false
    }

    val totalEpf = rows.sumOf { SupabaseApi.dbl(it, "employer_epf") }
    val totalSocso = rows.sumOf { SupabaseApi.dbl(it, "employer_socso") }
    val totalEis = rows.sumOf { SupabaseApi.dbl(it, "employer_eis") }
    val totalPcb = rows.sumOf { SupabaseApi.dbl(it, "pcb_mtd") }
    val totalCost = rows.sumOf { SupabaseApi.dbl(it, "total_cost") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("薪资人力合规中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = { showDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "录入工资")
                Spacer(Modifier.width(4.dp))
                Text("录入工资")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("本月人力缴款汇总", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("雇主 EPF RM %.2f".format(totalEpf), fontSize = 14.sp)
                Text("雇主 SOCSO RM %.2f".format(totalSocso), fontSize = 14.sp)
                Text("雇主 EIS RM %.2f".format(totalEis), fontSize = 14.sp)
                Text("PCB 预扣 RM %.2f".format(totalPcb), fontSize = 14.sp)
                Text("人力总成本 RM %.2f".format(totalCost), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("员工薪资明细（${rows.size} 条）", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (loading) Text("加载中...", color = Color.Gray)
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows) { r ->
                InfoRow(SupabaseApi.str(r, "staff_name"), "实发 RM %.2f".format(SupabaseApi.dbl(r, "net_salary")))
            }
        }
    }

    if (showDialog) {
        PayrollDialog(token, entities, onDone = { showDialog = false; refreshKey = refreshKey + 1 }, onDismiss = { showDialog = false })
    }
}

// ============ 5. 税务申报中心 ============
@Composable
fun TaxScreen(token: String) {
    var sst by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var formb by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var forme by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var genResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        sst = SupabaseApi.fetchTable(token, "sst_return", "?select=*&limit=20")
        formb = SupabaseApi.fetchTable(token, "formb_tax", "?select=*&limit=20")
        forme = SupabaseApi.fetchTable(token, "forme_submit", "?select=*&limit=20")
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("税务申报中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            scope.launch {
                genResult = SupabaseApi.callFunction("gen-returns", token, """{"tax_period":"2026-07","tax_year":"2026"}""")
                sst = SupabaseApi.fetchTable(token, "sst_return", "?select=*&limit=20")
                formb = SupabaseApi.fetchTable(token, "formb_tax", "?select=*&limit=20")
                forme = SupabaseApi.fetchTable(token, "forme_submit", "?select=*&limit=20")
            }
        }) { Text("生成报税底稿（SST02/FormB/FormE/CP500）") }
        genResult?.let { Spacer(Modifier.height(8.dp)); Text("生成结果：$it", fontSize = 12.sp, color = Color.Gray) }
        Spacer(Modifier.height(12.dp))

        SectionTitle("SST02 底稿（${sst.size} 条）")
        sst.forEach { r ->
            InfoRow("周期 ${SupabaseApi.str(r, "tax_period")}", "应缴 RM %.2f".format(SupabaseApi.dbl(r, "sst_payable")))
        }
        SectionTitle("FormB 合并报税（${formb.size} 条）")
        formb.forEach { r ->
            InfoRow("年度 ${SupabaseApi.str(r, "tax_year")}", "合并应税利润 RM %.2f".format(SupabaseApi.dbl(r, "total_taxable_profit")))
        }
        SectionTitle("FormE 薪资年度申报（${forme.size} 条）")
        forme.forEach { r ->
            InfoRow(
                "年度 ${SupabaseApi.str(r, "tax_year")}",
                "EPF %.2f · SOCSO %.2f · EIS %.2f · PCB %.2f".format(
                    SupabaseApi.dbl(r, "total_epf"), SupabaseApi.dbl(r, "total_socso"),
                    SupabaseApi.dbl(r, "total_eis"), SupabaseApi.dbl(r, "total_pcb")
                )
            )
        }
    }
}

// ============ 6. 合规预警中心 ============
@Composable
fun ComplianceScreen(token: String) {
    var alerts by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var reminders by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        alerts = SupabaseApi.fetchTable(token, "tax_alert", "?select=*&limit=50")
        reminders = SupabaseApi.fetchTable(token, "compliance_reminder", "?select=*&limit=50")
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("合规预警中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            scope.launch {
                SupabaseApi.callFunction("scan-alerts", token, "{}")
                alerts = SupabaseApi.fetchTable(token, "tax_alert", "?select=*&limit=50")
            }
        }) { Text("立即扫描风险") }
        Spacer(Modifier.height(12.dp))

        if (loading) { Text("加载中...", color = Color.Gray); return@Column }
        SectionTitle("风险告警（${alerts.size} 条）")
        alerts.forEach { r ->
            val lvl = SupabaseApi.str(r, "level")
            InfoRow("[${lvl}] ${SupabaseApi.str(r, "alert_type")}", SupabaseApi.str(r, "description"))
        }
        SectionTitle("法定合规日历（${reminders.size} 条）")
        reminders.forEach { r ->
            InfoRow(SupabaseApi.str(r, "reminder_type"), "到期 ${SupabaseApi.str(r, "due_date")} · ${SupabaseApi.str(r, "status")}")
        }
    }
}

// ============ 7. 官方档案库 ============
@Composable
fun ArchiveScreen(token: String) {
    var bizFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var phoneFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var aiFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(token) {
        bizFiles = SupabaseApi.listFiles(token, "biz_doc_archive")
        phoneFiles = SupabaseApi.listFiles(token, "phone_sync_attachment")
        aiFiles = SupabaseApi.listFiles(token, "ai_fb_draft_export")
        loading = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("官方档案库（7 年存档）", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (loading) { Text("加载中...", color = Color.Gray); return@Column }

        SectionTitle("biz_doc_archive · 报税文档（${bizFiles.size} 个）")
        if (bizFiles.isEmpty()) Text("暂无文件", color = Color.Gray)
        bizFiles.forEach { f -> InfoRow(f, "报税官方文档") }

        SectionTitle("phone_sync_attachment · 私人凭证（${phoneFiles.size} 个）")
        if (phoneFiles.isEmpty()) Text("暂无文件", color = Color.Gray)
        phoneFiles.forEach { f -> InfoRow(f, "私人凭证") }

        SectionTitle("ai_fb_draft_export · AI/FB（${aiFiles.size} 个）")
        if (aiFiles.isEmpty()) Text("暂无文件", color = Color.Gray)
        aiFiles.forEach { f -> InfoRow(f, "AI/FB 文件") }
    }
}

// ============ 8. 数据同步中心 ============
@Composable
fun SyncScreen(token: String) {
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(token) {
        rows = SupabaseApi.fetchTable(token, "biz_app_sync", "?select=*&limit=50")
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("数据同步中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (loading) { Text("加载中...", color = Color.Gray); return@Column }
        Text("同步记录（${rows.size} 条）", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows) { r ->
                InfoRow(
                    SupabaseApi.str(r, "sync_type"),
                    "${SupabaseApi.str(r, "status")} · ${SupabaseApi.dbl(r, "row_count").toInt()} 条"
                )
            }
        }
    }
}

// ============ 通用组件 ============
@Composable
fun SectionTitle(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}

@Composable
fun InfoRow(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

// ============ 录入对话框 ============
@Composable
fun OwnerDrawingDialog(token: String, entities: List<BusinessEntity>, onDone: () -> Unit, onDismiss: () -> Unit) {
    var entityId by remember { mutableStateOf(entities.firstOrNull()?.id ?: "") }
    var amount by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("") }
    var bankRef by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("录入老板提款") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EntitySelector(entities, entityId) { entityId = it }
                OutlinedTextField(amount, { amount = it }, label = { Text("提款金额 MYR") }, singleLine = true)
                OutlinedTextField(month, { month = it }, label = { Text("提款月份（如 2026-09）") }, singleLine = true)
                OutlinedTextField(bankRef, { bankRef = it }, label = { Text("银行转账参考号") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("备注") }, singleLine = true)
                msg?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = {
                scope.launch {
                    saving = true
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    val body = """{"business_entity_id":"$entityId","account_type":"private","amount_myr":$amt,"draw_month":"$month","bank_ref":"$bankRef","note":"$note"}"""
                    SupabaseApi.insertRow(token, "owner_drawing", body).onSuccess { onDone() }.onFailure { msg = it.message }
                    saving = false
                }
            }) { Text(if (saving) "保存中..." else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun CashflowDialog(token: String, entities: List<BusinessEntity>, onDone: () -> Unit, onDismiss: () -> Unit) {
    var entityId by remember { mutableStateOf(entities.firstOrNull()?.id ?: "") }
    var flowType by remember { mutableStateOf("收入") }
    var category by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("录入手动收支") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EntitySelector(entities, entityId) { entityId = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = flowType == "收入", onClick = { flowType = "收入" }, label = { Text("收入") })
                    FilterChip(selected = flowType == "成本支出", onClick = { flowType = "成本支出" }, label = { Text("成本支出") })
                }
                OutlinedTextField(category, { category = it }, label = { Text("分类（如 进货/租金/水电）") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("金额 MYR") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("备注") }, singleLine = true)
                msg?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = {
                scope.launch {
                    saving = true
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    val body = """{"business_entity_id":"$entityId","account_type":"official","flow_type":"$flowType","category":"$category","amount":$amt,"note":"$note"}"""
                    SupabaseApi.insertRow(token, "cashflow_business", body).onSuccess { onDone() }.onFailure { msg = it.message }
                    saving = false
                }
            }) { Text(if (saving) "保存中..." else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun EntitySelector(entities: List<BusinessEntity>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(entities.firstOrNull { it.id == selected }?.name ?: "选择主体")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entities.forEach { e ->
                DropdownMenuItem(text = { Text(e.name) }, onClick = { onSelect(e.id); expanded = false })
            }
        }
    }
}

@Composable
fun PayrollDialog(token: String, entities: List<BusinessEntity>, onDone: () -> Unit, onDismiss: () -> Unit) {
    var entityId by remember { mutableStateOf(entities.firstOrNull()?.id ?: "") }
    var staffName by remember { mutableStateOf("") }
    var basic by remember { mutableStateOf("") }
    var allowance by remember { mutableStateOf("") }
    var overtime by remember { mutableStateOf("") }
    var bonus by remember { mutableStateOf("") }
    var isForeigner by remember { mutableStateOf(false) }
    var ageOver60 by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("录入工资（自动计算）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EntitySelector(entities, entityId) { entityId = it }
                OutlinedTextField(staffName, { staffName = it }, label = { Text("员工名字") }, singleLine = true)
                OutlinedTextField(basic, { basic = it }, label = { Text("底薪") }, singleLine = true)
                OutlinedTextField(allowance, { allowance = it }, label = { Text("津贴") }, singleLine = true)
                OutlinedTextField(overtime, { overtime = it }, label = { Text("加班") }, singleLine = true)
                OutlinedTextField(bonus, { bonus = it }, label = { Text("奖金") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("外籍", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = isForeigner, onCheckedChange = { isForeigner = it })
                    Spacer(Modifier.width(16.dp))
                    Text("60岁以上", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = ageOver60, onCheckedChange = { ageOver60 = it })
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val b = basic.toDoubleOrNull() ?: 0
                        val al = allowance.toDoubleOrNull() ?: 0
                        val ot = overtime.toDoubleOrNull() ?: 0
                        val bo = bonus.toDoubleOrNull() ?: 0
                        val body = """{"basic_salary":$b,"allowance":$al,"overtime":$ot,"bonus":$bo,"is_foreigner":$isForeigner,"age_over_60":$ageOver60}"""
                        result = SupabaseApi.callFunction("gen-payroll", token, body)
                    }
                }) { Text("计算 EPF/SOCSO/EIS/PCB") }
                result?.let { r ->
                    Text("计算结果：", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(r, fontSize = 12.sp, color = Color(0xFF1B5E20))
                }
                msg?.let { Text(it, color = Color.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = {
                scope.launch {
                    saving = true
                    val b = basic.toDoubleOrNull() ?: 0
                    val al = allowance.toDoubleOrNull() ?: 0
                    val ot = overtime.toDoubleOrNull() ?: 0
                    val bo = bonus.toDoubleOrNull() ?: 0
                    // 从计算结果提取 net_salary / total_cost
                    var net = 0.0; var tc = 0.0
                    result?.let {
                        runCatching {
                            val j = Json.parseToJsonElement(it).jsonObject
                            net = (j["net_salary"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                            tc = (j["total_cost"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
                        }
                    }
                    val body = """{"business_entity_id":"$entityId","account_type":"official","staff_name":"$staffName","basic_salary":$b,"allowance":$al,"overtime":$ot,"bonus":$bo,"net_salary":$net,"total_cost":$tc,"is_foreigner":$isForeigner}"""
                    SupabaseApi.insertRow(token, "staff_payroll", body).onSuccess { onDone() }.onFailure { msg = it.message }
                    saving = false
                }
            }) { Text(if (saving) "保存中..." else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
