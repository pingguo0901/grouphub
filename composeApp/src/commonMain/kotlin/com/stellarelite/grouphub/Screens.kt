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
import kotlinx.serialization.json.JsonObject

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
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(token) {
        rows = SupabaseApi.fetchTable(token, "staff_payroll", "?select=*&limit=50")
        loading = false
    }

    val totalEpf = rows.sumOf { SupabaseApi.dbl(it, "employer_epf") }
    val totalSocso = rows.sumOf { SupabaseApi.dbl(it, "employer_socso") }
    val totalEis = rows.sumOf { SupabaseApi.dbl(it, "employer_eis") }
    val totalPcb = rows.sumOf { SupabaseApi.dbl(it, "pcb_mtd") }
    val totalCost = rows.sumOf { SupabaseApi.dbl(it, "total_cost") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("薪资人力合规中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
}

// ============ 5. 税务申报中心 ============
@Composable
fun TaxScreen(token: String) {
    var sst by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var formb by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var genResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token) {
        sst = SupabaseApi.fetchTable(token, "sst_return", "?select=*&limit=20")
        formb = SupabaseApi.fetchTable(token, "formb_tax", "?select=*&limit=20")
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
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("官方档案库（7 年存档）", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("biz_doc_archive", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("电子发票 PDF、进货凭证、EPF/SOCSO/EIS/PCB 回执、SST/FormB/FormE 底稿、执照证书", fontSize = 13.sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("phone_sync_attachment", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("手机同步的账单、聊天凭证、转账截图（内部留存）", fontSize = 13.sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("ai_fb_draft_export", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("AI 报表、FB 海报、帖子草稿、经营分析 PDF", fontSize = 13.sp, color = Color.Gray)
            }
        }
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
