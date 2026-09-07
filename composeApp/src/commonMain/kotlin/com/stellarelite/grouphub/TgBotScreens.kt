package com.stellarelite.grouphub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// ============ Telegram Bot 包车订单管理 ============
@Composable
fun TgOrderScreen(token: String) {
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Telegram Bot 包车订单管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("（真实经营账 · 不进报税）", fontSize = 12.sp, color = Color(0xFFF9A825))
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("订单") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("结款") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("用户") })
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> TgOrderList(token)
            1 -> TgPaymentList(token)
            2 -> TgUserList(token)
        }
    }
}

@Composable
fun TgOrderList(token: String) {
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var filter by remember { mutableStateOf("全部") }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(token, refreshKey) {
        rows = SupabaseApi.fetchTable(token, "tg_charter_order", "?select=*&order=created_at.desc&limit=100")
        loading = false
    }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("全部", "new", "ongoing", "completed", "cancelled").forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(if (f == "全部") "全部" else f, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (loading) { Text("加载中...", color = Color.Gray); return@Column }
        val filtered = if (filter == "全部") rows else rows.filter { SupabaseApi.str(it, "order_status") == filter }
        if (filtered.isEmpty()) { Text("暂无订单", color = Color.Gray); return@Column }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered) { r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(SupabaseApi.str(r, "order_no"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(SupabaseApi.str(r, "order_status"), fontSize = 12.sp, color = statusColor(SupabaseApi.str(r, "order_status")))
                        }
                        Text(SupabaseApi.str(r, "trip_details"), fontSize = 13.sp, color = Color.Gray)
                        Text("金额 RM %.2f · 佣金 %.2f · 司机 %.2f · 毛利 %.2f".format(
                            SupabaseApi.dbl(r, "total_order_amount"), SupabaseApi.dbl(r, "agent_commission"),
                            SupabaseApi.dbl(r, "driver_payable"), SupabaseApi.dbl(r, "our_gross_profit")
                        ), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TgPaymentList(token: String) {
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(token) {
        rows = SupabaseApi.fetchTable(token, "tg_order_payment", "?select=*&order=created_at.desc&limit=100")
        loading = false
    }

    if (loading) { Text("加载中...", color = Color.Gray); return }
    if (rows.isEmpty()) { Text("暂无结款记录", color = Color.Gray); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows) { r ->
            InfoRow(
                "${SupabaseApi.str(r, "order_no")} · ${SupabaseApi.str(r, "payment_type")}",
                "RM %.2f · %s · %s".format(SupabaseApi.dbl(r, "amount"), SupabaseApi.str(r, "payment_method"), SupabaseApi.str(r, "payment_status"))
            )
        }
    }
}

@Composable
fun TgUserList(token: String) {
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(token, refreshKey) {
        rows = SupabaseApi.fetchTable(token, "tg_bot_user", "?select=*&order=created_at.desc&limit=100")
        loading = false
    }

    if (loading) { Text("加载中...", color = Color.Gray); return }
    if (rows.isEmpty()) { Text("暂无用户", color = Color.Gray); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows) { r ->
            val active = SupabaseApi.str(r, "is_active") == "true"
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${SupabaseApi.str(r, "full_name")} · ${SupabaseApi.str(r, "user_role")}", fontWeight = FontWeight.Medium)
                        Text(SupabaseApi.str(r, "contact_info"), fontSize = 12.sp, color = Color.Gray)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            SupabaseApi.updateRow(token, "tg_bot_user", "?tg_user_id=eq.${SupabaseApi.str(r, "tg_user_id")}", """{"is_active":${!active}}""")
                            refreshKey++
                        }
                    }) { Text(if (active) "禁用" else "启用", color = if (active) Color.Red else Color(0xFF1B5E20)) }
                }
            }
        }
    }
}

fun statusColor(s: String): Color = when (s) {
    "completed" -> Color(0xFF1B5E20)
    "cancelled" -> Color.Red
    "ongoing", "matched" -> Color(0xFFF9A825)
    else -> Color.Gray
}
