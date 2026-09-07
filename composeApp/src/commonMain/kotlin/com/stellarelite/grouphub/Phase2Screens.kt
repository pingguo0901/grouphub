package com.stellarelite.grouphub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// ============ 二期：手机消息归集中心 ============
@Composable
fun MessageScreen(token: String) {
    var rows by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(token) {
        rows = SupabaseApi.fetchTable(token, "phone_msg_sync", "?select=*&order=msg_time.desc&limit=100")
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("手机消息归集中心", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("WhatsApp / WeChat / Gmail 统一归集", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        if (loading) { Text("加载中...", color = Color.Gray); return@Column }
        if (rows.isEmpty()) { Text("暂无消息", color = Color.Gray); return@Column }
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows) { r ->
                val src = SupabaseApi.str(r, "source")
                val cat = SupabaseApi.str(r, "ai_category")
                InfoRow(
                    "[$src] ${SupabaseApi.str(r, "sender")}",
                    "${SupabaseApi.str(r, "summary")}" + if (cat.isNotEmpty()) " · 分类：$cat" else ""
                )
            }
        }
    }
}

// ============ 二期：AI 经营助手 ============
@Composable
fun AiChatScreen(token: String) {
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("AI 经营助手", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("AI 只读数据生成报告，不修改任何财务/提款/报税记录", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(messages) { (role, content) ->
                val isUser = role == "用户"
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (isUser) "我" else "AI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                        Spacer(Modifier.height(4.dp))
                        Text(content, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("问 AI（如：这个月炙巷利润多少？）") },
                modifier = Modifier.weight(1f),
                singleLine = false
            )
            Spacer(Modifier.width(8.dp))
            Button(enabled = !loading && input.isNotBlank(), onClick = {
                val q = input
                input = ""
                messages = messages + ("用户" to q)
                scope.launch {
                    loading = true
                    val body = """{"question":"$q"}"""
                    val reply = SupabaseApi.callFunction("ai-chat", token, body)
                    messages = messages + ("AI" to reply)
                    loading = false
                }
            }) { Text(if (loading) "思考中..." else "发送") }
        }
    }
}

// ============ 二期：Facebook 营销中控 ============
@Composable
fun FbScreen(token: String) {
    var pages by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var drafts by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(token) {
        pages = SupabaseApi.fetchTable(token, "fb_page", "?select=*&limit=20")
        drafts = SupabaseApi.fetchTable(token, "fb_post_draft", "?select=*&order=created_at.desc&limit=20")
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Facebook 营销中控", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (loading) { Text("加载中...", color = Color.Gray); return@Column }

        SectionTitle("主页绑定（${pages.size} 个）")
        if (pages.isEmpty()) Text("暂无绑定主页", color = Color.Gray)
        pages.forEach { r ->
            InfoRow(SupabaseApi.str(r, "page_name"), "粉丝 ${SupabaseApi.dbl(r, "followers_count").toInt()}")
        }

        SectionTitle("帖子草稿（${drafts.size} 条）")
        if (drafts.isEmpty()) Text("暂无草稿", color = Color.Gray)
        drafts.forEach { r ->
            InfoRow(SupabaseApi.str(r, "status"), SupabaseApi.str(r, "content").take(60))
        }
    }
}
